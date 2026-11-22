# Tech scan, November 2026: runtimes, models and vision ideas for Yomu

Desk research for #472, 2026-11-21. Nothing here was run on a phone, an emulator or a desktop. Three research passes read primary sources (repos, model cards, papers, official docs) in parallel: Part A on ways of running the LLM, Part B on models, Part C on the vision side. Each part keeps its own sources and its own list of what could not be verified. Benchmark numbers are self-reported by whoever published them unless a part says otherwise.

This note continues `phone-translation-llm-candidates.md` (2026-09-16) and does not repeat it.

## Short answer

- **No new way of running the LLM beats what Yomu has.** An independent four-phone study has a CPU path as the fastest decoder for Qwen2.5-1.5B on every phone. GPU and NPU backends win only prompt processing, and Gemini Nano is blocked for an app that is not the top foreground app.
- **The speed left to take is inside Yomu's own decode loop**, not in a new backend.
- **One new model fits the default tier on a code path Yomu already ships:** Index-Translate-2B, a translation-trained Qwen3.5-2B under Apache-2.0.
- **Manga OCR moved a lot in 2026.** Two Apache-2.0 readers now score well above the original manga-ocr on a public benchmark of recent manga, mostly on handwriting and asides.
- **The cheapest rendering gain needs no model:** hyphenation and the `bubble` boxes the detector already produces.

## Ranked across the three parts

| # | Candidate | Part | Why it ranks here | Open question |
|---|---|---|---|---|
| 1 | Index-Translate-2B as an Experimental entry | B | Translation-trained Qwen3.5-2B, Apache-2.0, first-party Q4_K_M of 1.31 GB; loads as `qwen35`, the path the catalog's Qwen3.5 2B already uses | No Japanese→English figure is published; the trained prompt is Chinese |
| 2 | Batch the grammar-forced `[id]` tokens and keep the static prompt prefix | A | Output unchanged; a small JNI change; derived saving of roughly 1.5–3.5 s on a 15-bubble S23 page | Never measured on a phone; Qwen3.5 hybrids may need a saved state instead of a KV rewind |
| 3 | Desktop OCR comparison: Hayai v2.7 and Baberu v1.2 against the shipped reader | C | The public benchmark's gap is non-printed text, which is where Yomu skips reads today | Phone speed (far larger encoders), furigana behaviour, and where Yomu's 2025 reader sits on that benchmark |
| 4 | Hyphenation and bubble-shaped text in the typesetter | C | No model; bears directly on #463 (long Russian words) and #456 (long text panels) | Judged by eye only |
| 5 | Hy-MT2-7B and Index-Translate-9B for the 12 GB tier | B | Same templates as entries already shipped; both Apache-2.0 with first-party GGUF | Whether either beats Qwen3.5 9B on manga dialogue |
| 6 | int8 OCR decoder and ONNX Runtime 1.26 → 1.32 | C | OCR download from 140 MB to about 50 MB (estimate) | Accuracy after quantising, unmeasured |
| 7 | One sequence per bubble over a shared page prefix | A | 2.3× decode at 4 sequences on a desktop CPU | Each bubble stops seeing earlier translations; changes ADR-0013 |
| 8 | Thread count and pinning to big cores | A | 10–20% decode in Arm's and two papers' reports | Cannot be measured on an emulator |
| 9 | PP-OCRv6 tiny line detector for columns and ruby | C | A 1.8 MB model that outputs one polygon per column, the missing step in #438 | Thin evidence on manga |
| 10 | Bump the llama.cpp pin | A | Maintenance; needed for #7 | Possible slowdown on SME2 phones Yomu cannot test |

**Spikes filed from this table (2026-11-22):** #468 for row 1, #469 for row 2, #470 for row 3, and #471 for the `bubble` boxes in row 4. Row 4's hyphenation is already tracked in #463. Rows 5 to 10 have no issue.

**Watch, not ready:** multi-token-prediction speculative decoding (Qwen3.5 has the heads; no CPU numbers), Ternary-Bonsai 2 9B (a 9B-class model in 2.56 GB; speed unknown), Hy-MT2-1.8B at 1.25 bits (462 MB; blocked on an open llama.cpp PR), FumetoTranslate (a page-aware encoder–decoder on ONNX Runtime; no licence on the weights), LiteRT-LM.

**Blocked by licence:** PLaMo 3 Translate (the strongest Japanese-specific claims found), CharSpot OCR (per-character boxes and a furigana flag in 7 MB), LFM3.

**Skip:** llama.cpp OpenCL and Vulkan (no evidence past the October spike), the Hexagon NPU backend (decode 2–4× slower than CPU), Gemini Nano through AICore, ExecuTorch, MNN, MLC, the catalog models' vision towers (a separate 0.2–1.0 GB download each, for a small measured gain), neural inpainting, diffusion language models, a newer text detector (none found).

## What the scan confirms about the current design

- **One page per prompt is the right unit.** The one study that varied it found page-level better than line-level and found more than a page no better (Part B).
- **Sending text without the page image gives up little** once the whole page's text is in the prompt (Part B, Part C §8).
- **llama.cpp on CPU is the right runtime** for a decode-heavy page (Part A).
- **The ogkalu v4-s detector is still the best at phone size** (Part C §11).

## What was re-checked after the passes

- **OCR benchmark and cards.** The JMangaBench_Read leaderboard rows, the Hayai v2.7 and Baberu v1.2 cards and koharu's vision page were read a second time; the figures in Part C match.
- **Index-Translate-2B and Hy-MT2-7B.** Licence, file sizes, the benchmark table and the Chinese prompt were read a second time and match Part B.
- **Yomu's decode loop.** `ml/src/main/cpp/llama_jni.cpp` clears the KV cache for every page (line 252), decodes the prompt in one batch (line 331) and then calls `llama_decode` once per output token (line 378). The thread default is `min(cores, 4)` for both prefill and decode. Part A's first two candidates rest on this.
- **Typesetter and detector.** No hyphenation exists under `pipeline/` or `app/src/main`; `ml/src/main/java/com/yomu/ml/OnnxRuntime.kt` keeps `text_bubble` and `text_free` and drops `bubble`; ONNX Runtime is pinned at 1.26.0.
- **One correction made.** koharu's translation page names Index-Translate 9B and LFM3 1.7B, not the Index-Translate family alone.
- **One page that would not load.** The card for `fumetodev/Index-Translate-2B-JP-Manga-Finetune-v1` returned HTTP 401 on the second read. Everything Part B says about that fine-tune rests on a single reading, including the note that `llama_memory_seq_rm` cannot cut into Qwen3.5's recurrent layers.

---

## Part A: Running LLMs on Android for Yomu's translation step — state as of 2026-11-21

Scope: ways of *running* the model (runtime, backend, decoding technique). Model choice is out of scope.
Baseline: llama.cpp CPU, pinned at `7fe450e19` (2026-09-23, tag v0.5.0). Upstream head read locally is
`29b52d398` (2026-11-20, v1.0.0-241, 1454 commits later). Galaxy S23: Qwen2.5-1.5B Q4_K_M, 61–71 tok/s
prefill, 15–19 tok/s decode, 5–15 s per page.

### Short answer (runtimes)

Nothing found beats llama.cpp on CPU for Yomu's decode-dominated workload. The best independent
measurement (SIGMETRICS 2027 paper, five frameworks, four Snapdragon phones) has a CPU path as the fastest
decoder for Qwen2.5-1.5B on all four phones: llama.cpp on three, MNN on the oldest. No GPU or NPU path
wins decode. GPUs and NPUs win only on prefill, which is roughly a fifth of a Yomu page (my estimate).

The gains worth chasing are inside Yomu's own JNI loop. Decode on a phone CPU is limited by memory
bandwidth, so extra tokens evaluated in the same forward pass are close to free. Yomu's own numbers show
the headroom: prefill is 3.2–4.7 times faster per token than decode. Today every output token gets its
own pass, including the `[id]` literals the grammar already forces.

### Ranked table (runtimes)

| # | Candidate | What it is | Expected payoff for Yomu | Cost / risk | Evidence strength | Verdict |
|---|---|---|---|---|---|---|
| 1 | Batch grammar-forced tokens, keep the static prefix in KV | Feed the forced `[id]` literal in the same `llama_decode` as the sampled newline; stop clearing the KV cache between pages | Roughly 1–2.5 s less on a 15-bubble page for forced tokens, 0.5–1 s for the prefix; output unchanged | Small JNI change in `llama_jni.cpp`; tokenisation of the literal must be handled per model | Medium: mechanism documented, size derived from Yomu's own rates, not measured | **Spike** |
| 2 | One sequence per bubble over a shared page prefix | Prefill the page once, fork N sequences, decode N bubbles per pass | Decode throughput 2–3× at 4–8 sequences; page time down by roughly 40–55% | JNI rewrite to multi-sequence; each bubble no longer sees earlier translations (quality risk, touches ADR-0013); more heat | Medium: desktop CPU measurement plus Yomu's prefill:decode ratio; no phone number | **Spike** |
| 3 | Thread count, core pinning, separate prefill/decode thread counts | Pin to big cores; fewer threads for decode than for prefill | 10–20% decode | Small code change; per-SoC topology logic; cannot be measured on the emulator | Medium: Arm and two papers agree on direction; no S23 number | **Spike** (needs the S23) |
| 4 | Bump the llama.cpp pin to current master | 1454 commits | About zero on the S23 by itself; prerequisite for #2; small flash-attention fix | Deprecated batch API, on-device check; possible regression on SME2 phones | High (read from the repo) | **Do** as maintenance |
| 5 | MTP speculative decoding (`--spec-type draft-mtp`) | Use the model's own multi-token-prediction head as drafter | Unknown on CPU; 1.4–2.4× on desktop GPUs; only Qwen3.5 and large Gemma 4 | Not available for the default model; needs MTP GGUFs; one Apple-silicon report shows a slowdown | Low for phones | **Watch** |
| 6 | LiteRT-LM (GPU + MTP + constrained decoding) | Google's runtime, Apache-2.0 | Possibly 2× decode for Gemma 4 on flagship GPUs; nothing for Qwen2.5-1.5B | Second runtime, `.litertlm` conversion per model, GBNF to Lark port | Low–medium: vendor numbers only | **Watch** |
| 7 | MNN-LLM | Alibaba's runtime, Apache-2.0 | 1.3× decode on Snapdragon 8 Gen 3, slower than llama.cpp on newer chips | Second runtime, own model format, no grammar constraint found | Medium (independent paper) | **Watch**, leaning skip |
| 8 | llama.cpp OpenCL on Adreno, re-test | Much Q4_K work landed since the pin | No evidence it changes the Adreno 740 result | Cheap re-run if the spike harness still exists | Low | **Skip** (no new evidence) |
| 9 | llama.cpp Vulkan | Cross-vendor GPU backend | None shown; slower than CPU on Adreno 740 | Driver bugs per vendor | Low | **Skip** |
| 10 | llama.cpp Hexagon NPU backend | Snapdragon NPU, loads GGUF | Negative: decode 2–4× slower than CPU | Snapdragon only | Medium–high (independent papers) | **Skip** |
| 11 | Draft-model, EAGLE-3, DFlash, DSpark, n-gram speculative decoding | Separate drafter or lookup | None for a 1.5B target on CPU; n-gram needs repeated text | Second model in RAM, drafters are per-target | Low–medium | **Skip** |
| 12 | ExecuTorch | PyTorch runtime | No measured advantage | `.pte` export per model, second runtime | Low | **Skip** |
| 13 | Qualcomm Genie / GenieX, Cactus, MLC LLM | Vendor or NPU-first runtimes | None for this workload | Snapdragon-only or own formats | Medium | **Skip** |
| 14 | Android AICore / Gemini Nano (ML Kit GenAI Prompt API) | System model, zero download | None: blocked when the app is not the top foreground app | Beta, quota, device list | High (official docs) | **Skip** |
| 15 | ML Kit on-device Translate | Old NMT API | No new API found | — | Medium | **Skip** |
| 16 | Diffusion LMs, llada.cpp, 1-bit weights | New model classes | Nothing phone-ready on the runtime side | — | Medium | **Skip** |

---

### 1. Batch grammar-forced tokens and keep the static prefix (spike)

**What Yomu does now.** `nativeGenerate` in
`ml/src/main/cpp/llama_jni.cpp`
calls `llama_memory_clear` at the start of every page, decodes the whole prompt, then runs one
`llama_decode` per output token. The grammar built in `LlamaTranslationBridge.buildBatchGrammar` is
`"[id] " line "\n"` per bubble, so after each newline the next `[id]` literal has exactly one legal
continuation. Those tokens are still sampled and decoded one at a time.

**Mechanism.** A forced token needs no sampling, only a KV update. Several KV updates fit in one
forward pass. So when the model emits the newline, append the tokens of the next `[id]` literal to the
same batch and call `llama_decode` once. The first `[id]` can ride on the prompt batch. llguidance calls
this "fast-forward tokens": "Grammars can be also used to speed up decode via fast-forward tokens"
(https://github.com/guidance-ai/llguidance). Yomu does not need llguidance for it (that would add a Rust
toolchain, per `ml/llama.cpp/docs/llguidance.md`); the forced literal is known from the page.

**Size of the gain (derived, not measured).** The literal is about 3–4 tokens per bubble, depending on
the tokenizer. A sequential decode step costs 53–67 ms on the S23 (15–19 tok/s); an extra token inside a
batch costs about 14–16 ms if it runs at the prefill rate (61–71 tok/s). That saves roughly 40–50 ms per
forced token: about 1–2.5 s on a 15-bubble page, more in relative terms on pages of short bubbles. Small
batches may not reach the full prefill rate, so treat this as an upper estimate. Output is identical
except that the literal's tokenisation is fixed by Yomu.

