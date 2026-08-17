# Challenger LLM bake-off results (#84)

Measured on-device: **Samsung SM-S911B (Galaxy S23, 8 GB RAM)**, 2026-08-17.
Corpus: 17 eval cases / 145 story boxes, ADR-0004 page-level id-keyed batch path.
All challengers ran through `EngineBenchmarkTest` with `supportsIdKeyedBatch() = true`,
scored by `eval/run-eval.py` against the ADR-0004 gate. Q4_K_M tier unless noted.

## Results

| Engine | Role | JP-residue (gate 0) | Coverage | Readability | Latency/page | Peak PSS | Outcome |
|---|---|---|---|---|---|---|---|
| **cat_translate_1.4b** | gate | **0.252** | 100 % | 0.894 | ~19 s | — | Best challenger; still FAILs gate |
| llm 0.8b (incumbent) | gate | 0.388 | 100 % | 0.843 | ~14 s | — | Shipped default / baseline |
| qwen3_4b | gate | 0.964 | (partial) | 0.000 | 120 s cap | ~2.9 GB | Unfit — thinking model |
| hunyuan_mt_7b (Q3_K_M) | gate | 0.900 | (1 bubble) | 0.025 | 120 s cap | ~3.4 GB | Unfit — too slow / RAM-thrashes |
| mlkit | floor | 0.014 | 100 % | 0.992 | ~0.26 s | — | Floor, not gate-ranked |

Latency is the page-level id-keyed batch call; the 120 s figure is the bridge's
`BATCH_TIMEOUT_MS`, i.e. the model did not finish a page within the budget.

## Findings

- **CAT-Translate 1.4b is the winning challenger.** It is the only larger model that
  both runs acceptably on-device and beats the 0.8b incumbent: Japanese-residue 0.252
  vs 0.388 and readability 0.894 vs 0.843, at roughly +40 % latency. Per ADR-0008's
  promotion rule this is the sibling **#72 should ship as the on-device opt-in.**

- **No non-CAT challenger beats it — both are unfit on this device.**
  - **Qwen3-4B** is a *thinking* model: on the bare id-keyed prompt it emits
    `<think>…` reasoning and runs to the 120 s cap every page, producing garbage
    (residue 0.96, readability 0.0). Wrong tool for the prompt shape.
  - **Hunyuan-MT 7B** produces *correct* translation for the first bubble
    (`[0] Ah… that's amazing`) but decodes only ~3–7 tokens in 120 s (~40 s/token).
    Peak PSS (3.4 GB) sits below the 3.8 GB Q3 weight file, so the model is
    demand-paging from flash every token — it does not fit the device's usable RAM.
    Q4 (4.6 GB) OOM-killed the process on load outright.

- **No LLM passes the ADR-0004 gate.** Every gate engine leaves Japanese residue
  (> 0). The 0.8b rightly stays the shipped default; CAT-1.4b reaches enthusiasts via
  the ADR-0001 custom-model slot until measured adequate.

- **Confirms ADR-0008's RAM claim.** "Above ~1.5B params, weights alone consume the
  entire budget-tier RAM." The 4B is borderline; the 7B thrashes even at Q3 and OOMs
  at Q4. The 8 GB reference device tops out around the 1.4B sibling.

## Reproduce

```sh
# Full pass (baseline + challengers that fit the ~17-min budget):
eval/run-benchmark.sh

# Focused pass for one heavy challenger (skip the ~11-min enum baseline):
eval/run-benchmark.sh --skip-install \
  -Pandroid.testInstrumentationRunnerArguments.skipBaseline=true \
  -Pandroid.testInstrumentationRunnerArguments.challengers=hunyuan_mt_7b
```

Four heavy LLMs do not fit one instrumentation-timeout window; split the run with
`challengers=` (comma-separated engine names) and reuse the deterministic baseline
numbers across runs. A challenger that OOMs or times out is aborted and recorded,
never fatal to the run — results are also always recoverable from the run's `logcat.log`.

## Feeds

- **#72** — promote CAT-Translate 1.4b as the on-device opt-in; Qwen3 / Hunyuan are
  out on capability grounds, not just quality.
- **ADR-0008** — the pre-registered promotion trigger now has a measured result:
  CAT-1.4b is the challenger of record; no non-CAT model is viable on the reference tier.
