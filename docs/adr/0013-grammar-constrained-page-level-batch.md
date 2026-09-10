# The shipped translation architecture is a grammar-constrained page-level batch call

**Status:** accepted, 2026-09-10. Decides [#138](https://github.com/artsaraiva/yomu/issues/138) on the [#137](https://github.com/artsaraiva/yomu/issues/137) spike's numbers. Amends [ADR-0002](0002-cross-panel-translation-context.md) and corrects [ADR-0010](0010-qwen-default-cat-demoted-to-floor.md).

Yomu translates a page in **one page-level, id-keyed call per capture**, with the output shape enforced **at sample time by a GBNF grammar** rather than checked after the fact. The curated default (Qwen2.5-1.5B-Instruct) carries `idKeyedBatch = true`. Per-line translation survives as a **slot strategy** — for the CAT-Translate-0.8b low-storage floor, which cannot produce id-keyed output, and as the fallback for a page whose prompt exceeds the native prompt cap — not as the default architecture.

**Cross-page session context is withdrawn.** It is not deferred and not dormant: the plumbing is deleted.

## What was shipping, and why that is the question

[ADR-0002](0002-cross-panel-translation-context.md) decided page-level. `LlmModelCatalog.DEFAULT` has carried `idKeyedBatch = false` since [#90](https://github.com/artsaraiva/yomu/issues/90) (`0fceeff`), so every capture has routed to `translatePerLine`, `sessionContext` has been read by nothing, and [#119](https://github.com/artsaraiva/yomu/issues/119) then ranked three *per-line* prompt modes and shipped one. That was an architecture reversal recorded nowhere. This ADR is where it goes on the record — and where it is undone.

## Evidence

[#137](https://github.com/artsaraiva/yomu/issues/137), four arms on the shipped Qwen2.5-1.5B Q4_K_M over the 17-page / 147-bubble [ADR-0004](0004-translation-eval-contract.md) corpus, sampler parameters held fixed across arms, scored by the unmodified ADR-0004 scorer (`docs/research/137-grammar-batch-spike.md`):

| arm | non-translation | JP residue | median ms/page | peak PSS |
|---|---|---|---|---|
| per-line (shipped) | 0.000 | **0.000** | 3228 | 2074 MiB |
| batch, no grammar | 0.000 | 0.014 | **2056** | 2111 MiB |
| batch + grammar | 0.000 | 0.014 | 2396 | 2127 MiB |
| batch + grammar + session context | 0.000 | **0.272** | 2767 (13 pages) | 2146 MiB |

Run on a Pixel_10_Pro emulator. The gate metrics are device-independent; the latency figures are emulator-relative and comparable only to each other.

## Considered Options

- **Keep per-line (status quo).** Rejected. It is the only arm that passes ADR-0004 as written, and that is the whole of its case. Its advantage is 2 bubbles out of 147, both of which are a *sampler* defect (below); its cost is 36% of page latency, paid on every capture, because per-line pays a fresh prompt prefill and a cleared KV cache for every bubble.

- **Page-level batch without a grammar.** Rejected. It is the fastest arm and scores the same residue as the grammar arm, but residue does not price what the grammar buys. Two of 17 pages under the unconstrained batch lost **every** bubble to a malformed id tag (`"Burial shop [id]"`, `"Are only me?"` — 0 translations parsed, the whole page rendered as untranslated Japanese source), and the grammar recovered both. A silently untranslated page is the worst user-visible failure this app has.

- **Batch behind a flag, per-line as the default.** Rejected. A flag is two code paths and two eval matrices, permanently.

- **Grammar-constrained page-level batch. (Chosen.)** 26% faster than per-line *with the grammar's cost included*, at equal memory, with the structural failure class made unreachable and the residue gap attributable to a knob nobody has turned.

## The residue gap is adjudicated, not waived

ADR-0004 sets Japanese-residue rate 0 as a gate "hits adjudicated against the reference". Adjudicated here: both grammar-arm residue bubbles are the **final id of a page**, and both are the model continuing to write after the translation ends —

```
balloon-dense-dialogue [15] "That's right... [1] ただ空気いれた風船をいくら集めても浮くわけねーだろ!! ..."
tencho-p33             [6]  "Sorry...  (Mel)  (Tenzoku)  (Mel)  (Tenzoku) ..."
```

— with **no repetition penalty in the sampler**. That is [#139](https://github.com/artsaraiva/yomu/issues/139)'s decision, it is orthogonal to call shape, and it would appear on the per-line path too if per-line ever generated long enough to ramble. The gap is therefore not attributed to the page-level call and does not disqualify it.

This is not the first time the gate's literal reading has been set aside on adjudication: [ADR-0009](0009-selectable-translation-model-set.md) already found that "no model passes the gate (residue is never exactly 0), so 'pass the ADR-0004 gate' cannot be the promotion bar". ADR-0004 is **not** rewritten here; the reading it already carries is applied.

## Consequences

**The architecture decision ships behind #139, not with it.** This ADR decides; the build issue does not merge until #139 lands a repetition penalty and a re-run scores the batch arm at 0.000 residue. Deciding and shipping are separate acts, and this repository's recurring failure is a claim that ran ahead of its measurement.

**Falsification condition.** The latency case rests on an emulator. The build's acceptance criterion is a reference-phone (SM-S911B) run of **grammar-batch against per-line**: if the median ms/page gap is under **15%**, this decision reverts to per-line. The comparison is against grammar-batch — the arm that actually ships — not against the bare 36% figure, which is for a build that does not include the grammar.

**The grammar is mandatory on the batch path.** No flag, no fallback to unconstrained batch. Measured cost: **0.518 ms/token**, ~10× the base sampler chain but **3.2% of wall-clock** next to decode; 3.9% of tokens need the grammar-first re-sample.

**The grammar bounds line length, and that bound is a knob.** With `line` unbounded the model ran to the token cap on 5 of 17 pages; bounding it to **160 characters** (≈2× the corpus's longest human reference, which maxes at 75, median 22) cut that to 0 of 17 and left the two clipped tails that are this decision's entire residue gap. The value is not pinned here: it is a build-time knob co-owned with #139, and a working repetition penalty may let it relax.

**Session context is dead by decision.** ADR-0002's cross-page session memory was measured against a model for the first time in #137 and failed: the production-shaped payload (`OverlayService`'s full previous page, both languages) exceeds the 512-token `n_batch` prompt cap on **4 of 17 pages** (670 / 660 / 550 / 710 tokens), each returning `""` → `null` → an empty `PageTranslation` → the whole page rendered untranslated with no user-visible error. Surviving pages were **slower** (2767 vs 2396 ms) with 4.7× the grammar rejection rate (18.5% vs 3.9%), and no arm showed a quality gain. Koharu corroborates independently: its `TranslationContext` is populated only under `#[cfg(test)]`, and its coherence comes entirely from putting the whole page in one call — which is what this decision restores. `OverlayService`'s carry-and-clear plumbing and the `sessionContext` parameter are **deleted** in the build issue. Five dead-on-arrival fields is enough; this one does not become the sixth by lingering.

**`n_batch = 512` stays open, and stops being urgent.** It remains the binding ceiling on prompt size ([#136](https://github.com/artsaraiva/yomu/issues/136)), and its compute-buffer cost has never been measured. With session context gone and a per-page fallback in place, raising it is an optimisation rather than a prerequisite.

**A page that overflows the prompt cap falls back to per-line, loudly.** The per-line path exists anyway for the 0.8b, so this is reuse, not new code. Splitting a page across two batch calls was rejected: it divides the ids and reintroduces exactly the cross-panel blindness the page-level call is bought to remove. Today's behaviour — an empty `PageTranslation` and a silently untranslated page — is not retained under any option.

**Per-line survives as a slot strategy, not as an architecture.** [ADR-0012](0012-page-translation-slot.md) already puts batch-versus-per-line inside the slot. CAT-Translate-0.8b keeps its bare model-card per-line form unchanged (ADR-0002's #71 amendment) and is **not** re-measured under a grammar: forcing the id shape on a model that echoes its source produces well-formed garbage, and residue is the only gate that would catch it. Whether a grammar should constrain the per-line path at all stays open.

**`idKeyedBatch` stays a per-model capability flag.** `LlmModelCatalog.DEFAULT` flips to `true`; nothing else changes. The flag is read by the slot ADR-0012 makes responsible for strategy, and it is the single line whose wrong value caused this ticket, so it stays visible and greppable rather than dissolving into a hierarchy.

**The post-hoc guards stay, examined.** `looksLikeNonTranslation` (`TranslationEngine.kt:11-21`) and its loop heuristic are applied at one call site, `TranslationEngine.kt:88`, over `output.byId` — both strategies feed through it. The grammar constrains **shape, not content**: `[0] I'm sorry, I can't help with that` is grammar-valid output, and so is the `(Mel) (Tenzoku) (Mel)` loop above. `looksLikeNonTranslation` is also the declared twin of `is_non_translation` in `eval/run_eval_lib.py`; deleting it would desync runtime from scorer. `parseIdKeyedTranslations`'s entire-line regex (`LlamaTranslationBridge.kt:157`) is not a guard at all — it is the parser. Only its silent multi-line *drop* becomes unreachable, and that is a behaviour, not a line of code. Nothing is retired.

**Four native changes are prerequisites of the build.** Three are pre-existing defects [#135](https://github.com/artsaraiva/yomu/issues/135) found: the redundant `llama_sampler_accept` at `llama_jni.cpp:260` (harmless only because all four current samplers declare `.accept = nullptr`, but it would double-advance grammar state); `llama_sampler_init_grammar`'s return passed unchecked to `llama_sampler_chain_add`, so a malformed GBNF SIGSEGVs — a segfault-class fix that needs no architecture decision and can land independently; and the grammar sampler held **outside** `g_sampler` with upstream's rejection-sampling shape, because `llama_grammar_apply_impl` writes `-INFINITY` without clearing `cur_p->sorted` while `top_k` leaves it `true`, yielding NaN. The fourth is a behaviour change: **`MAX_BATCH_OUTPUT = 768`** caps the batch token budget, which un-inverts #136's self-refusal (the old "all of `N_CTX` the prompt does not use" budget is passed to `prompt_fits` as `output` and therefore *shrinks* the prompt allowance). `LLAMA_BUILD_COMMON` stays `OFF`; the GBNF is hand-written and two rules regardless of bubble count.

**A user-visible switch would otherwise go inert.** `promptMode` is read **only** inside `translatePerLine` (`LlamaTranslationBridge.kt:86`), so flipping the default to batch makes #119's shipped `TRANSLATION_ONLY` unread for the default model. Worse, `SettingsScreen.kt:97` renders "Use surrounding dialogue (experimental)" whenever the selected model's `promptMode == TRANSLATION_ONLY` — which stays true — so the switch would render and do nothing. That is precisely the pattern [#143](https://github.com/artsaraiva/yomu/issues/143) ruled must not be copied from koharu: on a phone the user cannot see that a switch did nothing. The switch is gated on `!idKeyedBatch`. The batch call **subsumes** capture-context anyway: it carries the whole page, not ±1 neighbour.

**The batch prompt is unvalidated and stays open.** `buildBatchPrompt` predates #119, and #119's three-mode ranking was per-line only — so the prompt now shipping on the winning architecture has never been benchmarked on it. It is a measurement, not a decision, and it carries a second question: with the grammar enforcing shape, does the prose instruction still earn its tokens against a 512-token prompt cap? Tracked separately.

**Bubble coverage cannot fail, and this decision rests on residue alone.** #137 measured coverage at 100% in all four arms *including* the one where 4 of 17 pages were never translated, because `TranslationEngine` substitutes `bubble.sourceText` for any id the slot does not return. Residue caught it only because the source happened to be Japanese; an ML Kit or OPUS-MT floor arm, or a change of source language, would not trip it. The coverage column in the table above is reported to say so. Owned by [#142](https://github.com/artsaraiva/yomu/issues/142).

**What this re-arms elsewhere.** [ADR-0006](0006-coherence-gate-contract.md)'s contrastive gate was blocked — with `sessionContext` reaching no model, its directional test scores 0 delta by construction. It becomes runnable, but **narrowed**: the model now sees a whole page at once, so the gate can measure *intra-page* coherence only; *cross-page* coherence is withdrawn with session context and is not coming back. ADR-0006 is not redesigned here. [ADR-0008](0008-translation-model-selection.md)'s fine-tune trigger, re-anchored to Qwen by ADR-0010, stays **armed and unfired**: its antecedent is now evaluable rather than blocked, and [#145](https://github.com/artsaraiva/yomu/issues/145) is what evaluates it.

## Amends ADR-0002

Page-level, id addressing, per-bubble fallback, panel markers and empty-OCR omission are **reaffirmed on measurement** — the #68 and #71 amendments narrowed them to the 0.8b's limits, and on the current default they hold. The **session-memory limb is withdrawn**. ADR-0002 keeps its `accepted` status and carries a pointer; it is not superseded, because most of it just won its argument.

## Corrects ADR-0010

ADR-0010 states that Qwen's 0.102 residue "was scored through the ADR-0004 page-level id-keyed batch path with `supportsIdKeyedBatch() = true` — **the same call the app ships**". The final clause is false: the app has never shipped that configuration. The number's provenance is a page-level path the app did not run.

**The conclusion survives.** ADR-0010's comparison was arm-consistent — Qwen and the 0.8b were measured on the same page-level path — so the ranking that demoted the 0.8b holds, and #137 strengthens it (the same model measures 0.000 residue per-line and 0.014 batch on this corpus, both far below the 0.388 that demoted the floor).

**One number is genuinely unknown:** CAT-Translate-0.8b has **never been scored per-line on the ADR-0004 corpus**. Its 0.03 per-line residue is #68's, on a different corpus. The floor's number on the shipped harness does not exist.
