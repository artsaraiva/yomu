# Challenger LLM bake-off results (#84)

Corpus: 17 eval cases / 145 story boxes, ADR-0004 page-level id-keyed batch path.
All challengers ran through `EngineBenchmarkTest` with `supportsIdKeyedBatch() = true`,
scored by `eval/run-eval.py` against the ADR-0004 gate. Q4_K_M tier unless noted.

Two devices:
- **Phone** — Samsung SM-S911B (Galaxy S23, 8 GB RAM). Latency + RAM are real here.
  The two bake-off leaders were phone-confirmed on 2026-08-18 (#72, see below); the
  rest of the phone column is from 2026-08-17.
- **Emulator** — Pixel 10 Pro AVD, arm64-v8a, **16 GB RAM**, on an Apple-Silicon host.
  Used to run models that OOM/thrash on the phone. **Quality metrics are valid; latency
  and RAM are host-CPU artifacts, not phone-representative.**

## Results (phone unless marked ᵉ = emulator)

| Engine | Role | JP-residue (gate 0) | Coverage | Readability | Latency/page | Peak PSS | Outcome |
|---|---|---|---|---|---|---|---|
| **gemma-2-2b-it** | gate | **0.010** | 100 % (11/17) | 1.122 | **~35 s** | 2.75 GB | Best quality, but ~35 s/page — 11/17 pages before its 6-min slice; too slow on-device |
| **qwen25_1.5b** | gate | **0.102** | 100 % | 0.969 | **~15 s** | 2.0 GB | **On-device winner** — 17/17, stable, fits budget |
| translategemma_4b ᵉ | gate | 0.028 | (71 ids) | 1.080 | ~52 sᵉ | 5.2 GBᵉ | Great quality but too slow (capped 7/17) |
| cat_translate_1.4b | gate | 0.252 / 0.320ᵉ | 100 % | 0.894 / 0.829ᵉ | ~19 s | 1.8 GBᵉ | Best CAT sibling; beats 0.8b |
| cat_translate_1.4b_i1 | gate | 0.286ᵉ | 100 % | 0.805ᵉ | ~2 sᵉ | 1.8 GBᵉ | ≈ regular 1.4b (marginal wash) |
| llm 0.8b (incumbent) | gate | 0.388 | 100 % | 0.843 | ~14 s | — | Shipped default / baseline |
| cat_translate_7b | gate | 0.830ᵉ | 100 % | 0.133ᵉ | ~15 sᵉ | **8.8 GBᵉ** | Worse **and** too big — rejected |
| qwen3_4b | gate | 0.964 | (partial) | 0.000 | 120 s cap | ~2.9 GB | Unfit — thinking model |
| hunyuan_mt_7b (Q3_K_M) | gate | 0.900 | (1 bubble) | 0.025 | 120 s cap | ~3.4 GB | Unfit — too slow / RAM-thrashes |
| mlkit | floor | 0.014 | 100 % | 0.992 | ~0.26 s | — | Floor, not gate-ranked |

Latency is the page-level id-keyed batch call; the 120 s figure is the bridge's
`BATCH_TIMEOUT_MS`, i.e. the model did not finish a page within the budget. The 1.4b
phone-vs-emulator residue spread (0.252 vs 0.320) is `temperature = 0.2` run variance.

## Findings

- **General small instruct models beat the CAT translation specialists — decisively.**
  `gemma-2-2b-it` scores Japanese-residue **0.027** and `Qwen2.5-1.5B-Instruct` **0.102**,
  against every CAT model's 0.25–0.39, at full coverage with fluent English output (spot-
  checked). The "translation-specialist beats general LLM" assumption does not hold on
  this eval; a 2B general instruct model is 12× cleaner on residue than the 1.4b specialist.
  These are the **strongest on-device candidates found**. Both were then phone-confirmed
  (#72) — quality holds, but latency splits them apart (see the phone-confirmation section).

- **`translategemma-4b` matches on quality (residue 0.028) but is too slow.** ~52 s/page
  even on the fast emulator (only 7 of 17 cases before the budget cap) → unusable on a phone.
  The specialist's quality is there; its speed is not.

- **CAT-Translate 1.4b was the best CAT sibling** (residue 0.252, beats the 0.8b) — but is
  now outclassed by the general 2B/1.5B models above. It remains the fallback if a
  gemma/Qwen licence or footprint issue rules those out.

- **The imatrix 1.4b (`i1`) is a wash, not an upgrade.** Same size / RAM / latency as
  the regular 1.4b; residue marginally better (0.286 vs 0.320), readability marginally
  worse (0.805 vs 0.829), both within run variance. No reason to switch the winner, but
  a fine equivalent if the i1 build is more convenient.

- **Bigger CAT is *worse*, not better.** CAT-Translate **7B** (emulator, 8.8 GB peak —
  needs a 16 GB device, unrunnable on the 8 GB phone) scores residue **0.830** /
  readability **0.133**, far below the 1.4b. It translates some pages cleanly but leaves
  whole pages in Japanese — an inconsistent translator on the bare batch prompt. Rejected
  on both quality and footprint. Parameter count does not buy quality here.

- **No non-CAT challenger beats the 1.4b either.**
  - **Qwen3-4B** is a *thinking* model: on the bare id-keyed prompt it emits
    `<think>…` reasoning and runs to the 120 s cap every page, producing garbage
    (residue 0.96, readability 0.0). Wrong tool for the prompt shape.
  - **Hunyuan-MT 7B** produces *correct* translation for the first bubble
    (`[0] Ah… that's amazing`) but decodes only ~3–7 tokens in 120 s (~40 s/token).
    Peak PSS (3.4 GB) sits below the 3.8 GB Q3 weight file, so the model is
    demand-paging from flash every token — it does not fit the phone's usable RAM.
    Q4 (4.6 GB) OOM-killed the process on load outright.

- **No LLM passes the ADR-0004 gate.** Every gate engine leaves Japanese residue
  (> 0). The 0.8b rightly stays the shipped default; CAT-1.4b reaches enthusiasts via
  the ADR-0001 custom-model slot until measured adequate.

- **Confirms ADR-0008's RAM claim.** "Above ~1.5B params, weights alone consume the
  entire budget-tier RAM." The 4B is borderline; the 7B thrashes even at Q3 and OOMs
  at Q4 (8.8 GB peak on the emulator). The 8 GB reference device tops out around the
  1.4B sibling — and, crucially, the 7B is not even worth the RAM: it scores *worse*.

## Phone-confirmation run (#72)

Run `20260818-104743` on the reference phone (Galaxy S23, 8 GB), the two bake-off
leaders only:

```sh
eval/run-benchmark.sh \
  -Pandroid.testInstrumentationRunnerArguments.skipBaseline=true \
  -Pandroid.testInstrumentationRunnerArguments.challengers=gemma2_2b,qwen25_1.5b
```

| | Qwen2.5-1.5B | gemma-2-2b-it |
|---|---|---|
| pages completed | **17 / 17** | 11 / 17 |
| latency/page | ~15 s median, ~16.6 s mean | ~35 s median, ~37 s mean |
| peak PSS | ~2.0 GB (load) / ~1.27 GB steady | ~2.75 GB (load) / ~2.07 GB steady |
| JP-residue (scorer, micro-avg) | 0.102 | 0.010 |
| coverage | 100 % (147 ids) | 100 % (105 ids, subset) |
| readability | 0.969 | 1.122 |

**Quality holds on-device.** Qwen's phone residue (0.102) matches the emulator exactly;
gemma's is even better on the phone (0.010 vs 0.027ᵉ). Both keep 100 % coverage and zero
non-translation. The emulator quality ranking survives the move to the phone.

**Latency is the separator, not RAM.** Both fit the 8 GB budget (2.75 GB worst-case peak,
at model-load). But gemma runs ~2.3× slower — ~35 s/page vs Qwen's ~15 s. gemma completed
only 11/17 pages because it exhausted its 6-min `PER_CHALLENGER_BUDGET_MS` slice
(`EngineBenchmarkTest`), **not** from a crash or OOM — the run logged `Benchmark complete`
cleanly. A wider budget would let it finish, but ~35 s/page is close to the ~52 s/page bar
that already ruled out `translategemma-4b` as too slow.

### Verdict — curated shortlist + tiers (ADR-0009)

ADR-0009's bar is *measured-best on the reference phone under a device-budget ceiling*, and
the ceiling that bites here is **latency**, not RAM.

- **Qwen2.5-1.5B → shipped selectable model, hosted tier (Apache-2.0).** The on-device
  winner: 17/17 stable, ~15 s/page, ~2 GB peak, residue 0.102 at full coverage. No auth
  friction. This is the model #90's picker offers.
- **gemma-2-2b-it → held out of the default shortlist; quality-best but latency-marginal
  (Gemma Terms → HF-auth tier).** Best residue (0.010) and readability, but ~35 s/page and
  ~2.75 GB peak. Not shipped as a routine option. If offered at all, it belongs behind an
  explicit "slower, higher quality" opt-in — a product call for #90, not decided here.
- **The 0.8b remains the default engine.** Nothing in this run changes the shipped default.

## Reproduce

```sh
# Full pass (baseline + challengers that fit the ~17-min budget):
eval/run-benchmark.sh

# Focused pass for one heavy challenger (skip the ~11-min enum baseline):
eval/run-benchmark.sh --skip-install \
  -Pandroid.testInstrumentationRunnerArguments.skipBaseline=true \
  -Pandroid.testInstrumentationRunnerArguments.challengers=hunyuan_mt_7b
```

Several heavy LLMs do not fit one instrumentation-timeout window; split the run with
`challengers=` (comma-separated engine names) and reuse the deterministic baseline
numbers across runs. A challenger that OOMs or times out is aborted and recorded,
never fatal to the run — results are also always recoverable from the run's `logcat.log`.

```sh
# Emulator, high-RAM: measure models that OOM on the phone (e.g. the 7B). Scope to the
# emulator so a connected phone is untouched, and skip the baseline. Trust only quality.
ANDROID_SERIAL=emulator-5554 eval/run-benchmark.sh \
  -Pandroid.testInstrumentationRunnerArguments.skipBaseline=true \
  -Pandroid.testInstrumentationRunnerArguments.challengers=cat_translate_1.4b,cat_translate_1.4b_i1,cat_translate_7b
```

## Next steps

- ~~Confirm `gemma-2-2b-it` and `Qwen2.5-1.5B-Instruct` on the physical phone.~~ **Done (#72)**
  — see the phone-confirmation section. Qwen2.5-1.5B is the shipped selectable model;
  gemma-2-2b-it is quality-best but ~35 s/page, held out of the default shortlist.
- **#90 product call:** decide whether gemma-2-2b-it ships at all as a "slower, higher
  quality" HF-auth opt-in, or is dropped from the picker entirely.
- **Re-run `translategemma-4b` uncapped** (its own focused pass) to get all 17 cases — though
  its ~52 s/page already reads as too slow for on-device.

## Feeds

- **#72** — **resolved.** Phone-confirmed both leaders (run `20260818-104743`). Quality held;
  latency split them. Shortlist: **Qwen2.5-1.5B** ships (hosted, Apache-2.0, ~15 s/page);
  **gemma-2-2b-it** held out as quality-best-but-slow (~35 s/page, HF-auth). CAT-1.4b remains
  the fallback. Qwen3/Hunyuan/CAT-7B stay out on capability grounds.
- **ADR-0008** — the pre-registered promotion trigger now has a measured result that
  *overturns* the "specialist beats general" premise: general small instruct models win the
  bake-off. Licence check needed before shipping (Gemma terms / Qwen Apache-2.0).
