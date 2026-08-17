# Challenger LLM bake-off results (#84)

Corpus: 17 eval cases / 145 story boxes, ADR-0004 page-level id-keyed batch path.
All challengers ran through `EngineBenchmarkTest` with `supportsIdKeyedBatch() = true`,
scored by `eval/run-eval.py` against the ADR-0004 gate. Q4_K_M tier unless noted.

Two devices, 2026-08-17:
- **Phone** — Samsung SM-S911B (Galaxy S23, 8 GB RAM). Latency + RAM are real here.
- **Emulator** — Pixel 10 Pro AVD, arm64-v8a, **16 GB RAM**, on an Apple-Silicon host.
  Used to run models that OOM/thrash on the phone. **Quality metrics are valid; latency
  and RAM are host-CPU artifacts, not phone-representative.**

## Results (phone unless marked ᵉ = emulator)

| Engine | Role | JP-residue (gate 0) | Coverage | Readability | Latency/page | Peak PSS | Outcome |
|---|---|---|---|---|---|---|---|
| **gemma-2-2b-it** ᵉ | gate | **0.027** | 100 % | 1.107 | ~5 sᵉ | 3.7 GBᵉ | **Best quality** — 12× less residue than CAT-1.4b |
| **qwen25_1.5b** ᵉ | gate | **0.102** | 100 % | 1.007 | ~2 sᵉ | 2.2 GBᵉ | Excellent; smallest/fastest |
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
  These are the **strongest on-device candidates found** — pending physical-phone latency/RAM
  confirmation (both fit the 8 GB budget at 1.7 GB / 1.0 GB Q4).

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

- **Confirm `gemma-2-2b-it` and `Qwen2.5-1.5B-Instruct` on the physical phone.** Both fit
  the 8 GB budget (1.7 GB / 1.0 GB Q4); the emulator gives quality but not real latency/RAM.
  This is the deciding measurement for #72.
- **Re-run `translategemma-4b` uncapped** (its own focused pass) to get all 17 cases — though
  its ~52 s/page already reads as too slow for on-device.

## Feeds

- **#72** — the promotion candidate has shifted: `gemma-2-2b-it` (residue 0.027) and
  `Qwen2.5-1.5B` (0.102) far outscore CAT-1.4b (0.252). Promote whichever confirms best on
  the phone; CAT-1.4b is the fallback. Qwen3/Hunyuan/CAT-7B remain out on capability grounds.
- **ADR-0008** — the pre-registered promotion trigger now has a measured result that
  *overturns* the "specialist beats general" premise: general small instruct models win the
  bake-off. Licence check needed before shipping (Gemma terms / Qwen Apache-2.0).
