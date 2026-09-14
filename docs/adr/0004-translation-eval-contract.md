# The translation eval scores one page-level call on annotation Japanese, keyed by bubble id

**Status:** superseded, with all its amendments, by [ADR-0015](0015-quality-belongs-to-the-model.md).

The translation half of the eval feeds engines OpenMantra's `text_ja` annotation — clean Japanese, not OCR output — assembles it into a page through the real `ContextAssembler`, and scores a single page-level call whose output is matched to the reference by bubble id. Engines that cannot take a page-level call are reported on a separate per-line floor line, never ranked against the gate. Failure is caught by two deterministic checks — Japanese residue and non-translation — rather than by the readability ratio, which is demoted to a diagnostic.

Before this, the harness looped `TranslationEngineSelector.translate(line)` over lines of ground-truth Japanese while `translation-quality/SCHEMA.md` claimed the input was "actual OCR output, including noise". ADR-0002 had already made the page-level call the design target, so the harness measured an architecture the project had decided against, on an input its own schema disclaimed, with metrics that scored CAT-Translate 0.000 untranslated and 0.000 artifact on a run whose outputs included a verbatim echo of the system prompt and a line of untranslated Japanese.

> **Revised by [#127](https://github.com/artsaraiva/yomu/issues/127) (2026-09-09).** The gate/floor split below is argued from `LlamaTranslationBridge.supportsBatch()`, which [#126](https://github.com/artsaraiva/yomu/issues/126) deleted along with the bridge delegation it belonged to. The distinction is unchanged; only its encoding moved. It is now explicit on `TranslationEngineType.role` (`GATE` / `FLOOR`) rather than inferred from a capability predicate, matching the `"role": "floor" | "gate"` the eval already emits. Read every `supportsBatch()` reference below as naming that role.

## Considered Options

**Score real OCR output instead of annotation Japanese.** Rejected. The eval's job here is to rank translation engines for #35; feeding them OCR output makes that ranking a function of the OCR model #34 has not chosen yet, so every number would need re-deriving once it does. It also cannot be built today — the harness has no OCR half at all, and whether it gets one is #46's open question. The cost is real and is recorded below, not hidden.

**Keep per-line scoring.** Rejected. ADR-0002 made one page-level call with panel markers, reading order and session context the design target and named the LLM the sole design target. A per-line harness cannot exercise any of that, and it cannot see the failure mode that matters most to a page-level engine: returning fewer entries than there are bubbles.

**Score only page-level, dropping ML Kit and OPUS-MT.** Rejected. `LlamaTranslationBridge.supportsBatch()` is `true` and both others are `false`, so a page-only harness would silently have nothing to say about the non-LLM engines. #26 puts the LLM path out of reach on budget-tier RAM, which makes a non-LLM fallback a live shipping path rather than a curiosity. They stay, on their own line, explicitly not comparable to the gate.

**Run the full `processPage(bitmap)` and accept detection and OCR in the loop.** Rejected — it is the previous option's problem in another form: the translation score becomes a cascade score, moving whenever the detector or OCR changes.

**Stub the detector and OCR inside `processPage`.** Rejected, and this is the one to be most careful about. This map has twice found a harness passing green while measuring nothing — #36's detector was silently stubbed at a fake 100%, and #41's translation cases were all discarded on a line-count mismatch under a summary that said so and a test that still exited 0. A stub that returns ground truth is indistinguishable, from the outside, from a stage that works.

**Add a chrF or COMET score, or an LLM judge, now.** Deferred, not rejected. Those answer "how good is this translation"; the checks below answer "did the engine translate at all". The second question is the one currently returning wrong answers, and it costs two regular expressions.

## Consequences

**The eval calls `ContextAssembler.assemble(...)` and `TranslationEngine.translate(blocks, ...)` directly.** Ground-truth boxes stand in for detections and `text_ja` stands in for OCR results; `BubbleDetector` and `OcrEngine` are not called. The distinction from stubbing is not cosmetic — an absent stage is visibly absent, whereas a stub that returns ground truth reports success. This also puts `ContextAssembler`'s panel detection and manga reading order under a score for the first time.

**Cascade quality is unmeasured, deliberately.** No number anywhere in this harness answers "how much does OCR error cost the final translation". Whether that gap is worth closing depends on #46, and it should be closed by a separate end-to-end case type rather than by contaminating this one.

**`translation-quality/SCHEMA.md` is corrected.** It has claimed OCR-noise input since it was written, and `generate-cases.py` has never produced that. The schema follows the harness, not the other way round.

**The gate is the page-level call; the per-line loop survives only as a floor.** Reported for ML Kit and OPUS-MT under a heading that states they are not ranked against the gate. A single number spanning both modes would rank an engine on a call shape it will never receive in production.

**Output is matched to the reference by bubble id, and a missing id scores zero rather than voiding the case.** The eval supplies the bubbles, so it owns the ids: bubble id *n* is line *n* of `source.txt` and `reference.txt`. Today `score_translation` returns `{"error": "line count mismatch"}` and the case scores nothing — that rule is why every run before #45 printed "No engine outputs scored." An engine that returns eight entries for twelve bubbles has failed four bubbles; that is the exact defect page-level scoring exists to catch, and it must cost score.

**Two deterministic failure metrics replace `untranslated_rate`.** *Japanese residue rate* is the fraction of output entries containing a CJK codepoint — the old metric required `source == output` exactly, so a line like `.. でも` passed clean. *Non-translation rate* is the fraction of entries that echo the instruction or return a refusal template rather than a translation; one CAT-Translate output is verbatim `Translate the following Jpn manga text into natural English. Please reply with the translation only.` and scored 0.000 on every existing metric. *Readability ratio* stays, reported, never gated: at 2.587 it flags that something is wrong without distinguishing a verbose translation from an echoed prompt.

**The first page-level numbers will be a pre-ADR-0002 baseline, and must be labelled as one.** ADR-0002 is decided but unbuilt: `TranslationEngine.translate` still carries `@Suppress("UNUSED_PARAMETER")` on `sessionContext` and never reads it, and `translateBatch` prompts `Translate these Japanese phrases to English, one per line, numbered:` — no panel markers, no bubble ids in the prompt, reading order flattened, cache still live on the per-bubble path. Candidates all run through identical scaffolding, so the *ranking* #35 needs survives; the absolute number is not ADR-0002's score and a report that lets anyone read it as such is a defect.

**Session context is not exercised by this harness.** Cases are single, non-consecutive pages, so `sessionContext` is empty everywhere and is reported as such. Cross-page referent resolution is what #30's contrastive set was chosen to measure and what ADR-0002 named as its acceptance gate; building a second, weaker version of it out of consecutive OpenMantra pages would measure the same thing worse.

**OPUS-MT is a live candidate, conditionally.** Its weights now have a download route, and `OpusMtTranslator.load()` fails with `dlopen failed: library "libdjl_tokenizer.so" not found` — a packaging defect tracked as #14. If that is a small fix it is measured on the per-line floor, where #26's RAM ceiling makes it the plausible fallback for devices the LLM does not fit. If it is not a small fix, it leaves the eval roster and #35 decides whether the engine code survives.

**Whether this case set can rank translation engines is not decided here.** #44 needed a full session, a paired sign test and a corpus-ceiling analysis to answer the same question for detection, and landed on "ranks a large gap, never a near-tie, with an 8pp separation rule". Translation has roughly 150 lines and noisier metrics, and the variance cannot be computed until the metrics and the page-level scoring path above exist. Until it is answered, no separation rule and no pass bar are set for translation.

## Amendment (#52): the set catches failures, and the pass bars are set

The question the consequence above left open is now resolved by #52 — and the "no pass bar is set for translation" clause is superseded. The answer needed no variance run: this contract encodes **no continuous quality metric**, only two rare-event failure detectors plus a diagnostic, so there is nothing to significance-test and #44's paired-sign-test method does not apply. **The set catches failure modes and gates regressions; it does not rank translation engines on quality, and it has no separation rule.** Pass bars: **non-translation rate 0** (hard gate), **Japanese-residue rate 0** (gate, hits adjudicated against the reference), **bubble coverage 100% of ids returned** (gate), **readability ratio diagnostic, no bar**. #35 selects among gate-passing engines on #26's non-quality axes; adequacy/fluency ranking stays deferred to #30's contrastive set and a possible future COMET-or-judge metric. The corpus stays at 17 cases / 152 lines — OpenMantra's line ceiling is moot with nothing to power. The scorer this requires is built under #58.

## Amendment (#203): what "hits adjudicated against the reference" means

The #52 amendment gates Japanese residue at 0 "hits adjudicated against the reference" and never says what adjudication is. The literal 0 has since been set aside case by case — by [ADR-0009](0009-selectable-translation-model-set.md) ("residue is never exactly 0") and by [ADR-0013](0013-grammar-constrained-page-level-batch.md) at 0.014 — and [#198](https://github.com/artsaraiva/yomu/issues/198) then measured the shipped arm at 0.027 on the reference phone, leaving a gate that read as failing in production. [#203](https://github.com/artsaraiva/yomu/issues/203) settles the reading rather than setting it aside a third time.

**The residue gate is zero unadjudicated hits.** Every residue hit is assigned a **named failure class**, argued against the reference, in the ADR or ticket that ships the arm. An arm with any hit in a class that has not been adjudicated **fails**. A class, once adjudicated, is carried by name: later hits in that class do not re-argue it, and a new class never inherits a verdict from an old one. The classes adjudicated so far are recorded in ADR-0013.

**Adjudication quotes the hit text when the run record survives.** When it does not, it may rest on the measuring ticket's description of the hits, and says so.

**No threshold replaces the 0.** A rate bar turns a rare-event detector into a tolerance and stops it detecting; #145 already showed residue rejecting the more accurate translator on this axis, which a bar would entrench rather than fix. Nor is residue demoted to a diagnostic: a hit in an unadjudicated class still stops a ship. Non-translation stays a hard 0, with no adjudication.

**A constraint that makes residue unreachable disables the gate; it does not pass it.** A grammar or post-filter that excludes Japanese characters from output scores 0 by construction and leaves this detector measuring nothing. ADR-0013's #203 amendment forbids it on the batch grammar.

## Amendment (#176): a continuous adequacy metric, staged as a per-arm no-regression gate

The amendment above says this contract "encodes **no continuous quality metric**, only two rare-event failure detectors plus a diagnostic". That clause is superseded. The contract gains one: **doc-level-context COMET over the committed page outputs, via Apache-2.0 [`Unbabel/wmt22-comet-da`](https://huggingface.co/Unbabel/wmt22-comet-da)**, costed at ~1 dev-day with no blockers by [#141](https://github.com/artsaraiva/yomu/issues/141) (Route B) and argued for on its own merits by [#145](https://github.com/artsaraiva/yomu/issues/145).

The reason is not that the old clause was wrong when written. It is that every ticket touching translation quality since #84 has had to argue about *meaning* from residue and chrF2: #119's prompt modes, #137's grammar arms, #153's `repeat_penalty`, #145's model comparison. #145's answer came from one reviewer hand-marking 42 bubbles on four pages — which answered that question and cannot track it, because it must be redone by hand for every prompt change, sampler knob and model swap. The metric exists to be re-runnable, not to be more sensitive than a reviewer.

### The job: a per-arm no-regression gate, armed from a measured baseline

A metric ships with a job or does not ship (#144, #139). `mean_chrf` is already an explicitly ungated diagnostic, so a second permanently-ungated number is a number nobody acts on; but a pass bar picked before the first baseline run is the ADR-0008 undocumented-constant failure. So it stages:

1. It lands **`gated: false`, with the arming issue named in its `eval-contract.json` `comment`** — the ungated state carries an expiry rather than becoming permanent.
2. The build records baselines and the run-to-run spread. **The threshold is derived, not chosen**: three reruns of one arm, `N = ceil(max observed spread)`, with the three numbers written into this ADR when the gate is armed. If the reruns come back bit-identical, `N` falls back to a stated floor rather than to zero.
3. The gate is then armed as a **no-regression delta against the arm's own recorded baseline**, never an absolute quality bar. COMET compresses strong systems into a narrow top band (JP-TL-Bench's finding, recorded in #141), so an absolute bar on this scale cannot be justified from anything measured here. A delta is also what every downstream ticket actually asks: *did this prompt / sampler / model change make meaning worse.*

**A baseline is keyed by arm identity**, not by corpus alone: model plus pinned revision, call shape (`idKeyedBatch`), grammar on or off, corpus hash, `contract_sha256`, and the COMET checkpoint revision. An arm is only ever compared against itself, so a new arm records its own first baseline unarmed. Two consequences: the build does **not** wait on [#149](https://github.com/artsaraiva/yomu/issues/149), and **no cross-arm COMET comparison is ever a gate** — cross-arm deltas are diagnostics, which is the shape a #145-class question needs anyway.

The per-arm keying is also what keeps this from repeating the mislabelling this ADR already warns about above: a single global adequacy number would be invalidated the moment the architecture moves, and would be read as a quality claim about an architecture it never measured.

### What is scored, and how

**Per bubble, with preceding-bubble context prepended to source, hypothesis and reference** — the Vernikos et al. (WMT 2022) concatenation trick surveyed for #30 (`docs/research/coherence-eval-methodology.md`, branch `research/coherence-eval-methodology`, not on `main`), ~30 lines over the sentence-level call, no retraining. Preceding-only, up to a character budget starting at 250: manga bubbles resolve referents backwards in reading order, and a *following* bubble is information the engine did not have when it translated, so scoring against it would credit or charge a context the model never saw. The budget is a documented knob, not a pinned constant.

The arm's number is the mean over bubbles. The per-bubble array is **evidence carried in the run record, not a registered metric** — it is the drill-down that tells a reviewer which bubble moved, which is the part of hand review a scalar cannot replace.

**Echoed, non-translated and empty bubbles.** The denominator is every bubble with a non-blank source line — identical to `score_translation`'s `entries`, so the adequacy mean and the existing gates count the same population. Within it:

- A **source echo** or a **non-translation** is scored exactly as returned, with no special case. A Japanese echo against an English reference simply scores low, and both classes are already caught precisely by gates of their own (`japanese_residue_rate` and `non_translation_rate`, both gated at 0).
- An **empty** output is floored to 0.0. COMET over an empty hypothesis is not a meaningful number, and leaving it out would be the excluding-by-stealth this rule exists to forbid.

Excluding the echo class from the denominator was considered and rejected, and it is the trap here. #145 measured CAT-Translate-1.4b at 54% clean *among the 35 bubbles it translated* against 38% over all 42: a mean computed over survivors **pays an engine for declining to answer**, and the engine most likely to trip it is the one whose failure the grammar cannot reach (ADR-0013: the grammar constrains shape, not content). One ungated diagnostic, `mean_comet_translated`, reports the survivors-only mean for drill-down; it is never the gate number.

### Where it runs

**A standalone re-scorer over an existing run record**, with the benchmark's `--comet` flag calling into it. #165's records exist so a run can be re-scored without re-inference, and #145's whole method was that the outputs are already on disk; a COMET path reachable only during a fresh device benchmark could not score anything already measured. The re-scorer refuses a record whose `contract_sha256` no longer matches `eval-contract.json`, on the same rule that already rejects an aggregate computed under a different contract.

It is host-side and scoring-time: CPU, minutes for 147 segments, never on-device.

**The dependency is opt-in and pinned.** `unbabel-comet` and its torch and ~2.3 GB XLM-R checkpoint go in `eval/requirements-comet.txt`, not `eval/requirements.txt`. `sacrebleu` hard-fails the whole run when missing (#156) and that is right for a 3 MB pure-Python dependency; applying the same rule to COMET would mean no benchmark runs anywhere without a 2.3 GB download. So COMET scoring hard-fails **when asked for and unavailable**, and is absent otherwise. It never degrades to a silent null — that is the failure the interpreter check exists to prevent.

The **checkpoint revision is recorded in the manifest beside `contract_sha256`**. An unpinned checkpoint makes two runs incomparable in exactly the way the contract hash already refuses, and ADR-0014 already made revision pinning the rule for every downloaded artefact.

### Contract entries

Registered in `eval-contract.json`, since the scorer refuses any dimension the contract does not carry:

- `mean_comet` — `stage: translation`, `gated: false` with the arming issue named in its `comment`, inputs `record:results[].text`, `reference.txt`, `source.txt`.
- `mean_comet_translated` — ungated diagnostic, same inputs.

`wmt22-comet-da` is source-aware, so **`source.txt` becomes a declared scoring input for the first time**. Every other translation metric either reads the reference or reads the source as a cross-check; this one reads all three, and the contract says so rather than leaving it to the scorer.

### The floor line is scored, and nothing retires

The per-line floor arms are scored and reported on the floor line, never gated and never ranked against the gate arm — the rule this ADR already sets for every other metric. (OPUS-MT stays off the roster while #14 is open.)

**The hand review stays the adjudicator of record.** COMET returns a number, not an error class: it cannot say *polarity flipped* or *wrong surname*, which is the output #145 actually acted on. It is invoked per decision, not per run, and the continuous metric is what tells a decision it is needed.

**ADR-0006's contrastive set (Route A) stays deferred, not retired.** It measures referent resolution directionally, which a reference-based scalar cannot, and it remains blocked for the reason #141 found: with session context reaching no model, the directional gate scores zero delta by construction.
