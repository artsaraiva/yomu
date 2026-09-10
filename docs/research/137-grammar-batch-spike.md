# Spike #137 — grammar-constrained page-level batch vs the shipped per-line path

**Question.** Does a grammar-constrained page-level batch call beat the shipped per-line path on the
ADR-0004 gates, and at what latency and memory?

**Answer, in one line.** The page-level call is **36% faster per page at equal memory**, the grammar
**does fix the class of failure it was bought for** (two pages that the unconstrained batch lost to a
missing id tag), and yet **per-line still wins the gate outright**: per-line scores 0.000 Japanese
residue against 0.014 for both batch arms, so on ADR-0004 as written, per-line PASSes and every batch
arm FAILs. Carrying session context into the batch call **makes things much worse, not better** — it
blows the 512-token `n_batch` prompt cap on 4 of 17 pages, each of which returns an empty
`PageTranslation` and silently renders the untranslated Japanese source.

---

## What was run

One model, Qwen2.5-1.5B-Instruct Q4_K_M — the shipped default — over the 17-page / 147-bubble
ADR-0004 corpus, four arms, sampler parameters **held at today's values throughout** (temperature
0.2, top_k 40, top_p 0.9, no repetition penalty, no min_p), so nothing here is attributable to
generation-parameter drift. #139 varies those separately.

| arm | call shape | grammar | session context |
| --- | --- | --- | --- |
| `qwen_perline` | shipped: one native call per bubble, `TRANSLATION_ONLY` prompt | — | never read (per-line ignores it) |
| `qwen_batch` | ADR-0002 page-level id-keyed, one call per page | — | none |
| `qwen_batch_grammar` | same | GBNF, `[id] text` pinned at sample time | none |
| `qwen_batch_grammar_ctx` | same | same | previous page's source/translation pairs |

`qwen_batch` exists so the grammar's contribution is **separable** from the page-level call's.
Without it a win could be credited to either, which is the mistake #119's comparison already made in
the other direction (it ranked three *per-line* prompt modes and concluded about architecture).