**Why batching is cheap on a phone CPU.** Arm's decode roofline material shows single-token decode is
memory-bound: on a Raspberry Pi 5, Qwen2.5 Q4_0 decode peaks at 2 threads and is 19.4% slower at 4, while
prefill keeps scaling to 3.1× at 4 threads
(https://learn.arm.com/learning-paths/cross-platform/llm-decode-roofline/threads-and-size/, undated).

**KV prefix reuse.** The static part of the batch prompt (chat-template header plus the one-sentence
instruction) is about 35–55 tokens (estimate). At 61–71 tok/s that is 0.5–0.9 s per page. Replace the
unconditional clear with a common-prefix check and `llama_memory_seq_rm(mem, 0, n_common, -1)`; llama.cpp's
server does the same thing per slot (https://github.com/ggml-org/llama.cpp/discussions/13606). The saving
is larger on the per-line fallback path, where every line repeats the prefix. A changed target language
or model simply misses the cache.

**Cost.** One native function and its tests. Risks: a model whose tokenizer merges `]` with the following
space; the cancel and timeout paths must still work mid-batch.

### 2. One sequence per bubble over a shared page prefix (spike)

**Mechanism.** Decode the page prompt once into sequence 0, copy it to N sequences with
`llama_memory_seq_cp` (shared cells, no recompute), start each sequence with its own `[id] ` literal and
generate N bubbles per `llama_decode`. Each sequence has a one-line grammar. APIs exist at the pin and at
head (`n_seq_max`, `kv_unified`, `llama_memory_seq_cp` in `ml/llama.cpp/include/llama.h`); head adds
`llama_batch_ext` (#24669, 2026-09-24) and a cheaper unified KV path for multi-sequence batches
(#29510, 2026-11-02).

**Reported speedups.**
- `llama-batched-bench`, CPU only, Intel Ultra 9 285K, Phi-4-mini Q4_K_M, 2025-12-14: generation
  25.7 tok/s at 1 sequence, 58.6 at 4 (2.3×), 86.3 at 8 (3.4×), 118.5 at 16 (4.6×)
  (https://github.com/ggml-org/llama.cpp/discussions/18030). Desktop x86, one user's run.
- No phone-CPU run of this benchmark was found. The ceiling on the S23 is the prefill rate: 61–71 tok/s
  against 15–19 tok/s, so at most about 3.2–4.7× (my arithmetic from Yomu's numbers).

**Cost for Yomu.**
- JNI rewrite: per-sequence sampler, grammar, status, streaming callback and abort handling.
- Quality: a bubble's translation no longer conditions on the earlier *translated* lines, only on all the
  Japanese lines. Names and pronouns may drift between bubbles. This changes ADR-0013's page-level batch
  and needs a by-eye comparison, since quality evals are retired.
- Heat: the CPU does more arithmetic per second instead of waiting on memory.
- It replaces most of candidate 1's forced-token work (the literal is injected at fork time), so spike 1
  first: it is cheaper and lossless.

### 3. Thread count, pinning, and separate prefill/decode threads (spike, needs the S23)

**What Yomu does now.** `RuntimeLimits.DEFAULT_THREADS = min(cores, 4)`; the same value is used for
`n_threads` and `n_threads_batch`; no affinity. The S23 has 1 prime + 4 performance + 3 efficiency cores,
so four unpinned threads can land on efficiency cores.

**Evidence.**
- "setting the number of threads equal to the number of big cores" usually gives the best llama.cpp
  performance; adding efficiency cores generally lowers decode speed (arXiv 2410.03613v5, dated 2026-02-09,
  https://arxiv.org/html/2410.03613v5). No Snapdragon 8 Gen 2 row.
- Arm: pinning threads "helped us to improve the latency speed by over 10 percent" (2024-01-17, Cortex-A700
  phones, self-reported,
  https://developer.arm.com/community/arm-community-blogs/b/ai-blog/posts/generative-ai-on-mobile-on-arm-cpu).
- Arm roofline: use the "decode knee" thread count for decode and all cores for prefill (URL above).
- The SIGMETRICS paper pins inference to mask `0xe0` by default (https://arxiv.org/html/2607.05475v2).

**How.** `ggml_threadpool_params` has `cpumask`, `strict_cpu`, `prio` and `poll`
(`ml/llama.cpp/ggml/include/ggml.h`), attached with `llama_attach_threadpool`. Core classes can be read
from `/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq`.

**Cost.** Small. The risk is a heuristic that is right on the S23 and wrong elsewhere, so keep the user's
thread setting as an override. An emulator cannot show this effect.

### 4. Bump the llama.cpp pin (do, as maintenance)

Read from `/usr/bin/git -C ml/llama.cpp log 7fe450e19..29b52d398` and the PR pages.

**Helps or is needed:**
- #32128 (2026-11-12): Android and Linux arm64 builds were using the 2×2 flash-attention tile because the
  code tested `__ARM_NEON__`; now 4×4. One data point: 7–8% on a Cortex-A53
  (https://github.com/ggml-org/llama.cpp/pull/32128). Flash attention is already on for Yomu
  (`LLAMA_FLASH_ATTN_TYPE_AUTO` is the default).
- #24669 `llama_batch_ext`, #29510 unified KV for multi-sequence, #32380 cheaper sequence removal: needed
  for candidate 2.
- Grammar: #29557 left factoring, #31153 recursion limit and faster candidate rejection. Yomu's grammar is
  two rules and already checks only the sampled token first, so no visible gain. Checked: the new limits
  (256 nesting levels, 2000 repetitions) do not affect `line ::= [...]{1,160}`.

**Does not help the S23:**
- #29219 (2026-10-13) KleidiAI SME2 kernels for Q4_K and Q6_K. Only on SME-capable CPUs; the S23 has none.
  Author's numbers, Apple M4 Pro, Llama-3.2-1B Q4_K_M, pp512: 387 vs 109 tok/s at 1 thread, 864 vs 700 at
  8 threads. A maintainer measured the opposite on the same chip with Qwen3.8-4B Q4_K_M at 10 threads:
  prefill 183.7 vs 223.1, decode 34.9 vs 50.0 tok/s with KleidiAI on
  (https://github.com/ggml-org/llama.cpp/pull/29219). Yomu builds with `GGML_CPU_KLEIDIAI`, so after the
  bump Q4_K_M models on SME2 phones take this path by default. That could be a regression Yomu cannot test.
  `GGML_KLEIDIAI_SME=0` in the environment turns it off (`ggml/src/ggml-cpu/kleidiai/kleidiai.cpp`).
- Arm's Android SME2 walk-through (undated, C1-Pro core, Llama-3.2-3B Q4_0, one thread): 12.3 vs 7.9 tok/s
  prompt, 9.1 vs 5.9 generation
  (https://learn.arm.com/learning-paths/mobile-graphics-and-gaming/performance_llama_cpp_sme2/run_llm/).
- #27851 (2026-09-26) tiled mul_mat for k-quants: x86 kernels only, and 0.78–0.84× at single-token decode
  (https://github.com/ggml-org/llama.cpp/pull/27851).
- #30808 and #27472: SVE kernels that require 256-bit vectors. Arm phone cores use 128-bit SVE (from
  memory, not re-checked). #30585 is a Q8_0 kernel.
- No big.LITTLE auto-detection for Android: `common_cpu_get_num_math` only handles x86 hybrids.
- New formats (FP8, NVFP4, Q1_0, more IQ types on GPU/NPU backends): nothing for Q4_K_M on ARM CPU.
- Memory: only direct-I/O copy fixes (#29749, #31321). No mmap or KV change relevant here. KV at context
  2048 for Qwen2.5-1.5B is about 56 MiB in f16 (my arithmetic), so KV quantisation is not worth it.

**Cost.** `llama_decode` and `llama_batch_get_one` are now marked deprecated (still present). AGENTS.md
requires an on-device translation check and a speed table for a llama.cpp bump. The main checkout already
carries an uncommitted local bump to `29b52d398`.

### 5. MTP speculative decoding (watch)

llama.cpp has had `--spec-type draft-mtp` since #22673 (merged 2026-05-16), so it is already in Yomu's
pin. The drafter is the model's own multi-token-prediction head: one transformer layer plus an output
head. `src/models/qwen35.cpp` implements it for Qwen3.5, which Yomu's catalogue carries at 0.8B, 2B, 4B
and 9B. Gemma 4 support (#23398, merged 2026-06-07) covered 31B and 26B-A4B; the author said E2B and E4B
"will be added later" and I found no MTP code in `src/models/gemma4.cpp` at head. Qwen2.5-1.5B, the
default, has no MTP head.

Numbers are all desktop GPU: about 2.4× on a DGX Spark with Qwen3.6 q8_0, 1.74–1.85× on RTX 3060/3090
with Qwen3.6-27B, acceptance about 0.72–0.83 at 2–3 draft tokens
(https://github.com/ggml-org/llama.cpp/pull/22673). A counter-example on the closest hardware class:
Qwen3.5-9B Q4_K_M on an M1 Max (Metal), baseline 25.3 tok/s, every MTP setting slower (21.9 at n=2, 19.3
at n=6); closed as "not a bug" (https://github.com/ggml-org/llama.cpp/issues/23752). No CPU-only or phone
result exists. The PR also notes prefill can get slower and that it needed `--parallel 1`.

In principle it suits a memory-bound CPU (cheap drafter, batched verification), and it is lossless. A
single `llama-cli` run on a phone with a Qwen3.5-2B MTP GGUF would settle it. Integration would go through
`common/speculative.h`, and Yomu's own grammar sampler would have to verify drafts.

### 6. LiteRT-LM (watch)

- Apache-2.0, v0.19.1 on 2026-11-05, Kotlin API stable, Maven `com.google.ai.edge.litertlm:litertlm-android`
  (https://github.com/google-ai-edge/LiteRT-LM, https://developers.google.com/edge/litert-lm/android).
- Own format `.litertlm`; no GGUF loading mentioned. Converted models listed: Gemma 4 E2B/E4B, Gemma 3n,
  Gemma 3 1B, Phi-4-mini, Qwen2.5-0.5B/1.5B, Qwen3-0.6B (https://developers.google.com/edge/litert-lm/overview).
- Constrained decoding through llguidance: regex, JSON Schema, Lark grammar. Documented for C++ only
  (https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/cpp/constrained-decoding.md). v0.19.0
  added GPU logit masking that works with MTP decoding.
- Self-reported numbers. Gemma 4 E2B on a Samsung S26 Ultra: CPU 557 prefill / 47 decode tok/s; GPU
  3808 / 52. MTP gives "up to 2.2x" decode on that GPU
  (https://developers.googleblog.com/blazing-fast-on-device-genai-with-litert-lm/, 2026-05-19).
- Qwen2.5-1.5B on a Samsung S25 Ultra, dynamic int8, context 1280: CPU 297.6 / 34.3 tok/s, GPU
  1667.8 / 30.9 (https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct). The SIGMETRICS paper has
  llama.cpp CPU at 329.2 / 53.9 on another Snapdragon 8 Elite phone, so for Yomu's default model LiteRT-LM is
  slower at decode on both CPU and GPU (different measurers, different quantisation).
- GPU needs `libOpenCL.so` declared in the manifest; engine initialisation "can take up to about 10
  seconds".
- NPU paths need per-chipset compiled models and cover few models (MediaTek: Gemma 3 270M/1B/3n, Qwen3
  0.6B; 28 tok/s decode for Gemma 3n E2B on a Dimensity 9500, self-reported,
  https://developers.googleblog.com/mediatek-npu-and-litert-powering-the-next-generation-of-on-device-ai/).
  One blog reports constrained decoding returning "not supported" on the Qualcomm NPU and a compiled
  1024-token KV cap (unverified, secondary:
  https://dev.to/jdshah/what-i-learned-benchmarking-the-same-llm-on-my-phones-npu-gpu-and-cpu-4c80).

The only case with a real upside is Gemma 4 on a flagship GPU with MTP. That is a second runtime, a
converted model per catalogue entry, a grammar port, and no number for an Adreno 740 or any mid-range
phone. Revisit only if Gemma 4 becomes Yomu's default.

### 7. MNN-LLM (watch, leaning skip)

Apache-2.0 (https://github.com/alibaba/MNN). From Table 4 of arXiv 2607.05475v2, Qwen2.5-1.5B, 4-bit
weights, 256-token prefill and decode, tok/s:

| Phone (SoC) | llama.cpp CPU prefill / decode | MNN CPU prefill / decode |
|---|---|---|
| Xiaomi 14 (SM8650, 8 Gen 3) | 157.2 / 34.5 | 259.5 / 45.7 |
| Xiaomi 15 (SM8750, 8 Elite) | 329.2 / 53.9 | 259.4 / 49.5 |
| Xiaomi 17 (SM8850) | 417.4 / 51.6 | 187.8 / 42.3 |
| OnePlus 15 (SM8850) | 299.9 / 55.7 | 228.3 / 47.5 |

MNN is 1.3× faster at decode on the 8 Gen 3, the chip closest to the S23, and slower on the newer ones.
Its own release notes for the Hexagon backend say "Decode still runs fastest on CPU" (3.6.1, 2026-07-23,
https://github.com/alibaba/MNN/releases). MNN uses its own model format; I found no GGUF loading and no
grammar or JSON-schema constraint in the README or the 2026 release notes (the 3.5.0 sampler rework adds
penalties only). Conversion path not re-verified. A 1.3× gain on older chips does not pay for a second
runtime plus the loss of GBNF and user-supplied GGUFs (ADR-0001).

### 8. llama.cpp OpenCL on Adreno (skip; nothing new for the Adreno 740)

Since the pin the Qualcomm team merged a lot of Q4_K work: cooperative dp4a GEMM for q4_0/q4_k/q6_k
(#30741, 2026-10-19), tiled Q4_K GEMM (#30412, 2026-10-28), faster decode with flash attention (#31633,
2026-11-04), decomposed flash attention (#32046, 2026-11-12), Q4_K binary kernels (#30948, 2026-11-16).
None of these PRs names a phone GPU or compares with CPU. The numbers given are for large models,
apparently on Snapdragon X2 laptops (for example gemma-4-E4B Q4_K_M decode 24.98 to 35.29 tok/s in #32046).
The binary kernel library "currently targets X2 GPUs" and is a manual download from Qualcomm's software
centre. `docs/backend/OPENCL.md` lists Adreno 750, 810, 830, 840, X1-85, X2-90 as verified; the 740 is
not listed.

Independent phone data says GPU does not beat CPU at decode even on newer Adrenos. arXiv 2607.05475v2,
llama.cpp, Qwen2.5-1.5B: Xiaomi 14 GPU 365.7 / 31.8 vs CPU 157.2 / 34.5; Xiaomi 15 GPU 680.9 / 48.6 vs CPU
329.2 / 53.9. For Llama-3.2-1B on the Xiaomi 14: GPU decode 22.5 vs CPU 41.9. The GPU wins prefill by
2–2.7×, which is the smaller part of a Yomu page. This is not evidence that changes the October result.

### 9. llama.cpp Vulkan (skip)

- Snapdragon 8 Gen 2 / Adreno 740, Android 15, Qwen3.5-2B Q4_K, 2026-03-15: generation 6–10 tok/s on
  master, "slower than CPU"; an earlier branch produced gibberish. Closed unresolved on 2026-04-30
  (https://github.com/ggml-org/llama.cpp/issues/20603).
- Exynos 2400 / Xclipse 940: a fix on 2026-10-02 took pp512 from about 10 to about 450 tok/s for ubatch of
  128 and above; no decode or CPU numbers (https://github.com/ggml-org/llama.cpp/pull/28531).
- The Qualcomm Vulkan fixes (#30829, #30826, #30824) were tested on Snapdragon X laptops under Windows.
- Mali: the 2410.03613 study found Mali-G720 prefill roughly 10× slower than Adreno 750 and crashes on
  Mali-G78, using MLC LLM.

### 10. llama.cpp Hexagon NPU backend (skip)

It loads ordinary GGUFs, now supports Q4_K and Q2_0, builds for Hexagon v73 (the S23's NPU) through v85,
opens an unsigned protection domain, and is MIT like the rest of the repo
(`ml/llama.cpp/docs/backend/snapdragon/README.md`, `ggml/src/ggml-hexagon/ggml-hexagon.cpp`). It is
labelled experimental and is Snapdragon-only.

Measured results are negative for decode:
- arXiv 2607.05475v2, llama.cpp commit `eadc418`: Qwen2.5-1.5B on Xiaomi 14, NPU 51.4 / 13.8 tok/s vs CPU
  157.2 / 34.5; on Xiaomi 15, NPU 82.3 / 18.3 vs CPU 329.2 / 53.9. "The CPU is usually the best decode
  backend because of its low dispatch overhead and effective cache use."
- arXiv 2605.27435 (2026-05-22, Snapdragon 8 Gen 3, llama.cpp b7588, Q4_0): CPU beats the NPU at prefill
  by up to 1.6×; the NPU gains only 1.05–1.2× at decode; battery drain up 22–51% with full offload.
- The repo's own example (Llama-3.2-1B Q4_0, Hexagon v79): pp128 169 tok/s, tg64 51.5 tok/s. The paper has
  the CPU of that chip class at 70.1 tok/s for the same model.

The backend changed heavily after those papers' commits (about 39,000 lines since the pin), so the gap may
have narrowed. No newer phone measurement was found.

### 11. Other speculative decoding (skip)

- Draft model (`draft-simple`): needs a second model in RAM. On a memory-bound CPU a 0.5B drafter is only
  about 3× faster than a 1.5B target, so there is little to gain. arXiv 2607.17283 (2026-07-19, Apple
  silicon laptop): best case 1.61×, three of five configurations slower; it pays "only when verification
  is genuinely batch-parallel and the draft/target latency gap is real."
- EAGLE-3, DFlash, DFlash2, DSpark, LiliCorr (`ml/llama.cpp/docs/speculative.md`): drafters are trained
  per target. Listed ones cover Qwen3 1.7B/4B/8B and larger, Gemma 4 26B/31B, gpt-oss. None for
  Qwen2.5-1.5B or Qwen3.5. DSpark supports only Qwen3 backbones today.
- n-gram and lookup types: they draft from text already in the context. A translation does not repeat its
  prompt, so they do not apply.

### 12. ExecuTorch (skip)

v1.6.0 (12 Nov) lists XNNPACK, Vulkan (kernels tuned for Mali and Adreno), Qualcomm, Samsung backends;
MediaTek is "temporarily unsupported"; v1.4.0 added `grammar` and `grammar_type` to its generation config
(https://github.com/pytorch/executorch/releases). Models must be exported to `.pte` one by one; no GGUF
import was found. The 2026 release notes carry no phone tok/s figures. The only numbers found are from
2024 (Llama 3.2 1B, OnePlus 12: 260 prefill / 50.2 decode, self-reported, seen only in a search summary:
unverified). Licence not re-checked today.

### 13. Qualcomm Genie / GenieX, Cactus, MLC LLM (skip)

- Genie (QAIRT): NPU prefill is very fast (1219–1625 tok/s for Qwen2.5-1.5B) but decode is 20.7–23.1
  tok/s, under half of llama.cpp CPU on the same phones (arXiv 2607.05475v2). Models are compiled per
  chipset. Snapdragon only. QAIRT redistribution terms were not found.
- The `NexaAI/nexa-sdk` repository page now describes Qualcomm's GenieX: BSD-3-Clause plus "Qualcomm's
  Terms of Use", Android artifact `com.qualcomm.qti:geniex-android:0.10.0`, Snapdragon 8 Elite and 8 Elite
  Gen 5 only, developer preview. For GGUF it dispatches to llama.cpp (https://github.com/NexaAI/nexa-sdk,
  https://github.com/qualcomm/geniex). How Nexa and GenieX relate was not verified.
- Cactus: Apache-2.0 including binaries; README lists its own models and Apple-silicon results only; no
  Android numbers (https://github.com/cactus-compute/cactus). A guide says it left GGUF for its own
  format (unverified, secondary).
- MLC LLM: slowest GPU path in the paper (Qwen2.5-1.5B decode 29.9 on Xiaomi 15, 16.8 on Xiaomi 14). The
  releases page shows a single `v0.1.dev0` pre-release (https://github.com/mlc-ai/mlc-llm/releases).
- PowerInfer-2: nothing runnable found; not investigated further.

### 14. Android AICore / Gemini Nano through ML Kit GenAI (skip)

Official overview (https://developers.google.com/ml-kit/genai):
- "GenAI API inference is permitted only when the app is the top foreground application." Use from the
  background, "including using a foreground service", returns `ErrorCode.BACKGROUND_USE_BLOCKED`. Yomu
  draws over another app, so it is not the top foreground application. This alone rules it out.
- "AICore enforces an inference quota per app"; bursts return `BUSY`, long-run use can return
  `PER_APP_BATTERY_USE_QUOTA_EXCEEDED`.
- Runs locally: "Functionality remains the same without reliable internet connection".
- APIs listed: direct model access (Prompt API), agentic workflows, speech recognition, speech
  translation, text to speech, image description, summarisation, proofreading, rewriting, embeddings.
  There is no text translation API.

Prompt API (https://developers.google.com/ml-kit/genai/prompt/android/get-started,
https://developers.google.com/ml-kit/genai/prompt/android): beta, `com.google.mlkit:genai-prompt:1.0.0-beta4`,
"not subject to any SLA or deprecation policy", input under about 4000 tokens, API level 26+, "not
supported on devices with an unlocked bootloader", structured output available. Devices are those with
Gemini Nano 2, 3 or 4. "Short translations" is a listed use case. The validated-language list was not on
the pages I could read; a search summary said English and Korean only (unverified).

### 15. ML Kit on-device Translate (skip)

No new API. The docs still describe the Google Translate offline models, pivoting through English for
non-English pairs; usage guidelines last updated 2026-04-09
(https://developers.google.com/ml-kit/language/translation). It is not an LLM, which ADR-0016 excludes.

### 16. Other 2026 developments (skip for the runtime question)

- DiffusionGemma: one checkpoint, 26B MoE with about 4B active, Apache-2.0
  (https://ai.google.dev/gemma/docs/diffusiongemma/model_card). Not phone size. No llama.cpp or LiteRT-LM
  support found.
- llada.cpp: paper only (arXiv 2606.13740, 2026-06-11), LLaDA-8B on a mobile NPU, 17–42× over its CPU
  baseline. No repository or licence on the arXiv page.
- 1-bit and ternary weights: Q1_0 and Q2_0 are in mainline llama.cpp; Yomu already ships two ternary
  models. Whether a 1-bit model translates well is a model question. Reported speeds are secondary
  (unverified).
- Looped or weight-shared transformers at phone size with runnable weights: not searched in depth;
  nothing surfaced.

---

### Not verified (runtimes)

- No phone-CPU measurement exists for candidates 1, 2 and 5. The gains for 1 and 2 are derived from Yomu's
  S23 prefill and decode rates and one desktop benchmark.
- OpenCL on Adreno 740 after October 2026: no numbers anywhere.
- Hexagon backend speed at current head: the papers used older commits.
- Token counts for the `[id]` literal and the static prefix are estimates, not counted with a tokenizer.
- Qualcomm's QMX blog (2026-04, llama.cpp on Snapdragon 8 Elite Gen 5 CPUs): only the title loaded.
- Whether current Qwen3.5 GGUFs in Yomu's catalogue contain the MTP tensors.
- Prompt API language list and device list; LiteRT-LM NPU model and chipset matrix.
- ExecuTorch and MNN conversion details and current phone numbers.

### Sources (runtimes)

Local (read-only):
- `ml/llama.cpp` — git log `7fe450e19..29b52d398`; `docs/speculative.md`, `docs/backend/OPENCL.md`, `docs/backend/snapdragon/README.md`, `docs/backend/snapdragon/developer.md`, `docs/android.md`, `docs/build.md`, `docs/llguidance.md`, `tools/batched-bench/README.md`, `include/llama.h`, `ggml/include/ggml.h`, `src/llama-grammar.cpp`, `src/models/qwen35.cpp`, `src/models/gemma4.cpp`, `ggml/src/ggml-cpu/kleidiai/kleidiai.cpp`, `ggml/src/ggml-hexagon/ggml-hexagon.cpp`, `common/common.cpp`
- `ml/src/main/cpp/llama_jni.cpp`, `grammar_sampler.cpp`, `CMakeLists.txt`
- `ml/src/main/java/com/yomu/ml/LlamaTranslationBridge.kt`
- `core/src/main/java/com/yomu/core/TranslationSlot.kt`

llama.cpp PRs, issues, discussions:
- https://github.com/ggml-org/llama.cpp/pull/29219 (KleidiAI SME2 Q4_K/Q6_K)
- https://github.com/ggml-org/llama.cpp/pull/27851 (tiled mul_mat for k-quants)
- https://github.com/ggml-org/llama.cpp/pull/32128 (NEON 4×4 flash-attention tile)
- https://github.com/ggml-org/llama.cpp/pull/30412, /pull/30741, /pull/30948, /pull/31633, /pull/32046 (OpenCL)
- https://github.com/ggml-org/llama.cpp/pull/30829, /pull/28531 (Vulkan on Qualcomm and Samsung)
- https://github.com/ggml-org/llama.cpp/issues/20603 (Vulkan on Adreno 740)
- https://github.com/ggml-org/llama.cpp/pull/22673, /pull/23398, /issues/23752 (MTP)
- https://github.com/ggml-org/llama.cpp/discussions/18030 (batched-bench on CPU)
- https://github.com/ggml-org/llama.cpp/discussions/13606 (KV cache reuse)

Papers:
- https://arxiv.org/html/2607.05475v2 — "Is Your NPU Ready for LLMs?" (v2 2026-10-16, SIGMETRICS 2027)
- https://arxiv.org/html/2605.27435 — "When NPUs Are Not Always Faster" (2026-05-22)
- https://arxiv.org/html/2410.03613v5 — "Understanding Large Language Models in Your Pockets" (v5 2026-02-09)
- https://arxiv.org/abs/2607.17283 — "Lossless but Not Free" (2026-07-19)
- https://arxiv.org/abs/2606.13740 — llada.cpp (2026-06-11)

Arm:
- https://learn.arm.com/learning-paths/cross-platform/llm-decode-roofline/threads-and-size/
- https://learn.arm.com/learning-paths/mobile-graphics-and-gaming/performance_llama_cpp_sme2/run_llm/
- https://developer.arm.com/community/arm-community-blogs/b/ai-blog/posts/generative-ai-on-mobile-on-arm-cpu

Other runtimes:
- https://github.com/google-ai-edge/LiteRT-LM, https://github.com/google-ai-edge/LiteRT-LM/releases
- https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/cpp/constrained-decoding.md
- https://developers.google.com/edge/litert-lm/overview, https://developers.google.com/edge/litert-lm/android
- https://developers.googleblog.com/blazing-fast-on-device-genai-with-litert-lm/
- https://developers.googleblog.com/mediatek-npu-and-litert-powering-the-next-generation-of-on-device-ai/
- https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct
- https://github.com/alibaba/MNN, https://github.com/alibaba/MNN/releases
- https://github.com/pytorch/executorch/releases
- https://github.com/cactus-compute/cactus
- https://github.com/NexaAI/nexa-sdk, https://github.com/qualcomm/geniex
- https://github.com/mlc-ai/mlc-llm/releases
- https://github.com/guidance-ai/llguidance

Google system models:
- https://developers.google.com/ml-kit/genai
- https://developers.google.com/ml-kit/genai/prompt/android, https://developers.google.com/ml-kit/genai/prompt/android/get-started
- https://developers.google.com/ml-kit/language/translation
- https://ai.google.dev/gemma/docs/diffusiongemma/model_card

Secondary, used as leads only:
- https://dev.to/jdshah/what-i-learned-benchmarking-the-same-llm-on-my-phones-npu-gpu-and-cpu-4c80
- https://www.qualcomm.com/developer/blog/2026/04/llama-models-acceleration-on-cpu-qmx (body not readable)

---

## Part B: Models worth an on-device check for Japanese manga translation (since 2026-09-16)

Desk research, 2026-11-21. Nothing here was run on a phone or a desktop. Sizes, licences, hashes and dates come from the Hugging Face API and the repos' own files; benchmark numbers come from model cards and papers and are **self-reported** by whoever published them. Anything I could not trace to a primary source says **unverified**.

It continues `docs/research/phone-translation-llm-candidates.md` (2026-09-16) and does not repeat it. Models already in the catalog or ruled out there are mentioned only where something changed.

### How the numbers were computed (models)

- **Fit gate**, as in the earlier note: `file bytes + 800 MiB + KV bytes/token × 4096`. Budget 4.80 GiB for a nominal 8 GiB phone, 7.20 GiB for 12 GiB.
- **KV bytes/token**: `layers × 2 × KV heads × head dim × 2` from each `config.json`. Qwen3.5-based models use the effective figures the earlier note derived (12,288 for 2B, 32,768 for 4B/9B; the DeltaNet state of 19–50 MiB is not counted).
- **llama.cpp support** was checked two ways: the file list of `ggml-org/llama.cpp/src/models` on GitHub, and the working tree of `ml/llama.cpp` in this repository (`29b52d398`, dated 2026-11-20; `git submodule status` marks it as differing from the pinned commit, so read "vendored" below as "the working tree", not the pin). Both have `qwen35`, `hunyuan-dense`, `plamo3`, `lfm3`, `gemma4-assistant`, `granite`, `hrm_text`. Neither has `aion` or `olala`.

### Short answer (models)

1. **One genuinely new candidate fits the default tier and the existing code path: Index-Translate-2B** (bilibili Index team, Apache-2.0, 2026-09-28). It is Qwen3.5-2B retrained for translation, so it loads as `qwen35` and should run at the speed of the Qwen3.5 2B already in the catalog. Its maker reports a large gain over the Qwen3.5-2B base and a smaller one over Hy-MT2-1.8B. Two other manga-translation projects adopted the family within six weeks: koharu the 9B, FumetoReaderPlus a fine-tune of the 2B.
2. **A community manga fine-tune of it exists and speaks Yomu's page format**: `fumetodev/Index-Translate-2B-JP-Manga-Finetune-v1` takes a page as a JSON object keyed `"1"…"N"` and returns the same keys. English only, one developer, training data undisclosed.
3. **For 12 GB phones, two translation-specialised models are worth a run against Qwen3.5 9B**: Hy-MT2-7B (older, overlooked; 4.62 GB) and Index-Translate-9B (5.78 GB).
4. **Ternary-Bonsai 2** (Qwen3.5 bases, 2/4/9B) supersedes the Ternary-Bonsai 4B/8B entries on paper: smaller KV, and its `Q2_0` files load in mainline. No quality numbers are published, and the earlier spike found `Q2_0` slow on ARM CPU. Check speed before anything else.
5. **The best Japanese-specific evidence belongs to a model Yomu cannot ship**: PLaMo 3 Translate (0.8B/2B/8B, first-party GGUF with a phone-targeted Q4_0). Its licence requires passing the agreement to every recipient and excludes open-source apps from its simpler end-user route.
6. **Nothing new from Qwen, Google, Mistral or Hugging Face at phone size since the last note.** Microsoft's Aion 2.3B (MIT) and OVH's Olala (Apache-2.0) are new but do not load in mainline llama.cpp. Liquid's LFM3-1.7B loads but carries a revenue-capped licence, thinks by default and has no translation evidence.

If only one thing gets tested: Index-Translate-2B and its manga fine-tune against Qwen2.5-1.5B and Hy-MT2 1.8B on the fixture pages.

### Ranked table (models)

"Need" is the fit gate at 4096 tokens. All sizes are the Q4_K_M file unless stated.

| # | Model | File · bytes · need | Licence | Japanese / translation evidence | llama.cpp | Verdict |
|---|---|---|---|---|---|---|
| 1 | **Index-Translate-2B** (IndexTeam, 2026-09-28) | `IndexTeam/Index-Translate-2B-GGUF` · `Index-Translate-2B.Q4_K_M.gguf` · 1,312,164,352 · 2.05 GiB | Apache-2.0 | Translation-specialised Qwen3.5-2B. Card: FLORES COMET-22 0.8655 (Hy-MT2-1.8B 0.8522, Qwen3.5-2B base 0.6983); 21-language average, no JA→EN figure. Native glossary and JSON-preservation instructions. | `qwen35` ✓ (same as catalog Qwen3.5 2B) | **Check on device.** Best evidence-to-risk ratio; no new runtime work. |
| 2 | **Index-Translate-2B JP-Manga fine-tune v1** (fumetodev, 2026-10-11) | `fumetodev/Index-Translate-2B-JP-Manga-Finetune-v1-GGUF` · `index-translate-2b-manga-v1-Q4_K_M.gguf` · 1,274,389,024 · 2.01 GiB | Apache-2.0 (repo tag); training data undisclosed | JA→EN manga only. Card: chrF++ 46.7 on 2,553 held-out manga lines (its Hy-MT2 fine-tune 45.2); page mode 47.2. OpenMantra chrF++ 30.38. Trained page format is id-keyed JSON, 2–8 lines. | `qwen35` ✓; own chat template (no empty think block) | **Check on device** as an English-only entry. Decide on the provenance question first. |
| 3 | **Hy-MT2-7B** (Tencent, 2026-05) | `tencent/Hy-MT2-7B-GGUF` · `Hy-MT2-7B-Q4_K_M.gguf` · 4,624,648,896 · 5.59 GiB (12 GB tier) | Apache-2.0 | Same family and prompt as the catalog's Hy-MT2 1.8B. Third-party table (IndexTeam): FLORES COMET-22 0.8747, WMT24++ 0.8593, above both 2B-class models. | `hunyuan-dense` ✓ | **Check on device (12 GB tier).** Overlooked, not new. Cheapest 9B-tier addition. |
| 4 | **Index-Translate-9B** (IndexTeam, 2026-09-28) | `IndexTeam/Index-Translate-9B-GGUF` · `Index-Translate-9B.Q4_K_M.gguf` · 5,780,090,304 · 6.29 GiB (12 GB tier) | Apache-2.0 | Card: FLORES COMET-22 0.8789 (Qwen3.5-9B base 0.8316), WMT24++ 0.8601. | `qwen35` ✓ | **Check on device (12 GB tier)** against Qwen3.5 9B, which costs the same to run. |
| 5 | **Ternary-Bonsai 2 4B / 9B / 2B** (PrismML, 2026-09-23) | `prism-ml/Ternary-Bonsai-2-{4B,9B,2B}-gguf` · `…-Q2_0.gguf` · 1,212,103,520 / 2,556,640,128 / 546,689,504 · 2.04 / 3.29 / 1.34 GiB | Apache-2.0 | None. Cards carry no benchmark. Bases are Qwen3.5-4B/9B/2B. | `Q2_0` file ✓ on upstream b11556 or newer (card). `PQ2_0`/`PTQ1_0`/MTP files need the Prism fork. | **Check speed first**, then quality. A 9B-class model inside the 8 GB budget is the only reason to care. |
| 6 | **rikai jp-eng Bench 265 1.4B** (community, 2026-11-04) | `rikai-translate/rikai-jp-eng-Bench-265-1.4B` · `gguf/…-Q4_K_M.gguf` · 931,179,232 · 1.98 GiB | MIT | Fine-tune of CAT-Translate-1.4B, JA↔EN. Card claims parity with CAT-Translate-3.3B on automatic metrics; its own LLM-judge row puts it below 3.3B. | `llama` ✓ (same as catalog CAT-Translate 1.4B) | **Watch / cheap check.** Drop-in for an existing entry; single-author claims. |
| 7 | **CAT-Translate-7B** (CyberAgent, 2026-02) | `mradermacher/CAT-Translate-7b-GGUF` · `CAT-Translate-7b.Q4_K_M.gguf` · 4,537,758,048 · 5.51 GiB (12 GB tier) | MIT | Card: JA→EN average BLEU 41.06 (1.4B: 33.26); best of the family on BSD dialogue (33.75). JA↔EN only. No first-party GGUF. | `llama`-family ✓ (Mistral-type config) | **Watch.** English-only 12 GB entry; try after rows 3–4. |
| 8 | **PLaMo 3 Translate 0.8B / 2B / 8B** (Preferred Networks, 2026-10-27, blog 2026-11-05) | `pfnet/plamo-3-translate-{0.8b,2b,8b}-GGUF` · 573,126,848 / 1,807,203,040 / 5,280,445,728 · 1.35 / 2.65 / 5.95 GiB. Also Q4_0 "for smartphones": 547,543,232 / 1,698,577,120 | **PLaMo Community License** — fails the rule | Strongest Japanese-centric claims: maker says the 0.8B beats every earlier translation-specialised LLM on JA–EN. 53 languages. Context, glossary and history are first-class prompt fields. | `plamo3` ✓ (first-party GGUF) | **Skip on licence.** Worth one message to PFN, or a mention next to the Custom slot. |
| 9 | **EuroMoE-2.6B-A0.6B-Instruct-2512** (UTTER, 2025-12) | `mradermacher/EuroMoE-2.6B-A0.6B-Instruct-2512-GGUF` · 1,616,056,480 | Apache-2.0 | Japanese listed; instruction data "with focus on … machine translation". No translation score on the card. | GGUF declares `llama`; **unverified** that it runs | **Watch.** The only sub-3B MoE with Japanese that might load in mainline. |
| 10 | **Aion 1.0 Instruct 2.3B** (Microsoft, 2026-11-18) | third-party only: `Xylolabs/Aion-1.0-Instruct-2.3B-GGUF` · 1,573,133,856 · 2.68 GiB | MIT | Japanese listed. Card: "Translation chrF++ 43.18" with no benchmark named; MMMLU 45.51; about 6% multilingual training data. | ✗ `aion` not in mainline; community GGUFs ship a patch | **Watch.** Revisit when Microsoft's quantised files and upstream support land. |
| 11 | **Olala-v1.0-translate / mini-7A1B** (OVH, 2026-10-27/30) | `OVHaiLLM/Olala-v1.0-mini-7A1B-GGUF` · smallest is q5_k_m, 5,990,551,040 | Apache-2.0 | 6.8B total, about 1B active; Japanese among 18 translation languages; tuned for financial documents. | ✗ needs OVH's llama.cpp fork (card) | **Watch.** Interesting architecture, not runnable. |
| 12 | **LFM3-1.7B** (Liquid, 2026-10-23) | `LiquidAI/LFM3-1.7B-GGUF` · 1,099,627,808 | **LFM Open License v1.0** (commercial use only under US$10M revenue) | Japanese listed. No translation benchmark; positioned for agents. Thinks by default; card says disabling it "can be inconsistent". | `lfm3` ✓ (merged 2026-10-27) | **Skip.** Licence plus no translation evidence. |
| 13 | **MiniTranslator-2B** (community, 2026-11-20) | `Caldalis/MiniTranslator-2B-GGUF` · 1,561,318,176 | Apache-2.0 | Fine-tune of MiniCPM5-2B. Card says "competitive with Hy-MT2-1.8B"; numbers are in images. One day old. | `llama` ✓ | **Watch.** |
| 14 | **Granite 4.2 3B** (IBM, 2026-08) | `ibm-granite/granite-4.2-3b-GGUF` · 2,244,011,552 · 3.18 GiB | Apache-2.0 | Japanese is a "tested language". No translation benchmark. Thinks by default. | `granite` ✓ | **Skip.** Nothing suggests it beats Qwen3.5 4B at translation. |
| 15 | **Hy-MT2-1.8B 2-bit / 1.25-bit** (Tencent, 2026-05) | `tencent/Hy-MT2-1.8B-2Bit-GGUF` 600,534,880 · `…-1.25Bit-GGUF` 461,860,800 | Apache-2.0 | Card: 1.25-bit cuts the 1.8B to 440 MB and is 1.5× faster. | ✗ needs STQ kernel, PR #22836, still open | **Watch.** A real low-storage floor once the PR merges. |
| 16 | **CAT-Translate-3.3B** (CyberAgent, 2026-02) | third-party only: `Sundowner123/cat-translate-3.3b-gguf` · 2,066,389,824 · 3.33 GiB | MIT | Better than 1.4B on average (JA→EN BLEU 37.51 vs 33.26) but **worse on BSD dialogue** (26.51 vs 31.28). | `llama` ✓ | **Skip.** Twice the size, worse on the dialogue set. |
| 17 | **FumetoTranslate** (fumetodev, 2026-10-29) | `fumetodev/fumetotranslate-manga-ja-translator` · ONNX int8, 448 MB (encoder 358,353,978 + decoder 90,095,234) | **No licence on the weights** | Encoder–decoder, reads the whole page. Card: OpenMantra chrF++ 33.26, above both of the author's LLM fine-tunes. JA→EN only. | Not an LLM; runs on ONNX Runtime | **Watch.** Outside the llama.cpp slot and unlicensed, but the strongest size-for-quality claim found. |

Not ranked, skip, one line each:

- **LFM2-350M-ENJP-MT** (Liquid, 2025-09; 229,310,240 B): LFM licence; single-turn, needs a fixed system prompt; CyberAgent's table puts its JA→EN average BLEU at 24.91 against 29.71 for CAT-Translate-0.8B, which Yomu already ships. No successor exists.
- **LFM2.5-1.2B-JP-202606, LFM2.5-2.6B, LFM2.5-8B-A1B** (Liquid): same licence. The 8B-A1B MoE loads as `lfm2moe` (5,155,564,768 B).
- **NiuTrans LMT-60 0.6B/1.7B/4B/8B** (2025-11, Apache-2.0): Chinese–English-centric, Qwen3-based, third-party GGUFs only, no comparison against Hy-MT2 or Index-Translate found.
- **EuroLLM-9B-Instruct-2512** (2026-01, Apache-2.0; third-party Q4_K_M 5,582,838,912 B): Japanese listed, no translation numbers on the card.
- **NVIDIA Nemotron 3.5 Bolt E4B** (2026-10): a Gemma 4 E4B fine-tune for tool use, OpenMDW-1.1 licence, Q4_K_M 6,180,446,208 B — bigger than the catalog's Gemma 4 E4B file. **Nemotron-Labs Dory 4B** and **Sigma-distill**: English only.
- **FrogNano-4B-2609** (Microsoft): Qwen3.5-4B tuned for coding agents.
- **MiniCPM5-2B** (OpenBMB, 2026-09): card lists English and Chinese only.
- **SmolLM3-3B**: no Japanese on the card. **Phi-4-mini**: nothing newer; no translation evidence.
- **Tower+ 2B** (CC-BY-NC-SA), **Cohere North-Small-Translate** (218B, CC-BY-NC), **Yandex-8B-WMT26** (own licence, Russian-centred), **Confucius4-T3PO** (14B, zh–en), **Serval-1.8B / Noir-0.8B** (catsai's own model licence), **apple/SimpleSD-4B** (Apple licence).
- **Bocchi-TL-Qwen3.5-4B**: English→Russian only. **Index-NativeLong 2B/9B**: document translation, zh↔en and zh↔ja templates only.
- **Too big**: Hy4 / Hy4-flash (220B+), Hy-SimulMT-30B-A3B, Hy-MT2-30B-A3B, RakutenAI-3.0.1 (684B), Qwen3.8 (27B+), Kolibri-1 (78B), Ripple-65B, ELYZA-Thinking 32B/33B, Sugoi 14B/32B.
- **No TranslateGemma on Gemma 4 exists.** `google/translategemma-*` are still the January 2026 Gemma 3 models. No Gemma 5.

### Candidates in detail

#### 1. Index-Translate-2B (and 9B)

- **What it is.** bilibili's Index team retrained Qwen3.5-2B and 9B for translation: multilingual mid-training (167.77B tokens), then separate general / instruction-following / meme-translation specialists merged 0.8 / 0.1 / 0.1 and distilled. Technical report arXiv 2609.40181 (submitted 2026-09-30, revised 2026-10-12).
- **Licence.** Apache-2.0 on the model repos, the GGUF repos and the GitHub repo. Ungated.
- **Files.** First-party GGUF. 2B: `Index-Translate-2B.Q4_K_M.gguf`, 1,312,164,352 B, sha256 `044b313d29342bd3b2c77cbb64023ca0d209bd9b3247763f9d162767ef2d746a`, repo sha `3a0eca6c…`. 9B: `Index-Translate-9B.Q4_K_M.gguf`, 5,780,090,304 B, sha256 `9cc6758b0007f4cbf42ea3400768cbff91ae74ef8d640e467158348426d33713`, repo sha `645939fb…`. The repos also hold `mmproj` vision files, which Yomu does not need.
- **Architecture.** `qwen35` in the GGUF metadata. Same layer layout as Qwen3.5, so KV and speed should match the catalog entries. fumetodev's card says the official 2B GGUF carries a multi-token-prediction block that an April 2026 llama.cpp refused to load; the earlier Yomu note found the vendored loader skips MTP tensors. Not run here.
- **Evidence (card, self-reported).** Averages over 21 languages (FLORES, 420 directions) and English→20 languages (WMT24++):

  | Model | FLORES COMET-22 | WMT24++ COMET-22 | instTrans IFscore |
  |---|---:|---:|---:|
  | Index-Translate-9B | 0.8789 | 0.8601 | 0.8209 |
  | Hy-MT2-7B | 0.8747 | 0.8593 | 0.6079 |
  | **Index-Translate-2B** | 0.8655 | 0.8489 | 0.7569 |
  | Hy-MT2-1.8B | 0.8522 | 0.8401 | 0.4932 |
  | Qwen3.5-9B (base) | 0.8316 | 0.8073 | 0.0609 |
  | Qwen3.5-2B (base) | 0.6983 | 0.6933 | 0.2431 |

  The comparison with the bases is the useful one: the 2B gains 0.17 COMET-22 over the Qwen3.5-2B Yomu ships. **There is no Japanese→English row.** The instruction benchmarks are Chinese→X. The general-ability table shows what translation training cost: MMMLU 42.7 for the 2B.
- **Languages.** "150 languages"; the inventory is in the report, which I did not read. The official client maps `ja`, `en`, `pt`, `es`, `fr`, `de` and `ru` to language names, so every Yomu target has a code.
- **Prompting.** The training-aligned prompt is **in Chinese** (instruction, then the source text). A constrained form adds numbered hard requirements (glossary pairs, "keep the JSON markers unchanged") and soft ones (style, genre). One user message, no system message, greedy decoding, thinking disabled with `enable_thinking=false`. English instructions are not documented as trained.
- **Batch input.** Not documented as an id-keyed batch, but JSON-structure preservation is a trained hard constraint, and the manga fine-tune below shows the 2B holding a keyed JSON page.
- **Adoption.** koharu's translation page names Index-Translate 9B and LFM3 1.7B; the 2B is not named there (re-checked 2026-11-21, https://koharu.rs/models/translation). FumetoReaderPlus (GPL-3.0 Android reader) ships the manga fine-tune of the 2B.
- **Risks.** Chinese-centred training and evaluation; Japanese quality is inferred, not shown. Prompt must follow the maker's Chinese wording or be re-validated. `multilingual` label needs the language inventory from the report.

#### 2. fumetodev manga fine-tunes

One developer, three artefacts, all tied to the FumetoReaderPlus Android app. Every number is the author's. On a second read the same day, the Index fine-tune's model card returned HTTP 401, so this section rests on a single reading.

- **Index-Translate-2B-JP-Manga-Finetune-v1** (2026-10-11). `index-translate-2b-manga-v1-Q4_K_M.gguf`, 1,274,389,024 B, sha256 `7f8475eb80dce562fdf7c7283d78fd79275f5e5f19ffcd1118f0d7e91021b5d5`. Repo tagged Apache-2.0.
  - *Scores:* chrF++ 46.7 / COMET-22 79.3 on about 2,500 held-out manga lines, against 45.2 / 79.1 for the Hy-MT2 fine-tune (+1.5, 95% interval +0.7 to +2.3). On OpenMantra (1,586 professionally translated lines, evaluation only): chrF++ 30.38, COMET-22 72.97.
  - *Three trained layouts:* one line; one line plus a glossary (pairs joined with `、`, one to three per line); and **a page** as `{"1": …, "N": …}` with the line count stated, returning the same keys. Page mode was trained on 2 to 8 lines and scores 47.2 chrF++ against 46.7 for one line at a time.
  - *Failure rate without a grammar:* of 505 test pages, 3 came back with lines missing and 4 did not parse.
  - *Glossary adherence:* 84% of 200 sentences used a supplied name the model would not have chosen (base model: 93%).
  - *Limits the card states:* English output only (asked for German, Chinese, Korean or Spanish it still answered in English); lines under 4 characters score below the Hy-MT2 fine-tune (25.3 vs 27.6); its own chat template omits the empty think block and using the base template costs 0.4 chrF++; using the Hy-MT2 prompt costs 2.4.
  - *Speed:* about 10% fewer tokens per second than the Hy-MT2 fine-tune on CPU; about 230 ms a line on a Snapdragon 8 Elite with prefix reuse.
- **Hy-MT2-1.8B-JP-Manga-Finetune-v5** (2026-09-03). `manga-v5-Q4_K_M.gguf`, 1,133,080,512 B, sha256 `e4ed3cf8535c4e99e4b9f3d930ecc6f93bc3b3e0b4b5edca872c4cff5a112abe`. Apache-2.0 tag. One line at a time, English only. A `v3-multilingual` variant keeps the base model's other targets but is about 4 chrF++ behind on JA→EN. The card warns that `eos_token_id` must be 120020.
- **What is missing.** The training corpus is described only as manga dialogue with teacher-generated references. No dataset, no source list, no licence statement for the data. For an app that hosts its curated models, that is the open question, not the Apache tag.
- **No baseline against a general model.** None of the cards scores Qwen2.5-1.5B, Qwen3.5 or stock Hy-MT2 on the same test set, so "better than what Yomu ships" is untested.

#### 3. Hy-MT2-7B

- Released 2026-05-21 with the 1.8B Yomu ships; the earlier note did not assess it. Apache-2.0, first-party GGUF: `Hy-MT2-7B-Q4_K_M.gguf`, 4,624,648,896 B, sha256 `9f96256500f3fc1ab4d64336b58f52a949a95ad7516b0c229476eef782f9f77b`, repo sha `ab847266…`.
- 32 layers, 8 KV heads, head dim 128: 131,072 B/token. Need 5.59 GiB; smallest passing `totalMem` 9.31 GiB.
- Same chat template, prompt families (terminology, style, structured data, background information) and sampling as the 1.8B.
- Only Q4_K_M is published first-party. unsloth also hosts a GGUF repo.
- Evidence is in row 3 of the table: a competitor's measurement, which is at least not the maker's own.

#### 4. Ternary-Bonsai 2 (2B / 4B / 9B)

- Released 2026-09-23, five days after the Yomu spike that rejected Bonsai 2 27B. Apache-2.0. Bases are Qwen3.5-2B/4B/9B with the architecture unchanged, so KV is 12,288 (2B) or 32,768 B/token (4B, 9B) against 147,456 for the Qwen3-based Ternary-Bonsai 4B/8B in the catalog.
- **What changed since the spike.** Each repo now ships a plain `Q2_0` file that the cards say runs on upstream llama.cpp from release b11556, which added the Hadamard activation transform. The vendored tree contains Hadamard code in `src/llama-graph.cpp`. `PQ2_0` and `PTQ1_0` are still fork-only: upstream PR #29077 was closed unmerged and issue #29058 is open.
- **Files.** `Ternary-Bonsai-2-2B-Q2_0.gguf` 546,689,504 B (`c2893fa6…`); `…-4B-Q2_0.gguf` 1,212,103,520 B (`64a5183b…`); `…-9B-Q2_0.gguf` 2,556,640,128 B (`1f884aa5…`).
- **Thinking** is on by default; the cards give non-thinking sampling (temperature 0.7, top-p 0.8, top-k 20, presence penalty 1.5).
- **Quality: no numbers on any card.** Prism's site was not read.
- **Speed is the blocker to expect.** The spike measured `Q2_0` prompt processing 3.5× slower than Q4_K_M at the same parameter count on ARM flags. A 9B at that rate is far outside "not much slower than a 4B dense model".
- The HF API reports the 4B and 9B repos' GGUF architecture as `dflash`, presumably from the drafter file; the `Q2_0` headers were not read.

#### 5. PLaMo 3 Translate

- Four sizes built on PLaMo 3 NICT; the 31B was trained on about 37B tokens of mostly synthetic translation data and distilled into the smaller ones (PFN blog, 2026-11-05).
- First-party GGUF for llama.cpp, `plamo3` architecture (merged upstream 2025-12-28). The card recommends the Q4_0 file for smartphones because mobile backends have tuned kernels for it.
- 0.8B: 16 layers, 1 KV head, 8,192 B/token. 2B: 24 layers, 4 KV heads, 49,152 B/token.
- Prompt format PFML3 has explicit fields for context, glossary, target language and earlier source–translation pairs. Multiple texts are sent as separate requests; there is no keyed batch. Greedy decoding.
- Card table for the 2B Q4_K_M: PFTB (En↔Ja) 0.6062, WMT24++ COMET-22 En→Ja 0.8774. The comparison against TranslateGemma and Hy-MT2 is a figure; I could not extract its numbers.
- **Licence** (`LICENSE/en` in the GGUF repo, revised 2026-11-17). Commercial use is free only under ¥1 billion revenue and after registration. Anyone providing the model by download must give recipients the agreement and ensure they comply, show "Built with PLaMo", and keep "PLaMo" in derived model names. The lighter "end-user limited provision" route explicitly does not apply where the product's source code is public. Japanese law governs.
- An open llama.cpp issue (#31889) reports garbage output for PLaMo 3 Q4_0 on the Hexagon backend. It does not concern CPU.

#### 6. CAT-Translate 3.3B / 7B and the rikai fine-tune

- CyberAgent's card (all four sizes MIT) reports BLEU on five Japanese–English sets. JA→EN averages: 7B 41.06, 3.3B 37.51, 1.4B 33.26, 0.8B 29.71.
- The dialogue set (BSD, translated a conversation at a time) does not follow the average: 7B 33.75, **1.4B 31.28**, 0.8B 29.63, **3.3B 26.51**. The 3.3B also trails the 1.4B on the court set (42.44 vs 43.84) and leads on medical, finance and patents.
- 3.3B: base `sarashina2.2-3b`, 32 layers, 8 KV heads, head dim 160 (163,840 B/token), context 8,192. No first-party GGUF.
- 7B: Mistral-type config, 32 layers, 8 KV heads; `mradermacher` Q4_K_M 4,537,758,048 B.
- `rikai-jp-eng-Bench-265-1.4B` (MIT) fine-tunes the 1.4B. Its card reports COMET-22 0.8632 on a 26-domain set against 0.8513 for CAT-1.4B and 0.8616 for CAT-3.3B, but an LLM-judge score of 66.9 against 71.6 for the 3.3B.

#### 7. Aion 1.0 Instruct 2.3B

- Microsoft's Windows on-device model, published 2026-11-18 as unquantised weights with quantised versions promised. MIT.
- 28 layers, GQA 24/8, head dim 128, 21 sliding-window layers (window 2048) and 7 full; 32K context; custom modelling code.
- Languages: English, German, Spanish, Portuguese, Italian, French, Chinese, Japanese.
- Community GGUFs declare architecture `aion`, and the Xylolabs card says stock llama.cpp does not know it and ships a patch. No upstream issue or PR with "aion" in the title was found.

#### 8. LFM3-1.7B and the LFM licence

- 24 layers: 13 gated short convolutions, 5 sliding-window attention, 6 global attention. 1.73B parameters. Card claims at least 2.5× faster prefill and 1.8× faster decode than Qwen3.5-4B/9B, Gemma 4 E4B, MiniCPM5-2B and LFM2.5-2.6B as Q4_0 on three devices with GPU offload; no CPU-only phone number.
- All benchmarks on the card are agentic, coding or knowledge. Nothing on translation or multilingual quality.
- The LFM Open License v1.0 is Apache-shaped but conditions commercial rights on revenue under US$10 million. Redistribution is allowed with a copy of the licence. It is not "equally permissive".

### Architectures that change the size/speed trade-off

Only items with released weights.

| Idea | Model | Status for Yomu |
|---|---|---|
| **Translation fine-tune of an architecture already shipped** | Index-Translate-2B/9B | Free: same loader, same speed class. This is the practical win of the period. |
| **Ternary weights on a hybrid-attention base** | Ternary-Bonsai 2 | Loads (Q2_0). Memory win is real; CPU speed is the open question. |
| **Small MoE** | EuroMoE-2.6B-A0.6B (64 experts, 8 active, 24 layers; Apache-2.0) | GGUF declares `llama`; unverified at runtime. Decode cost of a 0.6B model with 2.6B of weights on disk. No quality evidence. |
| | Olala-v1.0-mini-7A1B (29 Mamba-3 + 7 attention layers, 256 experts, 6 active; only 7 layers keep KV) | Fork only; smallest file 5.99 GB. |
| | LFM2.5-8B-A1B (`lfm2moe`, 5.16 GB) | Loads; LFM licence. |
| **Multi-token-prediction heads** | Qwen3.5 MTP GGUFs (`unsloth/Qwen3.5-2B-MTP-GGUF`, Q4_K_M 1,329,851,808 B; 4B 2,834,975,040 B) | `--spec-type draft-mtp` is in mainline and in the vendored `common/arg.cpp`. unsloth claims about 1.5–2× generation speed. Prism's 2B card says a draft head was *slower* than plain decoding at that size. Interaction with a GBNF grammar is untested. |
| | Gemma 4 MTP drafters (`google/gemma-4-E2B-it-assistant`, 78M parameters, Apache-2.0) | Google claims up to 3× decode speed with identical output. `gemma4-assistant` is in llama.cpp; only third-party GGUFs found. |
| **Extreme low-bit of a translation model** | Hy-MT2-1.8B 1.25-bit (461,860,800 B) | Blocked on PR #22836. |
| **Looped / weight-shared** | HRM-Text-1B (Sapient; `hrm_text` in llama.cpp) | English, pre-alignment checkpoint. Not usable. Inkling 2's loop variant is already covered and too big. |
| **Not an LLM at all** | FumetoTranslate: ModernBERT-ja-310m encoder + 4-layer decoder, page-aware cross-attention, ONNX int8 448 MB | Author reports 15 ms a line in page mode on a desktop CPU and higher manga scores than the 2B LLM fine-tunes. No licence on the weights. Shows that a page-aware encoder–decoder is a serious alternative for the English-only path, on the ONNX Runtime Yomu already links. |

### What helps small models translate manga

Findings Yomu could apply, strongest evidence first. Where the evidence is from a large model or one developer, that is said.

1. **A page is the right unit, and more than a page hurts.** Lippmann et al. (arXiv 2411.02589; GPT-4 Turbo on OpenMantra) measured line-by-line ChrF 32.7 against page-by-page 36.0 for JA→EN. Adding three previous pages, all previous pages or the whole volume scored at or below the single page; the authors describe quality falling as input grows past one page. Yomu's one-page prompt is the configuration they recommend. *Large-model evidence.*
2. **The same holds at 2B, by a smaller margin.** fumetodev's 2B fine-tune scores 47.2 chrF++ in page mode against 46.7 per line; its encoder–decoder gains about 1 chrF++ from the page, and lines under 4 characters gain 2 to 4. Short interjections are where page context pays. *One developer, self-reported.*
3. **Most of the benefit of the page image is already captured by page text.** In Lippmann et al., the image added 2.9 ChrF when translating line by line but only 0.6 at page level (36.0 → 36.6), and almost nothing for Japanese→Polish. A text-only pipeline that sends the whole page gives up little.
4. **Long pages should be chunked to the range a model was trained on.** The manga fine-tune was trained on 2 to 8 lines per request and still lost or broke 7 of 505 pages. Yomu's grammar removes the parse failures but not the dropped or degraded lines; a per-line fallback and a chunk size per model are both worth keeping.
5. **Use each translation model's own prompt.** The Index fine-tune lost 2.4 chrF++ with the Hy-MT2 prompt and 0.4 with the base model's chat template. Index-Translate's trained instructions are Chinese; Hy-MT2 and PLaMo each have their own slots for glossary and context.
6. **Glossaries work as a strong hint when they list only what occurs in the line.** fumetodev trained with one to three pairs, only those whose Japanese side appears in the line, and measured 84% adherence (93% for the base Index-Translate-2B). BSC's WMT26 terminology paper (arXiv 2609.09999) found the mirror image on the training side: glossary examples teach only when they contradict what the model would have written, and filtering for those raised term accuracy from 78.7% to 89.9%. For Yomu: send few, relevant pairs, and check the output for the target term afterwards.
7. **Do not add few-shot examples to the smallest models by default.** A study of local models (arXiv 2607.26286; FLORES, English→European languages) found five-shot prompting helped Mistral 7B and Qwen2.5 14B but hurt Llama 3.2 3B, that retrieval-selected examples beat random ones only modestly, and that JSON multi-output prompts exposed structured-output failures in the smaller models. *Not Japanese; direction of effect only.*
8. **A turn-per-segment layout is an alternative to a keyed batch.** Source-primed multi-turn translation (arXiv 2503.10494) gives the model the whole source first, then translates segment by segment as chat turns, reusing the KV cache; it beat both whole-document single-turn and independent segments. PLaMo 3's history turns are designed the same way. This could serve models that cannot hold the id-keyed schema. *Model sizes in that paper were not checked.*
9. **Greedy decoding for translation-specialised models.** Index-Translate and PLaMo evaluate at temperature 0. On the manga fine-tune, the Hy-MT2 sampler cost 0.6 chrF++ against greedy, and the author reports invented content at higher temperatures.
10. **Fine-tuning for English destroys the other targets.** fumetodev's v2 went off-target 44.5% of the time across 21 languages against 8.6% for stock Hy-MT2, and the Index fine-tune answers in English whatever is asked. Any manga fine-tune belongs behind an English-only gate.
11. **Bigger is not reliably better on dialogue.** On CyberAgent's own JA→EN table, CAT-Translate-3.3B beats the 1.4B on three of five sets (medical, finance, patents) and loses the conversation set by 4.8 BLEU and the court set by 1.4. Judge candidates on dialogue, not on an average.
12. **Pre-decode the fixed prompt prefix.** A CPU subtitle-translation study (arXiv 2607.09957; 0.6B and 1.7B models) cut mean latency 31% by prefilling the known part of the prompt while waiting for the next subtitle. On Qwen3.5-type hybrids a KV rewind is not enough: fumetodev's card notes `llama_memory_seq_rm` refuses to cut into recurrent layers, and saves the sequence state after the prefix instead (about 20 MB), taking a line from about 500 ms to 230 ms. Yomu could decode the instruction while OCR is still running. *This touches the runtime; noted here because both sources are about model behaviour.*
13. **Small models do carry Japanese pragmatics when the context is in the prompt.** A probing study of TinySwallow-1.5B (arXiv 2608.14896) found honorific register cleanly represented and implicit-subject and in-group contrasts resolved during generation, with the preferred continuation flipping with the scenario on 77–79% of items.
14. **Evaluation.** A study of VLM judges for manga translation (arXiv 2610.17908) found that giving the judge the page image improved detection of injected, visually dependent errors, but humans showed no consistent preference between text-only and image-aware judgements. OpenMantra remains the only public JA→EN manga set with professional references that these sources use.
15. **Sound effects.** OnomatoBridge (arXiv 2609.21199) is an image-editing pipeline; it offers nothing for a text prompt.

### Checked and found nothing new at phone size

HF organisation listings, newest first, read on 2026-11-21:

- **Qwen**: nothing at or below 9B since Qwen3.5 (February 2026). Newer releases are Qwen3.8 27B and up, image models and a 4B driving model.
- **Google**: nothing since the Gemma 4 QAT files (June) and `embeddinggemma-2`. No new TranslateGemma.
- **Mistral**: Mistral Large 4, time-series and Arabic speech models. No new Ministral.
- **Hugging Face (SmolLM)**: nothing since SmolLM3 (July 2025).
- **Zhipu**: nothing small. **Apple**: depth and video models. **OpenAI**: nothing.
- **Tencent**: no Hy-MT newer than Hy-MT2. Hy-SimulMT is 30B.
- **Japanese labs**: SB Intuitions has no new Sarashina text model since 2.2 (the base of CAT-Translate). Rakuten's new model is 684B. shisa-ai's recent work is quantised large models. ELYZA's are 32B+. Stockmark's are document readers.
- **VNTL**: last release January 2025 (`vntl-llama3-8b-v2`, Llama 3 licence). No successor.
- **Sakura / GalTransl**: Japanese→Chinese.

### Could not verify (models)

- A Japanese→English score for Index-Translate at any size. The report (arXiv 2609.40181) was read only as an abstract.
- The Index-Translate 150-language inventory.
- That the official Index-Translate GGUFs load in the vendored llama.cpp (MTP block), and that Chinese instructions are required rather than merely trained.
- Any quality number for Ternary-Bonsai 2, and which architecture string its `Q2_0` files declare.
- That the EuroMoE GGUF runs in mainline llama.cpp.
- PLaMo 3 Translate's comparison numbers against Hy-MT2 and TranslateGemma (figure only), and its effective KV size under windowed attention.
- The provenance of fumetodev's training corpus, and every score on its cards.
- The benchmark behind Aion's "Translation chrF++ 43.18".
- LFM3-1.7B and EuroMoE KV sizes (not computed).
- WMT26 general-task findings for small open systems: not found.
- Whether MTP or drafter speculation helps under a GBNF grammar.

### Sources (models)

All accessed 2026-11-21.

**Index-Translate**
- https://huggingface.co/IndexTeam/Index-Translate-2B · https://huggingface.co/IndexTeam/Index-Translate-2B-GGUF
- https://huggingface.co/IndexTeam/Index-Translate-9B · https://huggingface.co/IndexTeam/Index-Translate-9B-GGUF
- https://huggingface.co/IndexTeam/Index-Nailong-2B
- https://github.com/bilibili/Index-Translate (README, `docs/prompts.md`, `inference/llm/translate.py`)
- https://arxiv.org/abs/2609.40181

**Manga fine-tunes and peer apps**
- https://huggingface.co/fumetodev/Index-Translate-2B-JP-Manga-Finetune-v1-GGUF
- https://huggingface.co/fumetodev/Hy-MT2-1.8B-JP-Manga-Finetune-v5-GGUF
- https://huggingface.co/fumetodev/Hy-MT2-1.8B-JP-Manga-Finetune-v3-multilingual-GGUF
- https://huggingface.co/fumetodev/fumetotranslate-manga-ja-translator
- https://github.com/fumetodev/FumetoReaderPlus
- https://github.com/mayocream/koharu
- https://huggingface.co/lmg-anon (VNTL listing)

**Tencent**
- https://huggingface.co/tencent/Hy-MT2-7B · https://huggingface.co/tencent/Hy-MT2-7B-GGUF
- https://huggingface.co/tencent/Hy-MT2-1.8B-2Bit-GGUF · https://huggingface.co/tencent/Hy-MT2-1.8B-1.25Bit-GGUF
- https://github.com/ggml-org/llama.cpp/pull/22836

**PrismML**
- https://huggingface.co/prism-ml/Ternary-Bonsai-2-2B-gguf · https://huggingface.co/prism-ml/Ternary-Bonsai-2-4B-gguf · https://huggingface.co/prism-ml/Ternary-Bonsai-2-9B-gguf
- https://github.com/ggml-org/llama.cpp/issues/29058 · https://github.com/ggml-org/llama.cpp/pull/29077
- `docs/research/ternary-bonsai-spike.md` (this repository)

**Preferred Networks**
- https://huggingface.co/pfnet/plamo-3-translate-2b · https://huggingface.co/pfnet/plamo-3-translate-2b-GGUF (card and `LICENSE/en`)
- https://huggingface.co/pfnet/plamo-3-translate-0.8b-GGUF · https://huggingface.co/pfnet/plamo-3-translate-8b-GGUF
- https://www.preferred.jp/en/blog/tech/plamo-3-translate-weight-release
- https://github.com/ggml-org/llama.cpp/pull/17304 · https://github.com/ggml-org/llama.cpp/issues/31889

**CyberAgent and derivatives**
- https://huggingface.co/cyberagent/CAT-Translate-3.3b · https://huggingface.co/cyberagent/CAT-Translate-7b
- https://huggingface.co/mradermacher/CAT-Translate-7b-GGUF · https://huggingface.co/Sundowner123/cat-translate-3.3b-gguf
- https://huggingface.co/rikai-translate/rikai-jp-eng-Bench-265-1.4B

**Liquid AI**
- https://huggingface.co/LiquidAI/LFM3-1.7B (card and `LICENSE`) · https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF
- https://huggingface.co/LiquidAI/LFM2-350M-ENJP-MT · https://huggingface.co/LiquidAI/LFM2-350M-ENJP-MT-GGUF
- https://huggingface.co/LiquidAI/LFM2.5-8B-A1B-GGUF · https://huggingface.co/LiquidAI/LFM2.5-2.6B-GGUF · https://huggingface.co/LiquidAI/LFM2.5-1.2B-JP-202606-GGUF
- https://github.com/ggml-org/llama.cpp/pull/31252

**Other general models**
- https://huggingface.co/microsoft/Aion-1.0-Instruct-2.3B · https://huggingface.co/Xylolabs/Aion-1.0-Instruct-2.3B-GGUF
- https://huggingface.co/microsoft/FrogNano-4B-2609
- https://huggingface.co/OVHaiLLM/Olala-v1.0-mini-7A1B · https://huggingface.co/OVHaiLLM/Olala-v1.0-mini-7A1B-GGUF · https://huggingface.co/OVHaiLLM/Olala-v1.0-translate
- https://huggingface.co/utter-project/EuroMoE-2.6B-A0.6B-Instruct-2512 · https://huggingface.co/mradermacher/EuroMoE-2.6B-A0.6B-Instruct-2512-GGUF
- https://huggingface.co/utter-project/EuroLLM-9B-Instruct-2512
- https://huggingface.co/ibm-granite/granite-4.2-3b · https://huggingface.co/ibm-granite/granite-4.2-3b-GGUF · https://huggingface.co/ibm-granite/granite-swash-3b-a600m
- https://huggingface.co/nvidia/NVIDIA-Nemotron-3.5-Bolt-E4B-4GB · https://huggingface.co/nvidia/NVIDIA-Nemotron-Labs-Dory1.0-4B-BF16
- https://huggingface.co/openbmb/MiniCPM5-2B · https://huggingface.co/Caldalis/MiniTranslator-2B
- https://huggingface.co/NiuTrans/LMT-60-4B · https://arxiv.org/abs/2511.07003
- https://huggingface.co/google/gemma-4-E2B-it-assistant · https://huggingface.co/unsloth/Qwen3.5-2B-MTP-GGUF
- https://huggingface.co/sapientinc/HRM-Text-1B · https://huggingface.co/Aleph-Alpha/Kolibri-1
- https://huggingface.co/HuggingFaceTB/SmolLM3-3B · https://huggingface.co/Unbabel/Tower-Plus-2B · https://huggingface.co/CohereLabs/North-Small-Translate-1.0 · https://huggingface.co/yandex/Yandex-8B-WMT26
- HF API organisation listings: Qwen, google, mistralai, microsoft, ibm-granite, LiquidAI, HuggingFaceTB, tencent, nvidia, apple, zai-org, cyberagent, pfnet, sbintuitions, Rakuten, shisa-ai, elyza, stockmark, openai, prism-ml (`https://huggingface.co/api/models?author=<org>&sort=createdAt`)
- https://github.com/ggml-org/llama.cpp/tree/master/src/models

**Papers**
- Lippmann et al., Context-Informed Machine Translation of Manga using Multimodal LLMs — https://arxiv.org/abs/2411.02589
- Hinami et al., Towards Fully Automated Manga Translation (OpenMantra) — https://arxiv.org/abs/2012.14271
- Text-Only vs. Image-Aware VLM Judges for Manga Translation Evaluation — https://arxiv.org/abs/2610.17908
- OnomatoBridge — https://arxiv.org/abs/2609.21199
- Evaluating Prompt Scope and Demonstration Similarity in Local LLM Machine Translation — https://arxiv.org/abs/2607.26286
- Context-Aware Subtitle Translation on CPU — https://arxiv.org/abs/2607.09957
- Source-primed Multi-turn Conversation Helps LLMs Translate Documents — https://arxiv.org/abs/2503.10494
- SalamandraTA at WMT 2026 Terminology Shared Task — https://arxiv.org/abs/2609.09999
- Interpretable Cross-Lingual Alignment in Small Language Models (J-PragEval) — https://arxiv.org/abs/2608.14896

---

## Part C: Vision-side research for Yomu: detection, OCR, page structure, drawing back

Desk research, 2026-11-21. Nothing was run: no model, no build, no phone. Every number below is quoted from the source named beside it. "Self-reported" means the model's own author measured it. Sizes and licences come from the Hugging Face API or the repo's own files, read today.

**Three facts that change the picture, before the ranking.**

1. **Manga OCR moved a lot between June and November 2026.** A public benchmark of recent manga, [JMangaBench_Read](https://github.com/muscgab/JMangaBench_Read) (7,789 boxes, pages first posted 2025-10 to 2026-10), now ranks a dozen crop-level readers. The original manga-ocr is last of the ranked models. Koharu has dropped manga-ocr and made Hayai OCR its default ([koharu.rs/models/vision](https://koharu.rs/models/vision)).
2. **Yomu's OCR is not the model those benchmarks call "mangaOCR".** Yomu ships `jzhang533/manga-ocr-base-2025`: a DeiT-tiny encoder (hidden 192, 12 layers) and a 2-layer BERT decoder ([config.json](https://huggingface.co/jzhang533/manga-ocr-base-2025/blob/main/config.json)), 22 MB + 118 MB as ONNX. The original is 111M parameters. The fork's author reports validation CER 0.1017 against 0.1056 for the original (self-reported, [fork README](https://github.com/jzhang533/manga-ocr)). No public benchmark includes the 2025 model, so where it sits is unknown.
3. **The vision tower of the catalog VLMs is not on the phone.** The GGUF files Yomu downloads hold the text model only. The vision encoder is a separate `mmproj` file of 0.2 to 1.0 GB per model (table in [§8](#8-the-vision-tower-of-the-catalog-vlms-skip-as-ocr-watch-as-context)).

### Ranked table (vision)

| # | Candidate | What it is | Expected payoff for Yomu | Cost / risk | Evidence | Verdict |
|---|---|---|---|---|---|---|
| 1 | Hayai OCR v2.7 Supernova and Baberu OCR v1.2 | Two Apache-2.0 crop-level manga readers with ONNX exports, 157M and 115M parameters | Fewer wrong reads on handwriting, asides, narration and sound effects; fewer crops skipped as unsure | 122 to 226 MB against 140 MB today; far larger vision encoders than Yomu's (Baberu's is 86M parameters against about 5.6M), so slower per crop on a phone CPU (not measured anywhere) | Strong against the original manga-ocr (third-party benchmark); none against Yomu's 2025 model; none on phones | **Spike** (desktop first) |
| 2 | Use the detector's `bubble` class, shape text to the bubble, hyphenate | No new model: the `bubble` boxes Yomu already computes and drops, plus Android's own line breaker | Long text in narrow bubbles; text boxes grouped per balloon; asides ordered after their parent | Kotlin work in the typesetter and assembler only | Yomu's own #428 and ADR-0020 data; one comparable app ships the same idea | **Spike** |
| 3 | int8 OCR decoder, ONNX Runtime 1.32, thread count | Quantise the 118 MB decoder; bump from 1.26.0; set threads | OCR download about 140 MB to roughly 50 MB (my estimate from a 4× smaller decoder); some speed | One desktop parity check and one emulator timing ratio | Indirect: other manga OCR projects ship int8 decoders and report little loss (self-reported) | **Spike** (cheap) |
| 4 | PP-OCRv6 tiny/small text-line detector | 1.8 MB and 9.9 MB Apache-2.0 ONNX line detectors | Column positions inside a block: the missing step for ruby and line breaks in #428/#438 | One more small model and a pass per page; grouping rules to write | Weak on manga (official numbers are not manga; one app uses the medium tier on manga) | **Spike** (small, desktop) |
| 5 | Panel detector for reading order | `forkger/manga-panel-rfdetr`, RF-DETR Medium, Apache-2.0, 121 MB fp32 ONNX | Correct order on borderless or dark-gutter pages where the paper-gutter split fails | 121 MB, phone CPU time unknown, no accuracy on its card | Thin | **Watch**; first count how often order is wrong today |
| 6 | Balloon instance masks | `fumetodev/rtmdet-manga-layout-onnx` (43 MB), Koharu Layout HighRes (186 MB) | True balloon outline for screentone and conjoined balloons | A second detector pass; the 43 MB model has no card | Thin for the small one; good but desktop-class for Koharu's | **Watch** (do #2 first) |
| 7 | CharSpot and NAR readers (muscgab) | 1.7M to 54M parameter readers; CharSpot returns per-character boxes, a furigana flag and a learned order | Would solve ruby, line structure and speed in one 7 MB model | Weights are CC BY-NC-SA 4.0 | The authors' own numbers on their own benchmark (conflict disclosed; public crops, labels and scorer) | **Watch**: blocked by the licence rule |
| 8 | Vision tower of Qwen3.5 / Gemma 4 / Ministral 3 via `mtmd` | Give the LLM the page or crops | Speaker, gender and scene cues; in theory OCR and translation in one step | 0.2 to 1.0 GB extra download per model, a new native library, hundreds to thousands of image tokens per page | Benefit shown only for GPT-4 Turbo and small at page level; small local VLMs fail manga OCR | **Skip** as OCR; **watch** as context |
| 9 | Neural inpainting (AOT, MI-GAN, LaMa) | Erase source glyphs before drawing | Cleaner text over artwork | 16 to 207 MB, seconds per page, bitmap patches in the overlay | Feasible on Android (one app); no phone timings | **Skip** for now |
| 10 | PaddleOCR-VL family and document OCR VLMs | 0.26B to 3B page-OCR VLMs, several runnable in llama.cpp | None over #1 | 0.5 to 6.8 GB, seconds per crop on CPU | PaddleOCR-VL is benchmarked: slower than #1 and below Hayai; the document models publish no manga result | **Skip** |
| 11 | A newer detector | Anything past ogkalu RT-DETR v4-s | None found at phone size | — | ogkalu repo unchanged since the revision Yomu pins | **Skip** |
| 12 | NNAPI, QNN, XNNPACK providers | Hardware paths inside ONNX Runtime | No dependable cross-vendor gain | NNAPI deprecated; QNN is Qualcomm-only; XNNPACK covers convolutions, not transformers | Official docs | **Skip** (XNNPACK on the detector is an optional one-line test) |
| 13 | Magi v3 | Florence-2 model for panels, tails, speakers, OCR | Speaker links | 1.67 GB, non-commercial terms | Paper | **Skip** |

### 1. OCR: Hayai v2.7 and Baberu v1.2 (spike)

**What the benchmark says.** JMangaBench_Read v1.3.3 scores crops of speech, narration and asides; sound effects are a separate set. Its authors (muscgab) also publish competing models and say so in a conflict-of-interest note, so for the rows below they are a third party. Composite is 1 − normalised edit distance, averaged equally over printed and non-printed boxes.

| Model | Params | Licence | Composite | CER % | Exact % | Source of the row |
|---|---|---|---|---|---|---|
| Hayai v2.7 Supernova p256 | 158M | Apache-2.0 | 98.72 | 1.80 | 95.17 | benchmark leaderboard |
| manga-ocr-charspot-small | 11M | CC BY-NC-SA 4.0 | 97.17 | 1.37 | 92.37 | leaderboard (authors' own model) |
| PaddleOCR-VL-1.6 manga LoRA v6 | 959M | Apache-2.0 | 96.14 | 2.02 | 89.33 | leaderboard |
| manga-ocr-charspot-nano | 1.7M | CC BY-NC-SA 4.0 | 95.75 | 2.14 | 89.38 | leaderboard (authors' own model) |
| PaddleOCR-VL-For-Manga | 959M | Apache-2.0 | 95.05 | 1.99 | 87.91 | leaderboard |
| Baberu OCR v1.2 | 115M | Apache-2.0 | 94.94 | 3.04 | 84.9 | self-reported with the benchmark's scorer ([card](https://huggingface.co/genshiai-daichi/baberu-ocr)) |
| Baberu OCR v1.1 | 113M | Apache-2.0 | 93.38 | 3.62 | 81.90 | leaderboard |
| mangaOCR (kha-white original) | 111M | Apache-2.0 | 90.27 | 4.81 | 78.52 | leaderboard |

- **The gap is in non-printed text.** The original manga-ocr reads 88.3% of printed boxes exactly and 48.3% of non-printed ones ([charspot-nano card](https://huggingface.co/muscgab/manga-ocr-charspot-nano), same benchmark). Hayai v2.7 reports 97.1% and 91.4% (self-reported, [card](https://huggingface.co/JustANormalTinkerer/hayai-ocr-v2.7-supernova)). Baberu v1.2 reports 90.7% printed, 67.3% handwritten and 74.2% on sound effects (self-reported).
- **This matches Yomu's own pain.** #350 found speech read almost perfectly while only 1 of 7 non-speech bubbles arrived intact, and reads under 0.85 confidence are skipped.
- **Vertical text.** 7,158 of the 7,789 benchmark boxes are vertical; Hayai's card reports 96.2% exact on them and 90.4% on the 439 horizontal ones (self-reported).
- **Furigana.** Neither card says how ruby is handled. Baberu's Japanese labels were distilled from manga-ocr, which drops ruby. Unverified for both.

**Files.**

| | Hayai v2.7 | Baberu v1.2 | Yomu today |
|---|---|---|---|
| Design | SigLIP2 NaFlex encoder, 12-layer decoder, 128 to 384 patches per crop | DINOv2 encoder (86M), 6-layer decoder, 224×224, character vocabulary | DeiT-tiny encoder (about 5.6M), 2-layer BERT decoder, 224×224 |
| Smallest ONNX set | `hayai_prefill_int4.onnx` 138 MB + `hayai_decode_int8.onnx` 88 MB = 226 MB ([repo](https://huggingface.co/JustANormalTinkerer/hayai-ocr-v2.7-supernova-onnx)) | `vision_int4.onnx` 52 MB + two int8 decoder files 70 MB = 122 MB | 22 MB + 118 MB = 140 MB |
| ONNX notes | Repo created 2026-11-17, no card or licence tag yet; export and inference scripts included | Card documents the KV-cache loop and decode settings; needs only onnxruntime | — |
| Output cap | 128 new tokens in the examples | 256 characters; garbles past about 190 | — |

**Cost.** No source reports phone timings for either. On a Xeon Gold 6148 with 8 threads, in PyTorch fp32, the muscgab card measures Baberu v1.1 at 197 ms per crop and Hayai v2.5 at 294 ms, against 7 to 75 ms for their own small models; the original manga-ocr is not in that table. Yomu's emulator OCR stage takes 0.15 to 0.2 s per read today (`docs/research/jev-bubble-class-hints.md:385`) with an encoder a fifteenth the size of Baberu's; Hayai's card does not break out its encoder size. Expect several times slower per crop; the spike must measure it.

**Risks.**
- **Training data.** Hayai trains on AnimeText-derived crops and synthetic data; Baberu on a privately collected crop corpus it does not publish, plus Manga109-s. This is the same class of provenance risk ADR-0020 already accepted for the detector.
- **Release age.** Hayai v2.7 is eight days old and its ONNX export four.

**Suggested spike (desktop, the #453 replica).** Read the same crops with the pinned 2025 model, Baberu v1.2 (122 MB set) and Hayai v2.7 (226 MB set): OpenMantra's boxes plus the fixture pages, looking hardest at the crops Yomu skips as unsure and at `text_free` boxes. Time each on CPU as a ratio. Two shapes to compare: replace the reader, or keep the fast reader and re-read only crops under the 0.85 gate with the stronger one, which bounds the extra time to a few crops a page at the price of a second download. Running the pinned 2025 ONNX over JMangaBench_Read's crops once would also put Yomu's reader on the public scale (the dataset is CC BY-NC-SA 4.0, evaluation use).

### 2. Bubble boxes, shaped text and hyphenation (spike, no model)

- **Yomu already has bubble boxes and throws them away.** ADR-0020 keeps `text_bubble` and `text_free` and drops `bubble`, noting "Whether `bubble` boxes should shape render boxes is left open." #428 showed those boxes separate satellite bubbles and find asides.
- **What they would buy.**
  - **Grouping:** every text box inside one `bubble` box is one utterance, and a `text_free` box touching a bubble follows its parent in the prompt (the order fix #428 asked for).
  - **Render box:** the bubble box is a second opinion to the white flood fill in `BubbleRenderBox.kt`, which gives up on screentone, open or art-backed bubbles.
- **Shaping.** Yomu typesets into a rectangle 0.707 of the interior. Android's `StaticLayout.Builder` takes per-line left and right indents (`setIndents`) and automatic hyphenation (`setHyphenationFrequency`, default none), both since API 23 ([reference](https://developer.android.com/reference/android/text/StaticLayout.Builder)). Lines can therefore follow an ellipse or the flood-fill outline, and long words can break, with no library. The typesetter has no hyphenation today (no match for "hyphen" under `pipeline/` or `app/src/main`).
- **Someone ships this.** Fumeto Reader Plus (GPL-3.0, Android, released 2026-09) letters to the balloon contour, splits conjoined balloons at their necks, hyphenates English with TeX patterns, and draws a translucent patch for unenclosed text ([README](https://github.com/fumetodev/FumetoReaderPlus), self-described; 4 stars, so little outside scrutiny). Its notes say that treating each balloon as a single utterance repaired 31 same-speaker balloons on one reader's device that gap-based rules had cut in two (self-reported, `CLAUDE.md`).
- **Fill colour.** Koharu fills flat and smoothly shaded backgrounds without any model and uses its inpainter only over line art and screentone ([vision docs](https://koharu.rs/models/vision)). Sampling the bubble's own interior colour instead of fixed `0xF0FFFFFF` is the phone-sized version of that.

### 3. int8 OCR decoder, ONNX Runtime bump, threads (cheap spike)

- **The decoder is 84% of the OCR download** (118 MB of 140 MB) and is stored fp32.
- **Others quantise it with little loss.**
  - Baberu's sweep found an int8 decoder costs almost nothing, and that int4 weight-only on the vision side beat int8-dynamic on both size and accuracy (self-reported; composite 94.94 in PyTorch, 94.74 for the smallest ONNX set).
  - `muscgab/manga-ocr-nar-small-distil` is trained with int8 in the loop and ships as a 31 MB int8 ONNX scoring EM 88.9 on CPU (benchmark README). That shows int8 transformer OCR works when trained for it; it is not a test of quantising after training.
  - `onnx-community/manga-ocr-base-ONNX` ships the original decoder at 117 MB fp32 and 30 MB int8, with no accuracy figure.
- **fp16 is the risky one on Android.** Fumeto's notes say a full fp16 graph failed to load in Android's ONNX Runtime because GELU was fused into an fp16 kernel the CPU provider lacks; they store fp16 weights behind `Cast` nodes and compute in fp32 (self-reported).
- **Runtime version.** Yomu pins `onnxruntime-android:1.26.0`; Maven's latest is 1.32.0 (2026-11-13). Arm's KleidiAI kernels entered ONNX Runtime's CPU math library (MLAS) in 1.22 ([ORT blog](https://onnxruntime.ai/blogs/arm-microsoft-kleidiai)); neither that post nor the docs say whether the stock Android package enables them; the 1.31.0 notes list more Arm64 FP16 and quantised-kernel work (via a release-notes mirror, see "Not verified"). Whether any of it speeds Yomu's two graphs is unmeasured.
- **Threads.** `OnnxRuntime.loadModel` uses default `SessionOptions`. Pinning intra-op threads to the big-core count is an untested idea worth one timing run; I found no source measuring it for ONNX Runtime on Android.

### 4. PP-OCRv6 line detector for columns and ruby (small spike)

- **What exists.** PP-OCRv6 (PaddleOCR, 2026-06, Apache-2.0) has three tiers with official ONNX exports: tiny det 1.8 MB, small det 9.9 MB (2.48M parameters), tiny rec 4.5 MB, small rec 21 MB ([tiny det](https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_det_onnx), [small det](https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx)). Its own Japanese recognition score rises from 75.8 (PP-OCRv5 mobile) to 88.2 (v6 small) on Baidu's in-house set, which is not manga (self-reported).
- **Why it matters here.** #428 concluded that reading ruby is solved and finding it is not (37% precision from projection profiles), and that line breaks are worth sending "if column positions ever come cheaply, for example from a detector that outputs lines". A line detector is built to output that: one polygon per column. Whether the tiny and small tiers split a ruby column from its base column on manga is the thing the spike tests.
- **Evidence on manga is thin.**
  - Fumeto runs PP-OCRv6 medium det (62 MB) on manga pages and groups its lines with hand-written rules, including a furigana rule keyed on a line being several times thinner than its neighbour, shorter than it, and touching it on the reading side, replayed on 3,048 pages (self-reported).
  - `ogkalu/ppocr-v6-onnx` holds a 1.8 MB `manga_det_v0.2.onnx` with no card (unverified what it is).
- **A CTC line reader is the other half.** `fumetodev/PP-OCRv6_small_rec_manga_ONNX` (21 MB, 5.3M parameters, Apache-2.0) reports 75.1% of 422 held-out bubbles read exactly against 63.3% for the stock model, and its card says the model does not suppress furigana, so ruby lines must be dropped before it or read on purpose (self-reported, 0 downloads). It is a candidate ruby reader, not a replacement for the block reader.
- **Spike.** Run tiny and small det on #428's 20 ruby boxes and its 40 multi-column sample; count ruby columns found and false ones.

### 5. Panel detection and reading order (watch)

- **Today.** `ContextAssembler` splits at paper-white gutters and orders right to left, top to bottom. It cannot see borderless panels or dark gutters. I found no count of how often that goes wrong; get one before adding a model.
- **Candidates.**
  - **`forkger/manga-panel-rfdetr`**: RF-DETR Medium at 576×576, classes panel, text, body; Apache-2.0; 121 MB fp32 ONNX; trained on Manga109-s; used by the KMReader iOS app for panel-by-panel reading. The card gives no accuracy figure.
  - **Koharu Layout HighRes**: RF-DETR Seg 2XL, Apache-2.0, 186 MB, classes text, onomatopoeia, bubble, panel with masks; bubble and panel box AP 0.92 to 0.95 on its own Manga109 split (self-reported). Desktop-class.
  - **YOLO26 panel models** (`leoxs22/manga-panel-detector-yolo26n` and re-uploads): AGPL-3.0 base, whatever the re-upload's label says.
  - **Magi v3**: detects panels, characters, text and bubble tails and links speakers; 1.67 GB; its card allows personal, research, non-commercial and not-for-profit use only and asks anyone else to arrange a licence ([card](https://huggingface.co/ragavsachdeva/magiv3)).
- **Non-neural.** I found nothing newer for comics. The 2026 graph-based reading-order paper ([arXiv 2607.01018](https://arxiv.org/abs/2607.01018)) targets manuscripts and scores transitions with a language model. Fumeto's ordering rules (balloon first, rows of columns, right-to-left columns) are the most detailed public heuristics.
- **Speaker association.** No small 2026 model found. #350 already showed perfect speaker hints did not improve Qwen2.5-1.5B.

### 6. Balloon masks (watch)

- **`fumetodev/rtmdet-manga-layout-onnx`**: Apache-2.0, 43 MB, emits balloon instance masks. Its card is one line; classes, training data and accuracy are not published. The app's notes say the raw export returned 100 full-size masks (400 MiB a page) through ONNX Runtime's Java API and had to be re-exported to emit only balloons scoring at least 0.35.
- **Segmentation YOLOs** on Manga109 (`huyvux3005`, `mayocream`, `ShadowB`) rest on Ultralytics AGPL bases or carry CC BY-NC.
- **Verdict.** A mask model is the answer for screentone and conjoined balloons only after #2 shows where the flood fill and the bubble box fall short.

### 7. CharSpot and NAR readers (watch, licence)

- **What they are.** `manga-ocr-charspot-nano` is 1.7M parameters and 6.9 MB as ONNX; it detects each character, classifies it and orders the characters with a small learned head, with no decoder. It returns a box and score per character and has a furigana flag head. On a Xeon it reads a crop in 7 ms; composite 95.75 against 90.27 for the original manga-ocr. The 11M `charspot-small` scores 97.17 at 19.5 ms. Weak spots: horizontal text (65.8% exact) and handwriting (70.4%) ([card](https://huggingface.co/muscgab/manga-ocr-charspot-nano), authors' own numbers on their own benchmark).
- **Why it would matter.** Per-character boxes give column breaks, ruby and a per-character confidence in one small pass.
- **Blocker.** "Code: Apache-2.0. Weights: CC BY-NC-SA 4.0 (trained on AnimeText-OCR)." That fails Yomu's permissive-weights rule. Koharu ships one muscgab model with the non-commercial label shown. Asking the author for other terms, or retraining from the Apache-2.0 code on other data, is an owner decision, not a spike.

### 8. The vision tower of the catalog VLMs (skip as OCR, watch as context)

**Does llama.cpp support them?** Yes upstream. `docs/multimodal.md` lists Gemma 4 E2B/E4B; the converter maps `Qwen3_5ForConditionalGeneration` to the `qwen3vl` projector and has a vision path for `Mistral3ForConditionalGeneration` (`conversion/__init__.py`, read at llama.cpp `29b52d3`, 2026-11-20), and Mistral publishes the Ministral 3 mmproj GGUF in its own repo. I did not run any of them. The projector sources (`qwen3vl.cpp`, `gemma4v.cpp`, `pixtral.cpp`) are already present at Yomu's pin `7fe450e`. Yomu does not build `mtmd` (`LLAMA_BUILD_TOOLS OFF`).

**What it costs.**

| Catalog model | Text GGUF Yomu downloads | Extra `mmproj` file |
|---|---|---|
| Qwen3.5 0.8B | 0.53 GB | 205 MB (F16) |
| Qwen3.5 2B | 1.28 GB | 668 MB (F16) |
| Qwen3.5 4B | 2.74 GB | 672 MB (F16) |
| Qwen3.5 9B | 5.68 GB | 918 MB (F16) |
| Gemma 4 E2B / E4B | 3.35 / 5.15 GB | 987 / 992 MB (Google's file); 557 / 560 MB (ggml-org Q8_0) |
| Ministral 3 3B / 8B | 2.15 / 5.20 GB | 842 / 858 MB (BF16) |

- **Image tokens.** Qwen3.5 uses 16-px patches merged 2×2, one token per 32×32 px ([preprocessor config](https://huggingface.co/Qwen/Qwen3.5-2B/blob/main/preprocessor_config.json)); llama.cpp allows 8 to 4,096 per image. A 1280×2856 screenshot at full size is about 3,560 tokens. Gemma 4 has fixed budgets of 70, 140, 280, 560 or 1,120 tokens ([model card](https://huggingface.co/google/gemma-4-E2B-it)).
- **Phone evidence is thin and bad.**
  - On a Nothing Phone 2 (Snapdragon 8+ Gen 1), Qwen3.5-2B Q4_K_M processes prompts at 56 tokens/s on CPU, and Qwen3-VL-2B's vision encoder asked for 13.6 GB on a 1080×2412 screenshot and rebooted the phone ([np2-llm-bench](https://github.com/xiziphus/np2-llm-bench), one hobbyist's report, 2026-08). My arithmetic from that figure: a page shrunk to 768 tokens adds about 14 s of prompt processing before the encoder's own time.
  - On a Snapdragon 8 Elite, a 0.8B Qwen3.5-based OCR model took 30 to 81 s end to end for one 1024-px page on CPU ([llama.cpp #27649](https://github.com/ggml-org/llama.cpp/issues/27649), closed unresolved).
  - I found no measurement for Gemma 4 or Ministral 3 vision on a phone.

**Does the picture help translation?** The one controlled study used GPT-4 Turbo only ([Lippmann et al., COLING 2025](https://arxiv.org/abs/2411.02589)). Adding the page image to page-level text moved Japanese to English ChrF from 36.0 to 36.6 and xCOMET from 0.758 to 0.776; Japanese to Polish did not move (25.6 to 25.6 ChrF). The larger gain was at line level, where Yomu does not operate. The authors tested no open or small model. The 2026 follow-up I found studies judges, not translators ([arXiv 2610.17908](https://arxiv.org/abs/2610.17908)).

**Can a small VLM do OCR and translation in one step?** The evidence says no.
- A May 2026 local test on manga spreads found Gemma 4 E4B inventing or describing text rather than reading it, and Qwen3-VL 4B, 8B and 30B returning nothing ([nullmirror](https://nullmirror.com/en/blog/2026-05-24-local-vision-language-ocr-benchmark/), one author, Ollama on a Mac).
- Multimodal LLMs read vertical Japanese worse than horizontal ([arXiv 2511.15059](https://arxiv.org/abs/2511.15059)).
- On JMangaBench a 0.9B manga-tuned VLM (PaddleOCR-VL-For-Manga) scores below a 1.7M-parameter dedicated reader on composite and exact match (95.05 against 95.75; 87.9% against 89.4%) and takes 1,871 ms per crop on CPU against 7 ms.

**What would reopen it:** a published result that a model of 4B or less translates manga pages better with an image of 280 tokens or fewer than with text alone.

### 9. Inpainting (skip for now)

| Model | Size | Licence | Notes |
|---|---|---|---|
| AOT (manga-image-translator's) | 23 MB ONNX fp32 | MIT on the Hugging Face copies; the origin repo is GPL-3.0, so the chain is unclear | Runs on Android in `Yuu18id/manga-image-translator-android` via ONNX Runtime; no timings published |
| MI-GAN 512 | 16 MB (LiteRT fp16); ONNX pipeline also published | MIT, code and weights | Built for phones (ICCV 2023); trained on Places2, not manga. On-device times of 1.1 to 1.9 s on 2016–17 iPhones are from the paper's supplement as summarised by search (unverified by me) |
| LaMa / lama-manga | 206 to 207 MB ONNX | Apache-2.0 / MIT | Koharu's default; large for a phone |

- **Why skip.** Yomu draws over another app's live page, and inside white balloons a fill is already right. Inpainting only pays for text over artwork. The Android app closest to Yomu chose a translucent patch for that case rather than a model. It would add a download, per-page seconds and bitmap patches to the overlay.
- **Font and style matching.** `ogkalu/yuzumarker-font-detection-onnx` (MIT) exists; I did not look further, since Yomu's typeset bubble "answers to readability" by its own glossary.

### 10. Other OCR models checked (skip)

- **PaddleOCR-VL-For-Manga** (0.9B, 1.9 GB, Apache-2.0): 70% full-sentence accuracy on its Manga109-s split against 27% for the base (self-reported). Runs in llama.cpp. 1,871 ms per crop on a Xeon. Beaten by #1 on accuracy.
- **PaddleOCR-VL-1.6 manga LoRA v6**: composite 96.14, same size class, speed not measured.
- **manga-ocr-mobile** (bluolightning, about 10M parameters): about 7.4% CER and 73% exact on a Manga109-s split (self-reported). `ogkalu/manga-ocr-mobile` now holds ONNX files (17 + 25 + 23 MB) with no card, which would remove the LiteRT objection if they are what they appear to be (unverified). Yomu's current reader is already small, so this trades accuracy Yomu has not measured for speed it has not asked for.
- **Document OCR VLMs**: GLM-OCR (MIT, 2.7 GB), DeepSeek-OCR and OCR-2 (MIT / Apache-2.0, 6.7 and 6.8 GB), dots.ocr (MIT, 6.1 GB), LightOnOCR-2-1B (Apache-2.0, 2.0 GB), Granite-Docling 258M (Apache-2.0, 0.5 GB), MiniCPM-OCR-1.0 (Apache-2.0, 2.6 GB), HunyuanOCR (Tencent community licence). llama.cpp runs most of them. None publishes a manga result, and they are page parsers for documents.
- **MangaLMM**: Qwen2.5-VL 7B fine-tune, 16.6 GB, MIT. Too large.
- **kha-white/manga-ocr**: repo last pushed 2026-07-19; no new weights.

### 11. Detection (nothing better)

- `ogkalu/comic-text-and-bubble-detector` is at the same revision Yomu pins (`16e8a62`, last modified 2026-05-15). The 2026 uploads under other names that I opened (`NorwayFish/manga-bubble-detector`, `comicbox/comicapp-bubble-detector`) are copies of it.
- The new detectors are either desktop-class (Koharu Layout HighRes, which adds an `onomatopoeia` class) or YOLO-based with AGPL bases.
- **Sound effects.** Fumeto downloads a 17.5 MB crop classifier (MobileNetV4, Apache-2.0 per its notices, no card) to filter unenclosed sound effects. It is the only phone-sized SFX model I found and it is undocumented. Watch.

### 12. ONNX Runtime execution providers (stay on CPU)

- **NNAPI:** Google deprecated it in Android 15 ([Android NDK guide](https://developer.android.com/ndk/guides/neuralnetworks/migration-guide)). ONNX Runtime's NNAPI page does not mention it. Do not adopt.
- **QNN:** Qualcomm only; the NPU backend takes quantised models only and no dynamic shapes ([docs](https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html)), which rules out the OCR decoder loop as exported. The Android QNN artifact on Maven stops at 1.29.0.
- **XNNPACK:** in the stock Android package, registered per session. It covers Conv, pooling, 2D MatMul/Gemm, Resize, Softmax and QLinearConv ([docs](https://onnxruntime.ai/docs/execution-providers/Xnnpack-ExecutionProvider.html)): the detector's ResNet backbone, little of a ViT or decoder. Fumeto uses it for PP-OCR and hit a wrong-output bug with it on another graph (self-reported).
- **Conclusion.** No provider gives a dependable speed-up across vendors for Yomu's graphs. The gains available without a new runtime are in §3.

### 13. What comparable projects adopted in 2026 that Yomu lacks

| Project | Adopted | Relevant to Yomu |
|---|---|---|
| Koharu (Apache-2.0, desktop, 6.8k stars) | One RF-DETR layout model for panel, bubble, text, SFX with masks; Hayai OCR as default; fill flat backgrounds without a model | #1, §5, §2 |
| Fumeto Reader Plus (GPL-3.0, Android) | PP-OCRv6 line det + manga CTC rec; RTMDet balloon masks; SFX classifier; contour lettering; hyphenation | #2, #4, §6 |
| manga-image-translator-android (GPL-3.0) | comic-text-detector, 48px CTC OCR, AOT inpainting on ONNX Runtime Mobile | §9 |
| KMReader (iOS), yomihon (Android) | Panel-by-panel reading; KMReader uses forkger's detector, yomihon's method not checked | §5 |
| comic-translate (ogkalu) | README still lists manga-ocr and PP-OCRv5; its Hugging Face account holds PP-OCRv6 and manga-ocr-mobile ONNX files | §4, §10 |

### Not verified (vision)

- **Phone speed of Hayai and Baberu.** No source has it.
- **Where Yomu's 2025 OCR stands.** Not on any public benchmark.
- **ONNX Runtime release notes.** `github.com/microsoft/onnxruntime` returned 404 from this machine today, with and without sign-in. Release details come from the newreleases.io mirror and onnxruntime.ai.
- **Undocumented models.** `fumetodev/rtmdet-manga-layout-onnx`, `fumetodev/manga-sfx-filter-onnx`, `ogkalu/manga-ocr-mobile` and `ogkalu/ppocr-v6-onnx` have no model cards; classes, training data and accuracy are unknown.
- **Furigana behaviour** of Hayai and Baberu.
- **Vision-encode time** for Qwen3.5, Gemma 4 and Ministral 3 on a phone.
- **MI-GAN's on-device timings** and the **AOT inpainter's licence chain**.
- **manga-image-translator and BallonsTranslator** are active (pushed this week) but I did not read their 2026 changelogs.

### Sources (vision)

**Yomu repo (read-only):** `CONTEXT.md`; `docs/adr/0005`, `0007`, `0011`, `0020`; `docs/research/2d-text-structure.md`; `docs/research/jev-bubble-class-hints.md`; `app/src/main/java/com/yomu/app/service/ModelManager.kt`; `ml/src/main/java/com/yomu/ml/OnnxRuntime.kt`; `ml/build.gradle.kts`; `pipeline/.../bubble/BubbleRenderBox.kt`; `pipeline/.../context/PaperMask.kt`; `pipeline/.../typesetting/Typesetter.kt`; `app/.../overlay/TranslationRenderOverlay.kt`; `ml/llama.cpp` at `29b52d3` (working tree) and `7fe450e` (pin).

**OCR**
- JMangaBench_Read: https://github.com/muscgab/JMangaBench_Read
- Hayai v2.7 Supernova: https://huggingface.co/JustANormalTinkerer/hayai-ocr-v2.7-supernova · ONNX: https://huggingface.co/JustANormalTinkerer/hayai-ocr-v2.7-supernova-onnx
- Baberu OCR: https://huggingface.co/genshiai-daichi/baberu-ocr
- muscgab models: https://huggingface.co/muscgab/manga-ocr-charspot-nano · https://huggingface.co/muscgab/manga-ocr-charspot-small · https://huggingface.co/muscgab/manga-ocr-nar-small-distil
- Yomu's OCR lineage: https://huggingface.co/jzhang533/manga-ocr-base-2025 · https://github.com/jzhang533/manga-ocr · https://huggingface.co/artsaraiva/manga-ocr-2025-onnx-past · https://huggingface.co/kha-white/manga-ocr-base · https://huggingface.co/onnx-community/manga-ocr-base-ONNX
- PaddleOCR-VL: https://huggingface.co/jzhang533/PaddleOCR-VL-For-Manga · https://huggingface.co/PaddlePaddle/PaddleOCR-VL-1.6 · https://huggingface.co/sorryhyun/paddleocr-vl-1.6-manga-lora
- PP-OCRv6: https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec · https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det · https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_det_onnx · https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx
- manga-ocr-mobile: https://huggingface.co/bluolightning/manga-ocr-mobile · https://huggingface.co/ogkalu/manga-ocr-mobile · https://huggingface.co/ogkalu/ppocr-v6-onnx
- Document OCR VLMs (Hugging Face API): zai-org/GLM-OCR, deepseek-ai/DeepSeek-OCR, deepseek-ai/DeepSeek-OCR-2, rednote-hilab/dots.ocr, lightonai/LightOnOCR-2-1B, ibm-granite/granite-docling-258M, openbmb/MiniCPM-OCR-1.0, tencent/HunyuanOCR, hal-utokyo/MangaLMM
- Vertical Japanese in MLLMs: https://arxiv.org/abs/2511.15059
- Local VLM OCR test: https://nullmirror.com/en/blog/2026-05-24-local-vision-language-ocr-benchmark/

**Vision tower and multimodal translation**
- llama.cpp multimodal docs: https://github.com/ggml-org/llama.cpp/blob/29b52d398b294509746ec0a3f28948a3107c0213/docs/multimodal.md
- mmproj files: https://huggingface.co/unsloth/Qwen3.5-2B-GGUF (and 0.8B, 4B, 9B) · https://huggingface.co/google/gemma-4-E2B-it-qat-q4_0-gguf · https://huggingface.co/ggml-org/gemma-4-E2B-it-GGUF · https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512-GGUF
- Token geometry: https://huggingface.co/Qwen/Qwen3.5-2B/blob/main/preprocessor_config.json · https://huggingface.co/google/gemma-4-E2B-it
- Phone reports: https://github.com/xiziphus/np2-llm-bench · https://github.com/ggml-org/llama.cpp/issues/27649
- Papers: https://arxiv.org/abs/2411.02589 (https://aclanthology.org/2025.coling-main.232/) · https://arxiv.org/abs/2610.17908

**Page structure and detection**
- https://huggingface.co/ogkalu/comic-text-and-bubble-detector
- https://huggingface.co/forkger/manga-panel-rfdetr
- https://huggingface.co/mayocream/koharu-layout-highres · https://github.com/koharu-rs/koharu · https://koharu.rs/models/vision
- https://huggingface.co/ragavsachdeva/magiv3 · https://arxiv.org/abs/2503.23344
- https://arxiv.org/abs/2607.01018

**Fumeto Reader Plus**
- https://github.com/fumetodev/FumetoReaderPlus (README, `CLAUDE.md`, `THIRD-PARTY-NOTICES.md`)
- https://huggingface.co/fumetodev/PP-OCRv6_small_rec_manga_ONNX · https://huggingface.co/fumetodev/PP-OCRv6_medium_det_ONNX · https://huggingface.co/fumetodev/rtmdet-manga-layout-onnx · https://huggingface.co/fumetodev/manga-sfx-filter-onnx

**Drawing back**
- https://github.com/Picsart-AI-Research/MI-GAN · https://huggingface.co/litert-community/MI-GAN-512-Places2-LiteRT
- https://huggingface.co/Carve/LaMa-ONNX · https://huggingface.co/ogkalu/lama-manga-onnx-dynamic · https://huggingface.co/mayocream/lama-manga · https://huggingface.co/ogkalu/aot-inpainting
- https://github.com/Yuu18id/manga-image-translator-android
- https://developer.android.com/reference/android/text/StaticLayout.Builder

**ONNX Runtime**
- https://onnxruntime.ai/docs/execution-providers/Xnnpack-ExecutionProvider.html
- https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html
- https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html
- https://developer.android.com/ndk/guides/neuralnetworks/migration-guide
- https://onnxruntime.ai/blogs/arm-microsoft-kleidiai · https://onnxruntime.ai/docs/performance/kleidiai/
- https://newreleases.io/project/github/microsoft/onnxruntime/release/v1.31.0 (mirror)
- https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/maven-metadata.xml · https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android-qnn/maven-metadata.xml

---