Harness: `ArchitectureSpikeTest` on the spike branch, scored by the unmodified ADR-0004 scorer
(`eval/run-eval.py`, the page-level id-keyed path built in #58). Device: **Pixel_10_Pro emulator**
(`sdk_gphone16k_arm64`), not the reference phone — see [Honesty of the run](#honesty-of-the-run).

## Results

### Gate metrics (ADR-0004 scorer, `eval/results/20260910-111024.json`)

| arm | non-translation (gate 0) | Japanese residue (gate 0) | bubble coverage (gate 100%) | pages completed | readability | verdict |
| --- | --- | --- | --- | --- | --- | --- |
| `qwen_perline` | **0.000** | **0.000** | 100.0% (147 ids) | 17/17 | 1.016 | **PASS** |
| `qwen_batch` | 0.000 | 0.014 | 100.0% | 17/17 | 1.075 | FAIL |
| `qwen_batch_grammar` | 0.000 | 0.014 | 100.0% | 17/17 | 1.181 | FAIL |
| `qwen_batch_grammar_ctx` | 0.000 | **0.272** | 100.0% | 17/17 | 0.939 | FAIL |

**Bubble coverage is 100% in all four arms and means nothing here.** `TranslationEngine` substitutes
`bubble.sourceText` whenever the slot returns no usable translation for an id, so a page the model
never answered still emits a full-length, fully-"covered" result. That substitution is exactly the
silent #58 failure, and on this corpus **the residue metric is the only gate that catches it** — 40
of the 44 residue bubbles in the context arm are the Japanese source echoed back, not the model
writing Japanese. Any future run that reports coverage without cross-checking source-echoes is
reporting a number that cannot fail.

### Latency and memory

Pages that returned nothing are excluded from the latency figures (they "complete" in 0 ms).

| arm | median ms/page | mean | max | ms per bubble | total | peak PSS |
| --- | --- | --- | --- | --- | --- | --- |
| `qwen_perline` | 3228 | 3331 | 6959 | 385 | 56.6 s | 2074 MiB |
| `qwen_batch` | **2056** | 2101 | 5012 | **243** | 35.7 s | 2111 MiB |
| `qwen_batch_grammar` | 2396 | 2458 | 4976 | 284 | 41.8 s | 2127 MiB |
| `qwen_batch_grammar_ctx` | 2767 (13 pages) | 3588 | 14740 | 440 | 46.6 s | 2146 MiB |

- **The page-level call is faster, and the direction was not obvious.** One call per page beats N
  calls per page by **36% at the median** (2056 vs 3228 ms) and 37% per bubble, despite generating
  more tokens per call, because per-line pays a fresh prompt prefill and a cleared KV cache on every
  single bubble.
- **Memory is a non-issue.** The whole spread across four arms is **72 MiB (3.5%)** on a ~2.1 GiB
  peak PSS. The page-level call does not cost meaningful RAM over per-line.
- **The grammar costs ~340 ms/page** (2396 vs 2056), or ~14% of page latency — see below.

### The grammar overhead number (this ticket's other deliverable)

No published per-token grammar-overhead figure exists anywhere; upstream's own `common/sampling.cpp`
still carries `// TODO: measure grammar performance` and its benchmark issue has been open since
2023, so #135 deliberately refused to invent one. Measured here via `llama_perf_sampler` on the
grammar sampler's own chain, separately from the base sampler chain and from decode:

| arm | base sampling, total | grammar, total | grammar ms/token | grammar share of all sampling time | grammar rejections |
| --- | --- | --- | --- | --- | --- |
| `qwen_batch_grammar` | 139 ms | 1332 ms | **0.518** | 90.5% | 100 / 2573 tokens (3.9%) |
| `qwen_batch_grammar_ctx` | 132 ms | 3515 ms | 1.600 | 96.4% | 406 / 2197 tokens (18.5%) |

Read this carefully. **The grammar dominates sampling by an order of magnitude — and sampling is
noise next to decode.** 1332 ms of grammar work is spread over 17 pages that took 41.8 s in total:
grammar is **3.2% of wall-clock**, decode is essentially all of the rest. The 0.518 ms/token figure
is a real cost and it is affordable; on a CPU-only phone build, per-token decode is 10–20 ms.

The rejection rate is the more interesting number: **3.9% of tokens** need the grammar-first
re-sample under the plain batch prompt, rising to **18.5%** once session context is in the prompt —
the context pairs actively pull the model off the required output shape.

## What the grammar actually fixed, and what it did not

**Fixed — the exact failure class it was bought for.** Two of the 17 pages under the *unconstrained*
batch lost every bubble to a malformed id tag, and both were recovered by the grammar:

```
qwen_batch  bourei-sparse-single  raw = "Burial shop [id]"     -> parse finds no [n] line -> 0 translations -> source rendered
qwen_batch  tojime-p35            raw = "Are only me?"          -> no id tag at all       -> 0 translations -> source rendered
```

These are precisely what `parseIdKeyedTranslations`'s entire-line-match regex and
`looksLikeNonTranslation`'s seven patterns exist to detect after the fact, and the grammar makes them
unreachable. The grammar arm translated both pages correctly.

**Not fixed — the model rambles inside the last line.** Both residue bubbles in the grammar arm are
the *final* id of a page, and both are the same defect:

```
balloon-dense-dialogue [15] "That's right... [1] ただ空気いれた風船をいくら集めても浮くわけねーだろ!! [2] こいつ絶対バカだし ..."
tencho-p33             [6]  "Sorry...  (Mel)  (Tenzoku)  (Mel)  (Tenzoku)  (Mel)  (Tenzoku) ..."
```

The grammar guarantees *one line per id, in order*; it does not guarantee that a line stops when the
translation does. With **no repetition penalty in the sampler** the model finishes the last
translation and then keeps writing inside that same line. The first version of the grammar left
`line` unbounded and this ran to the token cap on **5 of 17 pages**; bounding it to 160 characters
(≈2× the longest line in the corpus's human references, which max out at 75 chars, median 22) cut
that to **0 of 17 token-cap hits** and left the two clipped tails above. The remaining fix is a
**repetition penalty**, which is #139's decision, not a grammar problem — noted there.

## Session context: measured for the first time, and it fails

ADR-0002's session context has never once reached a model in production. This is its first real
test, and the result is unambiguous:

```
balloon-mixed-dialogue  prompt 670 tokens  -> refused, 12/12 bubbles rendered as Japanese source
balloon-p28             prompt 660 tokens  -> refused, 12/12
bourei-p04              prompt 550 tokens  -> refused,  8/8
tencho-p27              prompt 710 tokens  -> refused,  9/9
```

`llama_jni.cpp` sets `n_batch = 512` and `prompt_fits` enforces it as a hard prompt cap, so a
670-token prompt is refused outright: `""` → `null` → an empty `PageTranslation` → the whole page
rendered untranslated, with **no user-visible error**. 4 of 17 pages, 41 of 147 bubbles.

**This corrects #136 on its own terms.** #136 measured the `[id]` shape as exceeding `n_batch` on
**0 of 17** pages with session context on; this run measures **4 of 17**. The difference is what
"session context" means: #136 modelled it analytically, while this arm builds it the way
`OverlayService` actually does — `result.translations.map { originalText to translatedText }`, the
*entire* previous page, both languages. That is a much larger payload than #136 assumed. #136's
headline conclusion survives intact and is if anything strengthened: **`n_batch`, not `N_CTX`, is the
binding ceiling, and it is the number that would have to be raised.** Its compute-buffer cost is
still unmeasured.

Note also that the context arm's *surviving* pages are not better than the no-context arm — they are
slower (2767 vs 2396 ms median) and their grammar rejection rate quadruples. There is no evidence
here that session context helps translation quality; there is clear evidence it costs latency and
breaks pages.

## Native changes made (all on the spike branch, none production-wired)

Three of these are fixes to pre-existing defects that #135 identified and that had to be cleared
before a grammar could be added at all:

1. **`llama_jni.cpp:260` — removed the redundant `llama_sampler_accept`.** `llama_sampler_sample`
   already accepts into the chain. Harmless today only because top_k / top_p / temp / dist all
   declare `.accept = nullptr` (verified in `llama-sampler.cpp`), but it would double-advance
   grammar state.
2. **`llama_sampler_init_grammar`'s return is now checked.** The existing code passes `init_*`
   returns straight to `llama_sampler_chain_add`, which dereferences unconditionally; a malformed
   GBNF would SIGSEGV. It now refuses the generation rather than silently sampling unconstrained.
3. **The grammar sampler is held outside `g_sampler`**, per #135: `llama_grammar_apply_impl` writes
   `-INFINITY` without clearing `cur_p->sorted` and `top_k` leaves it `true`, so a grammar placed
   after `top_k` in the same chain yields NaN. Sampling follows upstream's **rejection-sampling**
   shape — sample from the chain, test the chosen token through a one-element array, re-sample with
   the grammar applied *first* only on rejection. It is wrapped in a one-element chain of its own
   purely so `llama_perf_sampler` (chains only) can time it.
4. `LLAMA_BUILD_COMMON` stays `OFF`. The GBNF is hand-written, two rules regardless of bubble count.
5. **`MAX_BATCH_OUTPUT = 768` caps the batch token budget** (`LlamaTranslationBridge`). The old
   budget was "all of `N_CTX` the prompt does not use", which is passed to `prompt_fits` as `output`
   and therefore *shrinks* the prompt allowance to `N_CTX - budget - 8` — #136's self-refusal
   inversion. With the cap, the binding limit is `n_batch` again, where #136 says it belongs.

The `prompt.length / 2` token estimate is **still there**. With the budget capped it no longer drives
the self-refusal, so replacing it was not needed to get a clean measurement; the exact count is
visible in logcat when `prompt_fits` refuses (that is where the 670 / 660 / 550 / 710 figures above
come from).

## Honesty of the run

- **What the run scored:** the four arms' own outputs from this run and nothing else. Each case's
  `actual/` directory was deleted and rewritten before scoring (the #58 stale-output failure), and
  all 68 case×arm result files were re-extracted from this run's logcat, each parsed as JSON so a
  truncated log line would fail loudly rather than score short. 17 cases × 4 arms = 68 files, all
  present.
- **Bubble coverage did not measure anything** and is reported above only to say so. See the note
  under the gate table.
- **Ran on an emulator, not the reference phone.** The gate metrics (residue, non-translation,
  coverage, readability) are device-independent. The **latency and PSS numbers are
  emulator-relative** and are directly comparable *to each other* (same device, same session, arms
  run back to back) but not to ADR-0010's or #58's phone figures. The 36% per-line-to-batch gap is a
  ratio measured under identical conditions and should survive the move to hardware; the absolute
  milliseconds should not be quoted as phone numbers.
- **Every arm ran all 17 cases.** No arm was budget-truncated.
- Sampler parameters were identical across arms; the only variables are call shape, grammar, and
  session context.

## What this hands to the open tickets

- **#138 (architecture decision)** now has the numbers. The honest summary is that this is **not** a
  clean win for either side: page-level is materially faster at equal memory and the grammar closes
  the structural failure class, but per-line is the only arm that passes ADR-0004 as written, and the
  two batch arms' residue is a *last-line rambling* defect rather than anything about the page-level
  call itself.
- **#139 (generation parameters)** owns the remaining defect: with no repetition penalty, the model
  keeps writing after the final translation. This is the first measurement in this repo that gives
  `repeat_penalty` a concrete job.
- **New: `n_batch` must be decided before session context can ship at all.** At 512 the production
  session-context payload refuses 4 of 17 pages silently. Raising it is the obvious move and its
  compute-buffer cost has never been measured.
- **ADR-0010's factual error stands corrected by measurement, not just by inspection.** Its 0.102
  residue was attributed to "the ADR-0004 page-level id-keyed batch path… the same call the app
  ships". The app ships per-line, and per-line measures 0.000 residue on this corpus while the
  page-level path measures 0.014.
