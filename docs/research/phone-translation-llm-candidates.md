# Phone-sized translation LLM candidates for the curated catalog

Desk research, 2026-09-16. Nothing here was run on a phone. Every number is from a model card, a config file, the HF API, a licence text, a vendor tech report, or the vendored llama.cpp source (`ml/llama.cpp` at `37b53fd45`). Vendor benchmark numbers are **self-reported**. Anything not checked against a primary source says **unverified**.

Scope: GLM, DeepSeek, Muse, Kimi, Qwen, MiniMax, Mistral, Llama, Gemma. One model outside that list (Tencent Hy-MT2-1.8B) gets a short note because the brief names Hunyuan-MT as the kind of translation model to look for.

## How the numbers were computed

- **Fit gate** (`LlmModelCatalog.canRunOnDevice`): `sizeBytes + 800 MiB + kvCacheBytesPerToken × contextTokens ≤ totalMem / 100 × 60`. For a nominal 8 GiB `totalMem`, the budget is **5,153,960,700 B (4.80 GiB)**. What `totalMem` an 8 GB Galaxy S23 actually reports is unverified. It will be below 8 GiB, so treat the headroom figures as upper bounds.
- **Context**: the brief asks for ~4096 tokens. Note that Yomu's default is 2048 and the settings options are 1536/2048/2816 (`core/.../TranslationSlot.kt:151-154`). The table uses 4096. At 2048 every verdict below comes out the same.
- **KV bytes/token**: `layers × 2 × KV heads × head_dim × 2` (f16), from each `config.json`. Where attention is hybrid, both figures are given: the brief's plain formula, and the effective value llama.cpp would allocate.
  - **Sliding-window attention does not shrink the cache in Yomu today.** `llama_jni.cpp:180` starts from `llama_context_default_params()`, which sets `swa_full = true` (`ml/llama.cpp/src/llama-context.cpp:3652`), and Yomu never overrides it. So SWA layers are counted at full context below.
  - **Gemma 4 KV sharing does shrink the cache.** Only the first `n_layer − num_kv_shared_layers` layers own a KV cache (`src/models/gemma4.cpp:10`; `src/llama-model.cpp:2635-2638`).
  - **Qwen3.5 linear-attention layers** (Gated DeltaNet) keep a fixed-size recurrent state, stored as f32 (`src/llama-model.cpp:2578-2579`), not a per-token KV cache. The state size shown is my estimate from config, not a measurement.
- **Chat templates**: Yomu formats prompts with `llama_chat_apply_template` (`ml/src/main/cpp/llama_jni.cpp:132`). That is the heuristic, non-Jinja path in `src/llama-chat.cpp`. If a template is not recognised, Yomu falls back to CAT-Translate's `<|user|>…</s><|assistant|>`.
  - To check each candidate, I compiled `llm_chat_detect_template` + `llm_chat_apply_template` from the vendored source into a small harness. I fed it the `tokenizer.chat_template` read from each candidate GGUF's header (HTTP range request on the pinned file). The "template" results below come from running that harness, not from reading docs.

## Summary

### Ranked shortlist

1. **Qwen3.5-2B** (Qwen, Apache-2.0). Q4_K_M is 1.40 GB. KV is about 12 KB/token because only 6 of 24 layers use attention. It runs in non-thinking mode by default. Its self-reported WMT24++ score is 45.8, against 39.3 for Qwen3-1.7B. The vendored llama.cpp recognises its template as ChatML. **Main risk:** how fast the hybrid DeltaNet runs on a phone CPU.
2. **Gemma 4 E2B-it** (Google, **Apache-2.0**, a change from the Gemma Terms). There is a first-party QAT Q4_0 GGUF (3.35 GB) and an unsloth QAT UD-Q4_K_XL (2.62 GB). KV is tiny thanks to KV sharing, and thinking is off by default. **Blocker:** the vendored heuristic template path does not recognise Gemma 4's `<|turn>` format, so Yomu would currently prompt it in CAT format. Speed is also unknown (35 layers, 5.1B stored params including per-layer embeddings).
3. **Qwen3.5-4B** (Qwen, Apache-2.0). Q4_K_M is 3.01 GB. It has the strongest self-reported WMT24++ score of the family at this size (66.6). It **thinks by default**, so Yomu would have to add the empty `<think>` block itself. Expect it to be the slowest of the shortlist.
4. **Ministral 3 3B Instruct 2512** (Mistral, Apache-2.0). There is an official first-party Q4_K_M GGUF (2.15 GB). The card lists Japanese, Chinese and Korean and names "real-time efficient translation" as a use case, but gives no translation benchmark. KV is heavy at 106 KB/token. The template is recognised as `mistral-v7`, with a one-space mismatch against the official template.
5. **TranslateGemma 4B** (Google, **Gemma Terms of Use**). The only translation-specialised model at phone size in these families, and its Q4_K_M is 2.49 GB. Against it: Google's own tech report finds TranslateGemma **regressed on Japanese→English** in human evaluation (12B/27B), the card says "Total input context of 2K tokens", the prompt format is rigid (one text per call), and the licence is not Apache. It is conditional at best.

Also viable but not ranked: **Qwen3-4B-Instruct-2507** (non-thinking only, WMT24++ 58.9 self-reported, KV 147 KB/token) and **Qwen3.5-0.8B** (0.58 GB, WMT24++ 27.2; a floor-tier alternative to CAT-Translate 0.8B).

### Families ruled out

| Family | Why |
|---|---|
| **Muse** (Meta) | "Muse" in the 2026 LLM space is Meta Superintelligence Labs' family. **Muse Spark** is the frontier model it is distilled from; no weights were found on HF, so it is unverified as an open model. The open model is **Muse Glimmer-30B**: 29.6B dense params, Apache-2.0, smallest first-party GGUF 16.76 GB. There is no small Muse. Other things called "Muse" are not text translators: Microsoft's `microsoft/wham` world model and `microsoft/MuseVLA` robotics model. |
| **Kimi** (Moonshot) | No phone-sized model. The smallest are Kimi-VL-A3B (16.4B total) and Kimi-Linear-48B-A3B (49.1B). Kimi-K3 is about 2.78T. |
| **MiniMax** | No phone-sized text LLM. M2.7 is 228.7B and M3 is 427.0B. MiniMax-H3 is 33.1B and is mostly re-hosted by image/video tooling (Comfy-Org, lightx2v); that it is not a chat LLM is unverified. |
| **DeepSeek** | No first-party small model; V4-Flash is 290.9B and V4.1-Flash 763.2B. The only small DeepSeek release is **DeepSeek-R1-Distill-Qwen-1.5B**, a finetune of **Qwen2.5-Math-1.5B** (MIT, inheriting Apache-2.0 from Qwen). It is math-focused and built to reason: the card recommends forcing a `<think>` start, and there is no translation claim. `dspark_*`/`dflash_*`/`eagle3_*` repos are speculative-decoding drafters, not translators. |
| **GLM** (Zhipu/Z.ai) | The current line is huge (GLM-5.3-Flash is 321B; GLM-4.7-Flash is 31.2B). The only phone-sized chat models are **glm-edge-1.5b/4b-chat** (Nov 2024). Their GLM-Edge licence requires **registration for commercial use**, a "Built with GLM-Edge" notice, and Chinese law as governing law. Their GGUFs declare `chatglm.context_length = 2048`, and the card makes no translation claim. GLM-OCR (1.3B) is OCR, not translation. |
| **Llama** (Meta) | Llama 3.2 1B/3B fit, but Japanese, Chinese and Korean are **not** officially supported languages. The Llama 3.2 Community Licence adds "Built with Llama", a Notice file, the AUP and a 700M MAU clause. Llama 4 has no small model, and Meta's newer open line is Muse (above). No reason to prefer these over Qwen3.5 or Gemma 4. |

## Comparison table

KV figures and the fit verdict are at 4096 tokens as Yomu runs today (`swa_full = true`). "Need" is `size + 800 MiB + KV × 4096 (+ recurrent state)`. The budget is 4.80 GiB for 8 GiB `totalMem`.

| Model | Params / arch | Q4 GGUF repo · file · bytes | KV B/token (formula → effective) | Need @4096 · fits 8 GB? | JA→EN evidence | Licence (redistributable?) | llama.cpp arch · template in Yomu | Thinking? | Uncensored variant |
|---|---|---|---|---|---|---|---|---|---|
| **Qwen3.5-2B** | 2.27B incl. vision; 24 layers, 18 Gated DeltaNet + 6 gated attention | `bartowski/Qwen_Qwen3.5-2B-GGUF` · `Qwen_Qwen3.5-2B-Q4_K_M.gguf` · 1,396,198,496 (unsloth: 1,280,835,840) | 49,152 → **12,288** + ~19 MiB recurrent state | 2.15 GiB · **yes** | No per-pair number. WMT24++ avg over 55 langs (XCOMET-XXL), "Thinking" table: 45.8 (self-reported) | Apache-2.0 base and GGUF · yes | `qwen35` ✓ · ChatML ✓, but no empty `<think></think>` block | Non-thinking by default (2B card) | HauhauCS Q4_K_M 1,270,808,032; huihui-ai abliterated (mradermacher GGUF 1,270,809,024) · Apache-2.0 |
| **Gemma 4 E2B-it** | 2.3B effective / 5.1B with embeddings; 35 layers, 20 KV-shared; 4:1 SWA (512) : global | `google/gemma-4-E2B-it-qat-q4_0-gguf` · `gemma-4-E2B_q4_0-it.gguf` · 3,349,516,256 (first-party). `unsloth/gemma-4-E2B-it-qat-GGUF` · `gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf` · 2,620,370,976 | 35,840 → **18,432** | 3.97 GiB (google) / 3.29 GiB (unsloth) · **yes** | Text MT: none published. Speech: CoVoST ja→en BLEU 21.4 (tech report, self-reported). MMMLU 67.4% | **Apache-2.0** base and GGUFs · yes | `gemma4` ✓ · **UNKNOWN → CAT fallback ✗** | Off unless `<\|think\|>` is in the system turn | huihui-ai QAT abliterated Q4_K 3,416,118,240 (Apache-2.0); HauhauCS Q4_K_P 3,450,277,824 (repo tagged `gemma`) |
| **Qwen3.5-4B** | 4.66B incl. vision; 32 layers, 24 DeltaNet + 8 attention | `bartowski/Qwen_Qwen3.5-4B-GGUF` · `Qwen_Qwen3.5-4B-Q4_K_M.gguf` · 3,013,027,808 | 131,072 → **32,768** + ~50 MiB recurrent state | 3.76 GiB · **yes** | WMT24++ 66.6 (4B card, self-reported) | Apache-2.0 · yes | `qwen35` ✓ · ChatML ✓, no empty think block | **Thinks by default** (4B card) | HauhauCS Q4_K_M 2,707,513,696; huihui-ai abliterated (mradermacher 2,707,514,688) · Apache-2.0 |
| **Ministral 3 3B Instruct 2512** | 3.4B LM + 0.4B vision; 26 layers, GQA 32/8 | `mistralai/Ministral-3-3B-Instruct-2512-GGUF` · `Ministral-3-3B-Instruct-2512-Q4_K_M.gguf` · 2,147,023,008 (first-party) | 106,496 | 3.19 GiB · **yes** | None for translation. Base Multilingual MMLU: ja 65.7, zh 64.1, ko 48.9 (paper, self-reported) | Apache-2.0 · yes | `mistral3` ✓ · `mistral-v7` ✓ (emits `[INST] x[/INST]`; official is `[INST]x[/INST]`) | No; there is a separate `-Reasoning-2512` model | None from huihui-ai/HauhauCS/mlabonne found |
| **TranslateGemma 4B** | 4.97B incl. vision; Gemma 3, 34 layers, 5:1 SWA (1024) | `mradermacher/translategemma-4b-it-GGUF` · `translategemma-4b-it.Q4_K_M.gguf` · 2,489,909,760 (no first-party GGUF) | 139,264 (20,480 global + SWA if `swa_full` were false) | 3.63 GiB · **yes** | WMT24++ is en→X only; en→ja MetricX 4.44 vs Gemma 3 4B 5.09. Human MQM **JA→EN regression**: TG-27B 13.4, TG-12B 15.7, Gemma 3 27B 11.6 (tech report) | **Gemma Terms of Use**; base is gated. Redistribution allowed with conditions (§3.1) | `gemma3` ✓ · `gemma` ✓ | No | None from huihui-ai/HauhauCS/mlabonne found |
| Qwen3-4B-Instruct-2507 | 4.02B; 36 layers, GQA 32/8 | `bartowski/Qwen_Qwen3-4B-Instruct-2507-GGUF` · `…-Q4_K_M.gguf` · 2,497,280,736 | 147,456 | 3.67 GiB · yes | WMT24++ 58.9 (Qwen3.5-2B card, self-reported) | Apache-2.0 · yes | `qwen3` ✓ · ChatML ✓ | Non-thinking only | not surveyed |
| Qwen3.5-0.8B | 0.87B; same layout as 2B | `bartowski/Qwen_Qwen3.5-0.8B-GGUF` · `Qwen_Qwen3.5-0.8B-Q4_K_M.gguf` · 579,615,840 | 49,152 → 12,288 + ~19 MiB | 1.39 GiB · yes | WMT24++ 27.2 (self-reported) | Apache-2.0 · yes | `qwen35` ✓ · ChatML ✓ | Non-thinking by default | huihui-ai abliterated |
| Llama 3.2 3B Instruct | 3.21B; 28 layers, GQA 24/8 | `bartowski/Llama-3.2-3B-Instruct-GGUF` · `…-Q4_K_M.gguf` · 2,019,377,696 | 114,688 | 3.10 GiB · yes | None; ja/zh/ko not officially supported | Llama 3.2 Community · yes, with conditions | `llama` ✓ · `llama3` ✓ | No | huihui-ai/Llama-3.2-3B-Instruct-abliterated (llama3.2) |
| glm-edge-1.5b-chat | 1.59B; 28 layers, GQA 16/4 | `zai-org/glm-edge-1.5b-chat-gguf` · `ggml-model-Q4_K_M.gguf` · 980,470,144 | 57,344 | 1.91 GiB · yes (GGUF ctx 2048) | None | GLM-Edge licence; commercial use needs registration | `chatglm` ✓ · `glmedge` ✓ | No | mradermacher heretic GGUF (not assessed) |
| DeepSeek-R1-Distill-Qwen-1.5B | 1.78B; Qwen2 | `bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF` · `…-Q4_K_M.gguf` · 1,117,320,800 | 28,672 | 1.93 GiB · yes | None | MIT (+ Apache-2.0 upstream) · yes | `qwen2` ✓ · `deepseek3` ✓ | **Reasoning model** | not surveyed |
| Gemma 4 E4B-it | 4.5B effective / 8B; 42 layers, 18 KV-shared | `google/gemma-4-E4B-it-qat-q4_0-gguf` · `gemma-4-E4B_q4_0-it.gguf` · 5,154,941,280 | 86,016 → 57,344 | 5.80 GiB · **no** | CoVoST ja→en 25.5 (speech) | Apache-2.0 | `gemma4` ✓ · UNKNOWN ✗ | Off by default | HauhauCS, huihui-ai |
| Muse Glimmer-30B | 29.6B dense | `meta-models/Muse-Glimmer-30B-GGUF` · `Muse-Glimmer-30B-KQuant-17GB-Q4_K_M.gguf` · 16,756,683,904 | — | **no** | — | Apache-2.0 | `muse-glimmer` ✓ | Reasoning strength low…xhigh | Blackfrost-AI, mlasli (not assessed) |
| *Adjacent:* Hy-MT2-1.8B (Tencent) | 2.04B; 32 layers, GQA 16/4 | `tencent/Hy-MT2-1.8B-GGUF` · `Hy-MT2-1.8B-Q4_K_M.gguf` · 1,133,080,448 (first-party) | 65,536 | 2.09 GiB · yes | Translation-specialised; 33 languages including ja/ko/zh | Apache-2.0 (2026 release) · yes | `hunyuan-dense` ✓ · **misdetected as `hunyuan-vl` → malformed prompt ✗** | "fast-thinking", no think block | mradermacher heretic, mlx abliterated (not assessed) |

For comparison, today's default Qwen2.5-1.5B Q4_K_M is 986,048,768 B with 28,672 B/token, needing 1.81 GiB.

## Per-family detail

### Qwen (Alibaba)

**What exists at phone size.** The small models are Qwen3.5-0.8B, -2B and -4B (released 2026-02-27/28), plus Qwen3-0.6B/1.7B/4B and Qwen3-4B-Instruct-2507. Qwen3.6 and Qwen3.8 start at 27B/35B-A3B. There is no open-weights Qwen-MT model: searching HF for "Qwen-MT"/"Qwen3-MT" only returns MTP (multi-token-prediction) repos. Qwen-MT is an API product (unverified; no weights found).

**Architecture and KV.** Qwen3.5-2B has 24 layers: `layer_types` is 18 `linear_attention` + 6 `full_attention` (`full_attention_interval: 4`), with KV heads 2 and head_dim 256. Qwen3.5-4B has 32 layers (24 linear + 8 full) with KV heads 4. Only the full-attention layers hold a KV cache, which gives 12,288 B/token for 0.8B and 2B, and 32,768 B/token for 4B.

The DeltaNet state is sized from `linear_num_value_heads × linear_key_head_dim × linear_value_head_dim` in f32 per layer, plus a small conv state. That comes to roughly 19 MiB (2B) and 50 MiB (4B), fixed per sequence. This is an estimate; confirm it from the load log.

The bartowski GGUF declares `qwen35.block_count = 25`, which includes the MTP layer. The vendored loader skips MTP tensors unless `load_mtp` is set (`src/models/qwen35.cpp:36-38`).

**Translation evidence.** All self-reported, from the model cards. Qwen3.5 claims "Expanded support to 201 languages and dialects".
- The 2B card's "Multilingualism (Thinking)" table gives WMT24++ ("averaged scores on 55 languages using XCOMET-XXL") of **Qwen3-4B-2507 58.9, Qwen3-1.7B 39.3, Qwen3.5-2B 45.8, Qwen3.5-0.8B 27.2**.
- The 4B card gives **Qwen3.5-4B 66.6** (and Qwen3.5-9B 72.6) in a different table with different comparators. Do not compare it directly with the 2B table.
- WMT24++ is an English→X benchmark (see the TranslateGemma report below), so none of these numbers measures JA→EN directly.

**Licence.** Apache-2.0 on the base models (`license: apache-2.0` in the card) and on the bartowski/unsloth GGUF repos (HF API). None are gated.

**GGUF.** No first-party Qwen3.5 GGUF: `Qwen/Qwen3.5-0.8B-GGUF` returns 401. Re-hosts are bartowski (sha `7d26695…` for 2B, `4168f45…` for 4B, `f36b1ea…` for 0.8B), unsloth (`f6d5376…` for 2B) and ggml-org (0.8B Q4_0 only).
- 2B Q4_K_M lfs oid `57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8`.
- 4B Q4_K_M oid `13c16f426047e2de38cd075bdade4a7bcbc8c774384876f677740cda65f8a983`.
- The `mmproj-*` vision files are not needed.

**llama.cpp.** The `qwen35` arch is present (`src/llama-arch.cpp`, `src/models/qwen35.cpp`). The GGUF template is detected as ChatML and renders `<|im_start|>user\n…<|im_end|>\n<|im_start|>assistant\n`.

**Thinking.** From the model cards:
- 0.8B and 2B: "operates in non-thinking mode by default".
- 4B: "Qwen3.5 models operate in thinking mode by default".
- All three: "Qwen3.5 does not officially support the soft switch of Qwen3, i.e., `/think` and `/nothink`."

The official Jinja template adds `<think>\n\n</think>\n\n` to the assistant turn when `enable_thinking` is not true. Yomu's heuristic ChatML path does **not** add it, so every Qwen3.5 model would start its turn without the empty think block it was trained to see. Under Yomu's grammar a `<think>` token cannot be sampled, so the question is quality, not parse failure. The 2B card also warns that in thinking mode "Qwen3.5-2B is more prone to entering thinking loops".

Qwen3-4B-Instruct-2507: "supports only non-thinking mode and does not generate `<think></think>` blocks".

**Sampling.** The card recommends, for non-thinking text, `temperature=1.0, top_p=1.00, top_k=20, presence_penalty=2.0`. Yomu applies one global profile to every model (#192).

**Uncensored.**
- `HauhauCS/Qwen3.5-2B-Uncensored-HauhauCS-Aggressive` (sha `2bcf35c…`, Apache-2.0, Q4_K_M 1,270,808,032) and the 4B equivalent (`c09cdbc…`, Q4_K_M 2,707,513,696). The card claims "0/465 refusals. Fully uncensored with zero capability loss". That is self-claimed, with no evaluation shown.
- `huihui-ai/Huihui-Qwen3.5-2B-abliterated` / `-4B-abliterated` (Apache-2.0). The card calls itself "a crude, proof-of-concept implementation to remove refusals". GGUFs are at `mradermacher/Huihui-Qwen3.5-{2B,4B}-abliterated-GGUF`.
- No card reports translation quality before and after.

### Gemma (Google)

**What exists at phone size.**
- Gemma 4 E2B-it and E4B-it (released 2026-03-02).
- Their QAT variants, released 2026-05/06: `-qat-q4_0-gguf`, `-qat-q4_0-unquantized`, `-qat-mobile-*`, `-qat-w4a16-ct`.
- TranslateGemma 4B (2026-01-12, built on Gemma 3).
- Gemma 3 1B/4B and Gemma 3n E2B/E4B, still under the Gemma Terms.

The 12B, 26B-A4B, 31B and DiffusionGemma-26B-A4B are not phone-sized.

**Licence change.** Gemma 4 is **Apache-2.0**. The card links "License: Apache 2.0" to `ai.google.dev/gemma/docs/gemma_4_license`, which serves the Apache License 2.0 text verbatim, and the Gemma 4 tech report says it is released "under an Apache 2.0 license". The HF API shows `license: apache-2.0` and ungated for `google/gemma-4-E2B-it` and `google/gemma-4-E2B-it-qat-q4_0-gguf`.

This removes the reason `LlmModelCatalog.ALL`'s KDoc and ADR-0014 give for dropping Gemma: "no usable licence-clean gated GGUF". Gemma 4 needs no gate. **That reasoning was about Gemma 2/3 and TranslateGemma, and does not apply to Gemma 4.**

- Caveat: the unsloth GGUF's embedded metadata still says `general.license = gemma` (read from the file header), while the repo says apache-2.0. Stale converter metadata, not a licence grant.
- TranslateGemma and Gemma 3 remain under the **Gemma Terms of Use** ("Last modified: April 1, 2026"), and `google/translategemma-4b-it` is gated (`gated: manual`). §3.1 allows redistribution with conditions:

  > "You must include the use restrictions referenced in Section 3.2 as an enforceable provision in any agreement … governing the use and/or distribution of Gemma or Model Derivatives … You must provide all third party recipients of Gemma or Model Derivatives a copy of this Agreement. … All Distributions (other than through a Hosted Service) must be accompanied by a "Notice" text file that contains the following notice: "Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms"."

  §3.2 folds in the Prohibited Use Policy, and Google "reserves the right to restrict (remotely or otherwise) usage". So redistributing TranslateGemma is allowed, but it carries pass-through obligations Apache models do not. ADR-0014 dropped it because of the gate premise, not because redistribution was forbidden.

**Gemma 4 E2B architecture and KV.**
- The card says "2.3B effective (5.1B with embeddings)", with Per-Layer Embeddings, an ~150M vision encoder and an ~300M audio encoder.
- `text_config`: 35 layers, `num_kv_shared_layers: 20`, `num_key_value_heads: 1`, `head_dim: 256`, `global_head_dim: 512`, `sliding_window: 512`, full attention every 5th layer.
- The 15 KV-owning layers are 12 sliding and 3 full, giving 12×2×1×256×2 + 3×2×1×512×2 = **18,432 B/token** with `swa_full = true`.
- With `swa_full = false`, the sliding part would cap at about 512 + n_ubatch tokens, leaving roughly 6,144 B/token of growth.
- E4B: 42 layers, 18 shared, KV heads 2, giving 57,344 B/token. Its first-party Q4_0 is 5.15 GB, over budget.

**Translation evidence.**
- The Gemma 4 E2B card has no text-translation benchmark. It claims "Out-of-the-box support for 35+ languages, pre-trained on 140+ languages" and MMMLU 67.4%.
- The Gemma 4 tech report (arXiv 2607.02770, Table 7) only has **speech** translation: CoVoST ja→en BLEU **21.4** (E2B) and 25.5 (E4B), zh→en 17.9 / 21.9. That is transcribe-then-translate from audio. It is not evidence about text JA→EN.

**TranslateGemma 4B evidence** (tech report arXiv 2601.09012, CC-BY-4.0):
- Card: WMT24++ (55 languages), MetricX 5.32 and COMET 81.6 for 4B.
- Appendix Table 4: WMT24++ is English→X. For 4B, en→ja_JP MetricX is **4.44** (Gemma 3 4B: 5.09), en→zh_CN 2.66 (3.27), en→ko_KR 3.93 (4.72).
- Japanese→English appears only in the WMT25 human MQM evaluation (12B/27B, not 4B): TranslateGemma 27B **13.4**, 12B **15.7**, Gemma 3 27B **11.6** (lower is better). The report says "Japanese→English where TranslateGemma actually suffers a regression … due to mistranslation of named entities".
- The card states "Total input context of 2K tokens". Its template is rigid: user content must be a one-element list with `source_lang_code`/`target_lang_code`, and it "supports only User and Assistant roles". The report's preferred prompt ends "Produce only the {target_lang} translation, without any additional explanations or commentary". Whether it can emit Yomu's multi-id page batch is untested.

**GGUF.**
- Gemma 4 E2B, first-party: `google/gemma-4-E2B-it-qat-q4_0-gguf` (sha `675cff4…`), `gemma-4-E2B_q4_0-it.gguf`, 3,349,516,256 B, oid `fa401b55b07ee70a54c6dae3903c783a6e65064312529ea57175cb5f8dec6634`.
- Re-hosts:
  - `unsloth/gemma-4-E2B-it-qat-GGUF` (sha `66a399f…`): `gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf`, 2,620,370,976 B, oid `e531007218dfab990486a5de7676a6932d6ea8dea233d1f698d7c21cf8a16889`. This is the file koharu pins.
  - `ggml-org/gemma-4-E2B-it-GGUF`: Q4_0, 2,841,481,184 B.
  - `bartowski/google_gemma-4-E2B-it-GGUF`: Q4_K_M, 3,462,680,032 B.
- TranslateGemma 4B: no first-party GGUF. `mradermacher/translategemma-4b-it-GGUF` (sha `35a7486…`, tagged `gemma`, ungated re-host of a gated model) has `translategemma-4b-it.Q4_K_M.gguf`, 2,489,909,760 B, oid `81200d03e843d2ec1ece6eeafe7d13cb6e5211e1fcd336ade55790b683a08330`.

**llama.cpp.** The `gemma4` arch is present (`src/models/gemma4.cpp`, KV sharing handled).
- **Template:** the Gemma 4 template uses `<|turn>user` / `<|turn>model` / `<turn|>` and matches none of the heuristics in `llm_chat_detect_template`. It was checked on both the google and unsloth GGUF templates, with the harness returning `LLM_CHAT_TEMPLATE_UNKNOWN`. Yomu would therefore send the CAT fallback `<|user|>…</s><|assistant|>`, which is wrong.
- The Jinja path does handle Gemma 4 (`common/chat.cpp:1186-1193`), but Yomu does not link `common/chat`.
- TranslateGemma's Gemma 3 template is detected as `gemma` and renders `<start_of_turn>user\n…<end_of_turn>\n<start_of_turn>model\n`. That skips the language-code fields the official Jinja template injects.

**Thinking.** From the card: "Thinking is enabled by including the `<|think|>` token at the start of the system prompt. To disable thinking, remove the token." And: "For all models except for the E2B and E4B variants, if thinking is disabled, the model will still generate the tags but with an empty thought block". In the template, `enable_thinking` defaults to false. So E2B/E4B with no system turn do not think, and emit no empty channel either.

**Uncensored.**
- `huihui-ai/Huihui-gemma-4-E2B-it-qat-q4_0-unquantized-abliterated-GGUF` (sha `e38a3cd…`, Apache-2.0, `…-Q4_K.gguf` 3,416,118,240). The card calls itself a "crude, proof-of-concept implementation".
- `HauhauCS/Gemma-4-E2B-Uncensored-HauhauCS-Aggressive` (sha `da8593c…`, the file koharu pins): `…-Q4_K_P.gguf` 3,450,277,824. The repo is tagged `license: gemma` even though the base is Apache-2.0, so the tag looks wrong. Unverified which licence the author intends. The card claims "0/465 Refusals" and "lossless", and adds that Gemma 4 "didn't get as much manual testing time at longer context".
- No translation-quality data. No abliterated TranslateGemma found from huihui-ai, HauhauCS or mlabonne.

### Mistral

**What exists at phone size.** Ministral 3 3B Instruct 2512 and Ministral 3 3B Reasoning 2512 (both Apache-2.0). Also Shieldstral-1.0-3B, a guard model on the same base, and Voxtral Mini (speech). No Mistral translation finetune was found.

**Architecture and KV.** The paper (arXiv 2601.08584, Table 1) gives "Ministral 3 3B 26 3072 32 / 8 9216 ✓ 256k". The config gives 26 layers, 8 KV heads, head_dim 128, and `sliding_window: null`. That is **106,496 B/token**, about 406 MiB at 4096 tokens, and the heaviest KV of the shortlist.

**Translation evidence.**
- The card says "Supports dozens of languages, including English, French, Spanish, German, Italian, Portuguese, Dutch, Chinese, Japanese, Korean, Arabic" and lists "Real-time efficient translation" among use cases.
- There is no translation benchmark in the card or paper. The paper's Table 3 gives base Multilingual MMLU (5-shot) for 3B: Chinese 64.1, Japanese 65.7, Korean 48.9 (self-reported). That is knowledge, not translation.

**Licence.** Apache-2.0 on the base and on the first-party GGUF repo. The card says "Open-source license allowing usage and modification for both commercial and non-commercial purposes." Not gated per the HF API, though the GGUF repo carries an `extra_gated_description` privacy notice.

**GGUF.** First-party `mistralai/Ministral-3-3B-Instruct-2512-GGUF` (sha `eb599d4…`): `Ministral-3-3B-Instruct-2512-Q4_K_M.gguf`, 2,147,023,008 B, oid `9ed150d4367e68df0ac8e1540f6ddc65b42d0ee26378329d1ecbca60f93fc5f8`. bartowski re-host: 2,146,498,528 B.

**llama.cpp.** The `mistral3` arch is present (`general.architecture = mistral3` in the GGUF).
- The template is detected as `mistral-v7` and renders `[INST] TEST_PROMPT[/INST]`, with a space after `[INST]`.
- The official template renders `'[INST]' + message['content'] + '[/INST]'`, with no space, and injects a long default system prompt that the heuristic path omits.
- `mistral-v7-tekken` exists in `LLM_CHAT_TEMPLATES` but is not auto-detected.

**Thinking.** The Instruct model does not think; reasoning is a separate `-Reasoning-2512` model.

**Uncensored.** None found from huihui-ai, HauhauCS or mlabonne. There are community roleplay finetunes (not assessed).

### Llama (Meta)

**What exists at phone size.** Llama 3.2 1B/3B Instruct (2024-09). No newer small Llama; Llama 4 is 17B-active MoE.

**KV.** 1B: 16 layers × 8 KV heads × head_dim 64, giving 32,768 B/token. 3B: 28 layers × 8 × 128, giving 114,688 B/token.

**Translation evidence.** Model card: "English, German, French, Italian, Portuguese, Hindi, Spanish, and Thai are officially supported. Llama 3.2 has been trained on a broader collection of languages than these 8 supported languages." No JA→EN claim.

**Licence** (Llama 3.2 Community License, `meta-llama/llama-models`):
- §1.b.i: "you shall (A) provide a copy of this Agreement with any such Llama Materials; and (B) prominently display "Built with Llama" …"
- §1.b.iii: a Notice file reading "Llama 3.2 is licensed under the Llama 3.2 Community License, Copyright © Meta Platforms, Inc. All Rights Reserved."
- §2: licensees with "greater than 700 million monthly active users" must request a licence.
- The AUP's EU exclusion applies only to "multimodal models included in Llama 3.2", so not to 1B/3B.
- Base repos are gated (`gated: manual`); bartowski's GGUF re-host is ungated, `license: llama3.2`.

**llama.cpp.** `llama` arch; template `llama3` detected. Not a thinking model.

**Uncensored.** `huihui-ai/Llama-3.2-3B-Instruct-abliterated` (llama3.2); bartowski `Llama-3.2-3B-Instruct-uncensored-GGUF` (not assessed).

**Verdict.** Ruled out. It fits, but ja/zh/ko are unsupported, the licence carries conditions, and Qwen3.5-2B is newer and Apache.

### Muse (Meta Superintelligence Labs)

**Identification.** The vendored llama.cpp has a `muse-glimmer` arch (`src/models/muse-glimmer.cpp`, commit `62bf73d25` "model: Muse Glimmer Support (#26841)"). Its hparams switch only knows `case 52: type = LLM_TYPE_30B`. The `meta-models/Muse-Glimmer-30B` card says:
- "Authors: Meta Superintelligence Lab. Model Release Date: August 2026. License: Apache 2.0"
- "Muse Glimmer is a 30-billion-parameter causal language model … distilled from Muse Spark"
- "Total Parameters ~29.6B"; 52 layers, 32/2 attention heads, SWA 2048 in a [Local×3, Global] pattern
- "Multilingual. Muse Glimmer is trained on data from more than 100 languages"
- Reasoning strength is set in the system prompt, "low / medium / high / xhigh" (no "off" level listed)

The `meta-models` org lists only Glimmer-30B repos. No Muse Spark weights were found on HF, and whether Spark is open is unverified. Other "Muse" models on HF are not text LLMs: `microsoft/wham` (a world model) and `microsoft/MuseVLA` (robotics VLA). **Assessed: Muse Glimmer.**

**Verdict.** Ruled out. The smallest first-party GGUF is `Muse-Glimmer-30B-KQuant-17GB-Q4_K_M.gguf` at 16,756,683,904 B.

### GLM (Zhipu / Z.ai, `zai-org`)

**What exists.**
- Current: GLM-5.3 and GLM-5.3-Flash (321.3B params, MIT), GLM-5.2, GLM-4.7-Flash (31.2B), GLM-4.6V-Flash (10.3B), GLM-OCR (1.33B, OCR).
- Phone-sized chat models: only `glm-edge-1.5b-chat` (1.59B) and `glm-edge-4b-chat` (4.33B), from 2024-11. They have first-party GGUFs: 1.5B `ggml-model-Q4_K_M.gguf` 980,470,144 B; 4B 2,627,488,704 B.

**Licence** (`LICENSE`, the "GLM-Edge License"):
- "This license allows you to use all open source models in this repository for free for academic research. For users who wish to use the models for commercial purposes, please do so [here] Complete registration."
- "(B) Prominently display "Built with GLM-Edge" …"
- "This license shall be governed and construed in accordance with the laws of People's Republic of China."

**Other facts.**
- The GGUF header shows `general.architecture = chatglm` and `chatglm.context_length = 2048`.
- The template is detected as `glmedge`.
- The card contains usage code only: no language list and no translation claim.

**Verdict.** Ruled out: commercial use needs registration, context is 2048 in the GGUF, there is no translation evidence, and the models are from 2024.

### DeepSeek

**What exists.** First-party releases are all large: V4-Flash 290.9B, V4-Pro, V4.1-Flash 763.2B, V3.x, R1. There are also drafter repos (`dspark_qwen3_4b_block7`, `dflash_*`, `eagle3_*`) for speculative decoding of *other* models.

**The small distill.** `deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B` (1.78B, MIT).
- Card: "Base Model: Qwen2.5-Math-1.5B"; "derived from Qwen-2.5 series, which are originally licensed under Apache 2.0 License, and now finetuned with 800k samples curated with DeepSeek-R1".
- It is a reasoning model. The card says: "we recommend enforcing the model to initiate its response with "<think>\n" at the beginning of every output."
- The template is detected as `deepseek3`. There is no translation claim.
- Community "DeepSeek-V4-Pro-Qwen3.5-4B" distills (e.g. `Jackrong/…`) are third-party Qwen3.5 finetunes, not DeepSeek releases.

**Verdict.** Ruled out: DeepSeek has no phone-sized model of its own, and the Qwen-based distill is math/reasoning-oriented.

### Kimi (Moonshot AI)

Everything is too large. From the HF API `safetensors.total`: Kimi-K3 2,779,931,837,184; Kimi-Linear-48B-A3B 49,122,681,728; Kimi-VL-A3B-Instruct 16,407,657,776; Moonlight-16B-A3B. The vendored llama.cpp does support `kimi-linear` and `kimi-k3`, but that does not matter at these sizes. **No phone-sized model.**

### MiniMax

MiniMax-M2.7 is 228,689,764,864 params; M3 is 427,040,140,160 (licence "minimax-community"); M2.5, M2.1, M1 and Text-01 are also hundreds of billions. MiniMax-H3 is 33,122,992,896 params under "minimax-h3-community-license-agreement". It is mostly re-hosted by image/video tooling, so it is probably not a chat LLM (unverified). **No phone-sized model.**

### Adjacent, outside the brief's families: Tencent Hy-MT2-1.8B

This is what the brief means by "Hunyuan-MT-style". `tencent/Hy-MT2-1.8B` (2026-05) is Apache-2.0 ("Hy-MT2-1.8B is licensed under the Apache License, Version 2.0"), whereas the earlier Hunyuan-MT carried a Tencent licence (unverified here). The card says it covers "translation among 33 languages", including Japanese and Korean, and describes the models as "fast-thinking". A first-party GGUF exists: `tencent/Hy-MT2-1.8B-GGUF` (sha `a0c709d…`), `Hy-MT2-1.8B-Q4_K_M.gguf`, 1,133,080,448 B, oid `dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699`. KV is 65,536 B/token; the addendum below first "corrected" this to 131,072 and then retracted that, so the fit stays 2.09 GiB at 4096 tokens.

**Blocker in the vendored llama.cpp:** the GGUF template contains `<｜hy_begin▁of▁sentence｜>`, so the heuristic detector returns `hunyuan-vl`. That renders `<｜hy_begin▁of▁sentence｜>PROMPT<｜hy_User｜>`: content before the role marker, and no `<｜hy_Assistant｜>`. The official template renders `<｜hy_begin▁of▁sentence｜><｜hy_User｜>PROMPT<｜hy_Assistant｜>`.

Also noted, not assessed: `fumetodev/Hy-MT2-1.8B-JP-Manga-Finetune-v5-GGUF` exists (licence and provenance unchecked). LiquidAI LFM2.5-1.2B is in koharu's catalog but outside this brief.

## Open questions for an on-device check

These are sharp enough to grill on; each needs a real S23 run or a code change.

1. **Qwen3.5 speed on CPU.** Qwen3.5-2B replaces 75% of attention layers with Gated DeltaNet. Is its ms/page on the S23 below Qwen2.5-1.5B's ~15 s/page, or do the delta-net ops in the vendored ggml ARM CPU backend make it slower despite similar weight bytes? Measure prefill and decode separately, since a page-level batch prompt is prefill-heavy.
2. **Qwen3.5 without the empty think block.** Yomu's heuristic ChatML path omits `<think>\n\n</think>\n\n`, which the official template always adds in non-thinking mode. Under the GBNF grammar, does output differ with the block added to the prompt by hand versus left out? On Qwen3.5-4B (thinks by default), is the forced grammar start enough, or does quality collapse?
3. **Gemma 4 template path.** The heuristic detector returns UNKNOWN for Gemma 4, so Yomu would send the CAT fallback. Which fix: a hard-coded `<|turn>user\n…<turn|>\n<|turn>model\n` branch in `llama_jni.cpp`, or linking `common/chat` (Jinja, `common_chat_params_init_gemma4`)? The same question applies to Hy-MT2 (misdetected as `hunyuan-vl`) and to Ministral's space after `[INST]`. Is it time to stop relying on `llama_chat_apply_template` heuristics for curated models?
4. **Gemma 4 E2B peak RSS versus file size.** Per-layer embeddings are large tables that llama.cpp may leave mmap'd and untouched. Is peak PSS for E2B well below `3.35 GB + 800 MiB`, or above it? Does the file-size fit gate misjudge PLE models in either direction?
5. **Gemma 4 E2B speed.** gemma-2-2b took ~35 s/page. E2B has 35 layers at hidden size 1536 plus a PLE lookup per layer. Does it land nearer 15 or 35 s/page? Is Google's QAT Q4_0 (3.35 GB) faster or slower than unsloth's UD-Q4_K_XL (2.62 GB) on the S23's CPU kernels?
6. **`swa_full`.** Yomu inherits `swa_full = true`. For Gemma-family models, setting it false would cut TranslateGemma's KV from 139,264 to roughly 20,480 B/token beyond the 1024-token window. Page-level single calls never rewind the cache, so is there any reason to keep `swa_full = true`? Does changing it alter output?
7. **Grammar-constrained page batch per model.** For each shortlisted model, does the id-keyed grammar output stay well-formed and translated across the fixture pages? Specifically:
   - Does Ministral 3 3B (card recommends temperature <0.1) behave at Yomu's global sampler settings?
   - Does TranslateGemma, trained on single-text prompts with a 2K input context, handle multi-id pages at all, or is it per-line only (`idKeyedBatch = false`)?
8. **TranslateGemma JA→EN.** Google's own human evaluation shows TranslateGemma 12B/27B worse than Gemma 3 27B on Japanese→English (named-entity errors). Does the 4B show the same on manga names? If so, is its only advantage over Gemma 4 E2B gone, given it also brings the Gemma Terms pass-through?
9. **Which host to pin.** For Qwen3.5 there is no first-party GGUF: bartowski or unsloth? The unsloth 2B Q4_K_M is 115 MB smaller than bartowski's at the same nominal quant. For Gemma 4: first-party QAT Q4_0 (bigger, Google-signed) or unsloth UD-Q4_K_XL (smaller, the one koharu ships)? For Ministral 3, first-party exists. Record the sha256 against the HF API at pin time (ADR-0014).
10. **Uncensored variants.** No abliterated model card reports translation quality. HauhauCS claims "zero capability loss" without evidence; huihui-ai calls its method "crude, proof-of-concept". On the fixture pages, does abliteration change JA→EN output beyond the refusal lines? Do these variants even refuse less on manga content than the base models, or do the bases not refuse under the translation prompt anyway? Licence tags also need fixing before shipping: HauhauCS Gemma 4 is tagged `gemma` over an Apache-2.0 base.
11. **Sampler per model.** Qwen3.5 recommends `presence_penalty=2.0` and temperature 1.0 for non-thinking text; Ministral recommends temperature <0.1. Yomu uses one global profile (#192), adding per-model overrides only once measurements force two models apart (#139). Do Qwen3.5 and Ministral under the shared profile produce loops (the ADR-0013 run-on failure) that force that split?
12. **Real `totalMem` on the S23.** What does `ActivityManager.MemoryInfo.totalMem` report? All headroom figures here assume 8 GiB.

## Sources

All accessed 2026-09-16.

**Local (this repository, vendored llama.cpp at `37b53fd4545847188fdad29e38ba57875efc8228`)**
- `app/src/main/java/com/yomu/app/translation/LlmModelCatalog.kt`
- `core/src/main/java/com/yomu/core/Constants.kt`, `core/src/main/java/com/yomu/core/TranslationSlot.kt`
- `app/src/main/java/com/yomu/app/service/ModelManager.kt`
- `ml/src/main/cpp/llama_jni.cpp` (lines 114-145, 180-192)
- `ml/llama.cpp/src/llama-arch.cpp`, `src/llama-chat.cpp`, `src/llama-context.cpp:3630-3660`, `src/llama-model.cpp:2578-2579, 2635-2638`, `src/llama-hparams.cpp:208-260`, `src/models/{qwen35,gemma4,muse-glimmer}.cpp`, `common/chat.cpp:1108-1193`
- `docs/adr/0013`, `0014`, `0015`
- `koharu/crates/koharu-translator/src/local/catalog.rs` (used only as a lead)

**Hugging Face API** (`/api/models/<repo>`, `/api/models/<repo>/tree/main`, `/api/models?author=…`) and files (`/resolve/main/config.json`, `README.md`, GGUF header range reads):
- https://huggingface.co/Qwen/Qwen3.5-0.8B · https://huggingface.co/Qwen/Qwen3.5-2B · https://huggingface.co/Qwen/Qwen3.5-4B · https://huggingface.co/Qwen/Qwen3-4B-Instruct-2507 · https://huggingface.co/Qwen/Qwen3-1.7B · https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct
- https://huggingface.co/bartowski/Qwen_Qwen3.5-0.8B-GGUF · https://huggingface.co/bartowski/Qwen_Qwen3.5-2B-GGUF · https://huggingface.co/bartowski/Qwen_Qwen3.5-4B-GGUF · https://huggingface.co/unsloth/Qwen3.5-2B-GGUF · https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF · https://huggingface.co/bartowski/Qwen_Qwen3-4B-Instruct-2507-GGUF
- https://huggingface.co/google/gemma-4-E2B-it · https://huggingface.co/google/gemma-4-E4B-it · https://huggingface.co/google/gemma-4-E2B-it/resolve/main/chat_template.jinja · https://huggingface.co/google/gemma-4-E2B-it-qat-q4_0-gguf · https://huggingface.co/google/gemma-4-E4B-it-qat-q4_0-gguf · https://huggingface.co/unsloth/gemma-4-E2B-it-qat-GGUF · https://huggingface.co/ggml-org/gemma-4-E2B-it-GGUF · https://huggingface.co/bartowski/google_gemma-4-E2B-it-GGUF
- https://huggingface.co/google/translategemma-4b-it (gated; card text and config read via the ungated mirror https://huggingface.co/Infomaniak-AI/vllm-translategemma-4b-it) · https://huggingface.co/mradermacher/translategemma-4b-it-GGUF · https://huggingface.co/unsloth/gemma-3-4b-it
- https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512 · https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512-GGUF · https://huggingface.co/bartowski/mistralai_Ministral-3-3B-Instruct-2512-GGUF
- https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct · https://huggingface.co/unsloth/Llama-3.2-3B-Instruct · https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF · https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF
- https://huggingface.co/meta-models/Muse-Glimmer-30B · https://huggingface.co/meta-models/Muse-Glimmer-30B-GGUF · https://huggingface.co/microsoft/wham · https://huggingface.co/microsoft/MuseVLA
- https://huggingface.co/zai-org/glm-edge-1.5b-chat (+ `/LICENSE`) · https://huggingface.co/zai-org/glm-edge-1.5b-chat-gguf · https://huggingface.co/zai-org/glm-edge-4b-chat-gguf · https://huggingface.co/zai-org/GLM-5.3-Flash · https://huggingface.co/zai-org/GLM-4.7-Flash · https://huggingface.co/zai-org/GLM-OCR
- https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B · https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF · https://huggingface.co/deepseek-ai/DeepSeek-V4-Flash · https://huggingface.co/deepseek-ai/DeepSeek-V4.1-Flash
- https://huggingface.co/moonshotai/Kimi-K3 · https://huggingface.co/moonshotai/Kimi-Linear-48B-A3B-Instruct · https://huggingface.co/moonshotai/Kimi-VL-A3B-Instruct
- https://huggingface.co/MiniMaxAI/MiniMax-M3 · https://huggingface.co/MiniMaxAI/MiniMax-M2.7 · https://huggingface.co/MiniMaxAI/MiniMax-H3
- https://huggingface.co/tencent/Hy-MT2-1.8B (+ `/LICENSE.txt`) · https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF
- https://huggingface.co/HauhauCS/Qwen3.5-2B-Uncensored-HauhauCS-Aggressive · https://huggingface.co/HauhauCS/Qwen3.5-4B-Uncensored-HauhauCS-Aggressive · https://huggingface.co/HauhauCS/Gemma-4-E2B-Uncensored-HauhauCS-Aggressive · https://huggingface.co/huihui-ai/Huihui-Qwen3.5-2B-abliterated · https://huggingface.co/huihui-ai/Huihui-Qwen3.5-4B-abliterated · https://huggingface.co/huihui-ai/Huihui-gemma-4-E2B-it-qat-q4_0-unquantized-abliterated-GGUF · https://huggingface.co/mradermacher/Huihui-Qwen3.5-2B-abliterated-GGUF · https://huggingface.co/mradermacher/Huihui-Qwen3.5-4B-abliterated-GGUF · https://huggingface.co/huihui-ai/Llama-3.2-3B-Instruct-abliterated

**Licences and reports**
- Gemma 4 licence (Apache 2.0): https://ai.google.dev/gemma/docs/gemma_4_license
- Gemma Terms of Use (last modified April 1, 2026): https://ai.google.dev/gemma/terms
- Gemma 4 Technical Report: https://arxiv.org/pdf/2607.02770
- TranslateGemma Technical Report: https://arxiv.org/pdf/2601.09012
- Ministral 3 paper: https://arxiv.org/pdf/2601.08584
- Hy-MT2 report (linked from card, not read): https://arxiv.org/pdf/2605.22064
- Llama 3.2 Community License: https://raw.githubusercontent.com/meta-llama/llama-models/main/models/llama3_2/LICENSE
- Llama 3.2 Acceptable Use Policy: https://raw.githubusercontent.com/meta-llama/llama-models/main/models/llama3_2/USE_POLICY.md
- Llama 3.2 model card: https://raw.githubusercontent.com/meta-llama/llama-models/main/models/llama3_2/MODEL_CARD.md

## Addendum: 9B tier and uncensored pins (2026-09-16)

Follow-up desk research, same day, same rules as above: primary sources only, vendor numbers **self-reported**, nothing run on a phone. Decisions this addendum takes as given: the curated ceiling is about 9B, shown only on 12 GB+ phones through the existing fit gate; licences must be Apache-2.0 or MIT; Yomu will format prompts through llama.cpp `common/chat` (Jinja); uncensored variants come from huihui-ai.

### Method changes and corrections to the sections above

- **Fit gate budgets.** `totalMem / 100 × 60` with integer division (`LlmModelCatalog.kt:130-133`). **12 GiB → 7,730,941,080 B (7.20 GiB). 16 GiB → 10,307,921,460 B (9.60 GiB).** A "12 GB" phone reports less than 12 GiB (unverified by how much), so the table also gives the smallest `totalMem` at which each model passes.
- **KV** uses the same formula and the same `swa_full = true` rule. For Qwen3.5, the gate does not count the DeltaNet recurrent state. The state is estimated from `llama_hparams::n_embd_r()`/`n_embd_s()` (`src/llama-hparams.cpp:208-254`) as f32 × linear layers: **19.3 MiB** for 0.8B/2B and **50.3 MiB** for 4B/9B. The gate underestimates by that much.
- **Host rule** for pins: first-party GGUF, else unsloth, else bartowski. That moves Qwen3.5 from bartowski (used above) to **unsloth**, because `Qwen/Qwen3.5-*-GGUF` still returns 401.
- **Template check now uses Jinja, not the heuristic.** I compiled the vendored engine (`common/jinja/*.cpp` + `common/json.cpp` + `common/unicode.cpp`, stubbing `ggml_abort`) into a harness that renders the `tokenizer.chat_template` read from each pinned GGUF header. The harness does not run the per-model handlers in `common/chat.cpp`, so I read those separately (see the Jinja table below).
- **Retracted: Hy-MT2-1.8B KV is 65,536 B/token, as the shortlist row first said.** This addendum claimed 131,072 off its own multiplication, which is the slip: `config.json` has `head_dim: 128` and `num_key_value_heads: 4` over 32 layers, and the GGUF agrees (`block_count = 32`, `attention.head_count_kv = 4`, `attention.key_length = attention.value_length = 128`), so 32 × 2 × 4 × 128 × 2 = 65,536, not 131,072. The fit is the original 2.09 GiB at 4096 tokens, not 2.34 GiB. Checked again against the pinned GGUF header on 2026-09-21 (#288), which is the figure the catalog entry carries.
- **Finding: in the vendored `common/chat`, `enable_thinking` defaults to `true`** (`common/chat.h:261`, `bool enable_thinking = true;`). `common/chat.cpp:920` passes it straight into the template. If Yomu does not set it to false:
  - Gemma 4 renders `<|turn>system\n<|think|>\n<turn|>`, which **turns thinking on**.
  - The official Qwen3.5 template opens `<think>\n`.
  
  **Yomu must pass `enable_thinking = false` explicitly.**

### A. 9B tier

"Need" is `size + 800 MiB + effective KV × ctx`, as the gate computes it (no recurrent state). "Min totalMem" is the smallest `totalMem` that passes at 4096 tokens.

| Model | Params / arch | Pinned GGUF (repo@sha · file · bytes · sha256) | KV B/token (formula → effective) | Need @2048 / @4096 | 12 GiB | 16 GiB | Min totalMem @4096 |
|---|---|---|---|---|---|---|---|
| **Qwen3.5-9B** | Card: "Number of Parameters: 9B"; HF safetensors total 9,653,104,368 (includes vision). 32 layers: "8 × (3 × (Gated DeltaNet → FFN) → 1 × (Gated Attention → FFN))". Attention is 16 Q / 4 KV heads, head_dim 256 | `unsloth/Qwen3.5-9B-GGUF@3885219b6810b007914f3a7950a8d1b469d598a5` · `Qwen3.5-9B-Q4_K_M.gguf` · 5,680,522,464 · `03b74727a860a56338e042c4420bb3f04b2fec5734175f4cb9fa853daf52b7e8` | 131,072 → **32,768** (8 full-attention layers) + 50.3 MiB state | 6.13 / 6.20 GiB | **fits** (+1.00 GiB) | **fits** (+3.40) | 10.33 GiB |
| **Gemma 4 E4B-it** | "4.5B effective (8B with embeddings)"; HF total 7,996,156,490. 42 layers, `num_kv_shared_layers: 18`, KV heads 2, head_dim 256 (SWA 512) / 512 (global) | `google/gemma-4-E4B-it-qat-q4_0-gguf@4b4a2c1d584be7264f87aac328a1bc739ce81b6c` · `gemma-4-E4B_q4_0-it.gguf` · 5,154,941,280 · `676c35070db6dbe52f93e9c864ee0fba4eddea94b9c875d9cb10daff453fbaee` (first-party) | 86,016 → **57,344** (24 KV-owning layers: 20 SWA + 4 global) | 5.69 / 5.80 GiB | **fits** (+1.40) | **fits** (+3.80) | 9.67 GiB |
| **Ministral 3 8B Instruct 2512** | "8.4B Language Model", "0.4B Vision Encoder"; HF total 8,918,026,716 (FP8 + BF16). 34 layers, GQA 32/8, head_dim 128, `sliding_window: null` | `mistralai/Ministral-3-8B-Instruct-2512-GGUF@0102285ad796bd99af90f58de616092e5630e970` · `Ministral-3-8B-Instruct-2512-Q4_K_M.gguf` · 5,198,911,904 · `33e7a72cf5e6e2cfc2f2847075acc013d68bba023e35310cef86b5cf8fdca761` (first-party) | **174,080** | 5.96 / 6.29 GiB | **fits** (+0.91) | **fits** (+3.31) | 10.48 GiB |
| *Ministral 3 14B Instruct 2512* | 40 layers, GQA 32/8, head_dim 128 | `mistralai/Ministral-3-14B-Instruct-2512-GGUF@74fac473c43357d7fb2671713608183cc72496d0` · `…-Q4_K_M.gguf` · 8,239,593,024 | 204,800 | 8.85 / 9.24 GiB | **no** (−2.04) | fits (+0.36) | 15.39 GiB |

None of the three fits at 8 GiB (Qwen3.5-9B −1.40, Gemma 4 E4B −1.00, Ministral 3 8B −1.49 GiB at 4096). Alternatives with the same verdicts:
- `bartowski/Qwen_Qwen3.5-9B-GGUF@182be2fd…` Q4_K_M: 6,169,341,984 B, needs 6.65 GiB, min `totalMem` 11.09 GiB. That is too tight for a real "12 GB" phone. Another reason to take unsloth.
- `unsloth/gemma-4-E4B-it-qat-GGUF@8c5a9e4fd5482e2be20fe0bf013b4c262a8f4265` · `gemma-4-E4B-it-qat-UD-Q4_K_XL.gguf` · 4,215,695,776 · `df0fd4ee07072c607c29a0a1cb4f98918426cca12f45a2776bdd6ee6d09a4de3`. It needs 4.93 GiB, so it still misses an 8 GiB phone by 0.13 GiB.

**Ministral 3 14B is out by the ~9B ceiling decision, not by the gate.** At a nominal 16 GiB the gate would pass it (+0.36 GiB at 4096). A phone reporting under 15.39 GiB would not.

**Per model.**
- **Qwen3.5-9B**
  - *Licence:* card front matter `license: apache-2.0`. The unsloth repo is `apache-2.0`, and the GGUF header says `general.license = apache-2.0`.
  - *Thinking:* "Qwen3.5 models operate in thinking mode by default". Disable it with `"chat_template_kwargs": {"enable_thinking": False}`. There is no `/nothink` ("does not officially support the soft switch of Qwen3").
  - *Translation evidence:* card "Multilingualism" table, self-reported: WMT24++ **72.6** (Qwen3.5-4B 66.6, GPT-OSS-20B 67.8, GPT-OSS-120B 74.4) and MMMLU 81.2. As noted above, WMT24++ is English→X.
  - *llama.cpp:* `qwen35` arch present (`src/models/qwen35.cpp`). The unsloth GGUF has `qwen35.block_count = 32` (no MTP block).
  - *Template:* the unsloth GGUF template is **not** the official one. It flips the default: it renders `<think>\n` only when `enable_thinking is true`, and the empty block otherwise. It also changes tool-argument iteration. With an explicit `enable_thinking=false`, the official and unsloth templates render identically: `<|im_start|>assistant\n<think>\n\n</think>\n\n`.
- **Gemma 4 E4B-it**
  - *Licence:* card "License: Apache 2.0" (links `gemma_4_license`). The google GGUF repo is `apache-2.0`, and the header says `general.license = apache-2.0`.
  - *Thinking:* "Thinking is enabled by including the `<|think|>` token at the start of the system prompt. To disable thinking, remove the token." E2B/E4B emit no empty thought block when thinking is off.
  - *Translation evidence:* no text-MT benchmark in the card. MMMLU 76.6% (card). Speech only: CoVoST ja→en BLEU 25.5 (tech report, cited above).
  - *llama.cpp:* `gemma4` arch present. The GGUF has `gemma4.attention.shared_kv_layers = 18`.
  - *Caveat:* the same PLE caveats as E2B apply (open questions 4-5 above).
- **Ministral 3 8B Instruct 2512**
  - *Licence:* "This model is licensed under the [Apache 2.0 License]" (card and GGUF card). Both repos are `apache-2.0`, and the header says `general.license = apache-2.0`.
  - *Thinking:* none. Reasoning is a separate `Ministral-3-8B-Reasoning-2512` model.
  - *Translation evidence:* card use case "Translation and content generation"; languages include "Chinese, Japanese, Korean" (GGUF `general.languages` includes `ja`, `zh`, `ko`). No translation benchmark. Base "Multilingual MMLU" 0.706 (card, self-reported).
  - *llama.cpp:* `mistral3` arch present (`src/models/mistral3.cpp`).
  - *Cost:* KV is the heaviest of the tier: 174,080 B/token, 664 MiB at 4096.

### B. Pin tuples for the ≤5B shortlist (ADR-0014)

The need column uses the same gate as above.

| Model | Repo @ commit sha | File | Bytes | LFS sha256 | KV B/token | Need @4096 · 8 GiB |
|---|---|---|---|---|---|---|
| Qwen3.5-0.8B | `unsloth/Qwen3.5-0.8B-GGUF@6ab461498e2023f6e3c1baea90a8f0fe38ab64d0` | `Qwen3.5-0.8B-Q4_K_M.gguf` | 532,517,120 | `bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517` | 12,288 | 1.32 GiB · fits |
| Qwen3.5-2B | `unsloth/Qwen3.5-2B-GGUF@f6d5376be1edb4d416d56da11e5397a961aca8ae` | `Qwen3.5-2B-Q4_K_M.gguf` | 1,280,835,840 | `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223` | 12,288 | 2.02 GiB · fits |
| Qwen3.5-4B | `unsloth/Qwen3.5-4B-GGUF@e87f176479d0855a907a41277aca2f8ee7a09523` | `Qwen3.5-4B-Q4_K_M.gguf` | 2,740,937,888 | `00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4` | 32,768 | 3.46 GiB · fits |
| Qwen3.5-9B | `unsloth/Qwen3.5-9B-GGUF@3885219b6810b007914f3a7950a8d1b469d598a5` | `Qwen3.5-9B-Q4_K_M.gguf` | 5,680,522,464 | `03b74727a860a56338e042c4420bb3f04b2fec5734175f4cb9fa853daf52b7e8` | 32,768 | 6.20 GiB · 12 GiB+ |
| Qwen3-4B-Instruct-2507 | `unsloth/Qwen3-4B-Instruct-2507-GGUF@a06e946bb6b655725eafa393f4a9745d460374c9` | `Qwen3-4B-Instruct-2507-Q4_K_M.gguf` | 2,497,281,120 | `3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597` | 147,456 | 3.67 GiB · fits |
| Gemma 4 E2B-it (first-party QAT) | `google/gemma-4-E2B-it-qat-q4_0-gguf@675cff42a74c774d6cb76f76d8eacb49b48c9b93` | `gemma-4-E2B_q4_0-it.gguf` | 3,349,516,256 | `fa401b55b07ee70a54c6dae3903c783a6e65064312529ea57175cb5f8dec6634` | 18,432 | 3.97 GiB · fits |
| Gemma 4 E2B-it (unsloth QAT) | `unsloth/gemma-4-E2B-it-qat-GGUF@66a399f68ddd113b06dff02fca9523e55465d11d` | `gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf` | 2,620,370,976 | `e531007218dfab990486a5de7676a6932d6ea8dea233d1f698d7c21cf8a16889` | 18,432 | 3.29 GiB · fits |
| Gemma 4 E4B-it (first-party QAT) | `google/gemma-4-E4B-it-qat-q4_0-gguf@4b4a2c1d584be7264f87aac328a1bc739ce81b6c` | `gemma-4-E4B_q4_0-it.gguf` | 5,154,941,280 | `676c35070db6dbe52f93e9c864ee0fba4eddea94b9c875d9cb10daff453fbaee` | 57,344 | 5.80 GiB · 12 GiB+ |
| Ministral 3 3B Instruct 2512 | `mistralai/Ministral-3-3B-Instruct-2512-GGUF@eb599d408350ea2bb60452cb86be7c7b2fc28227` | `Ministral-3-3B-Instruct-2512-Q4_K_M.gguf` | 2,147,023,008 | `9ed150d4367e68df0ac8e1540f6ddc65b42d0ee26378329d1ecbca60f93fc5f8` | 106,496 | 3.19 GiB · fits |
| Ministral 3 8B Instruct 2512 | `mistralai/Ministral-3-8B-Instruct-2512-GGUF@0102285ad796bd99af90f58de616092e5630e970` | `Ministral-3-8B-Instruct-2512-Q4_K_M.gguf` | 5,198,911,904 | `33e7a72cf5e6e2cfc2f2847075acc013d68bba023e35310cef86b5cf8fdca761` | 174,080 | 6.29 GiB · 12 GiB+ |
| Hy-MT2-1.8B | `tencent/Hy-MT2-1.8B-GGUF@a0c709d9fac510f2c807aa3af52872340dc37a4a` | `Hy-MT2-1.8B-Q4_K_M.gguf` | 1,133,080,448 | `dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699` | 65,536 | 2.09 GiB · fits |

No first-party GGUF exists for Qwen3.5 or Qwen3-4B-Instruct-2507 (`Qwen/…-GGUF` returns 401), so unsloth is used for both. Every repo in this table is `apache-2.0` and ungated per the HF API. `tencent/Hy-MT2-1.8B-GGUF` was last modified 2026-09-08, so pin by sha, not by `main`.

### C. Jinja rendering with `enable_thinking=false`

Rendered from the pinned GGUF headers with the vendored engine. The input was one user message `USER`, `add_generation_prompt=true`, `enable_thinking=false`.

| Template (source) | `common/chat.cpp` route | Rendered prompt | Notes |
|---|---|---|---|
| Qwen3.5 0.8B/2B/4B/9B (unsloth; one template for all four) | Qwen3-Coder handler (`chat.cpp:1204-1210`; matches `<tool_call>`/`<function=`/`<parameter=`); prompt comes from `common_chat_template_direct_apply_impl` | `<\|im_start\|>user\nUSER<\|im_end\|>\n<\|im_start\|>assistant\n<think>\n\n</think>\n\n` ✓ | Official 9B/4B templates (`tokenizer_config.json`) render the same with `false`. Omitting the flag gives `<think>\n` (official) or the empty block (unsloth). |
| Qwen3-4B-Instruct-2507 (unsloth) | generic | `<\|im_start\|>user\nUSER<\|im_end\|>\n<\|im_start\|>assistant\n` ✓ | No think block; the model is non-thinking only. |
| Gemma 4 E2B/E4B (google; the E2B and E4B templates are byte-identical) and unsloth (a different template, same output) | Gemma 4 handler (`chat.cpp:1186-1193`). Both templates carry the "OpenAI Chat Completions" marker, so no compatibility workaround fires. | `<bos><\|turn>user\nUSER<turn\|>\n<\|turn>model\n` ✓ | With `true` it prepends `<\|turn>system\n<\|think\|>\n<turn\|>`. A system message renders as `<\|turn>system\nSYS<turn\|>`. |
| Ministral 3 3B/8B (first-party; templates differ only in the model name inside the default system prompt) | Ministral 3 handler (`chat.cpp:1096-1100`), which converts system content to `[{type:text}]` blocks (checked: renders the same) | With a system message: `<s>[SYSTEM_PROMPT]SYS[/SYSTEM_PROMPT][INST]USER[/INST]` ✓ | **With no system message, the template injects Mistral's long Le Chat default system prompt** ("You are Ministral-3-8B-Instruct-2512 … You power an AI assistant called Le Chat …"). Yomu should always send its own system message. `enable_thinking` has no effect on this template. |
| Hy-MT2-1.8B (first-party GGUF = `chat_template.jinja` in the base repo, same rendering) | generic | `<｜hy_begin▁of▁sentence｜><｜hy_User｜>USER<｜hy_Assistant｜>` ✓ | This fixes the `hunyuan-vl` misdetection described above. A system message renders as `…sentence｜>SYS<｜hy_place▁holder▁no▁3｜><｜hy_User｜>…`, but the card says the model has no default system prompt. |

Not exercised: the handlers' grammar/parser outputs (`data.grammar`, PEG parsers) and how they interact with Yomu's own GBNF grammar.

### D. Uncensored pins (huihui-ai)

All five variants exist. huihui-ai publishes its own GGUF **only for the Gemma 4 QAT abliterations**. For Qwen3.5, the GGUF comes from mradermacher's static (non-`i1`) repos.

| Base | Abliterated repo @ sha (licence) | GGUF repo @ sha (licence) · file · bytes · sha256 | Need @4096 · fits |
|---|---|---|---|
| Qwen3.5-2B | `huihui-ai/Huihui-Qwen3.5-2B-abliterated@b2e291a65f29a9b148981fa5299caea5d35bd4c8` (apache-2.0) | `mradermacher/Huihui-Qwen3.5-2B-abliterated-GGUF@f36848fead3fdda244cf60195c46993d23183d4c` (apache-2.0) · `Huihui-Qwen3.5-2B-abliterated.Q4_K_M.gguf` · 1,270,809,024 · `aa25eea787afe56a097268f7ed3460cb623e1901d2e89cd2b654cabb42f80636` | 2.01 GiB · 8 GiB |
| Qwen3.5-4B | `huihui-ai/Huihui-Qwen3.5-4B-abliterated@5581467dfd52bf338c782006a6cdce05c42594be` (apache-2.0) | `mradermacher/Huihui-Qwen3.5-4B-abliterated-GGUF@4a5daa6fbefca5fe822dc65fcb95cc4576fa9720` (apache-2.0) · `Huihui-Qwen3.5-4B-abliterated.Q4_K_M.gguf` · 2,707,514,688 · `423f10b6ec2d99c3378143d7cd3b80eb4887b3ed92103103ac59173b404f4f7c` | 3.43 GiB · 8 GiB |
| Qwen3.5-9B | `huihui-ai/Huihui-Qwen3.5-9B-abliterated@05b9e7c9b978ba29bdb8f50a49c30e4b91183339` (apache-2.0) | `mradermacher/Huihui-Qwen3.5-9B-abliterated-GGUF@9f646d7eda193ddf2348134f3bff3d49eed7a2c6` (apache-2.0; header `general.license = apache-2.0`) · `Huihui-Qwen3.5-9B-abliterated.Q4_K_M.gguf` · 5,627,045,248 · `ea1858ef4dc4b648b8dbb44612962a0333e945060dd0545ac0f28d7c4416e4b3` | 6.15 GiB · 12 GiB+ |
| Gemma 4 E2B (QAT) | `huihui-ai/Huihui-gemma-4-E2B-it-qat-q4_0-unquantized-abliterated@8f3a91d4c94343d170d898b2b6ce9182dd7c84e1` (apache-2.0) | `huihui-ai/Huihui-gemma-4-E2B-it-qat-q4_0-unquantized-abliterated-GGUF@e38a3cdcf55879424c971d0961ea70b82870b989` (apache-2.0) · `Huihui-gemma-4-E2B-it-qat-q4_0-unquantized-abliterated-Q4_K.gguf` · 3,416,118,240 · `6bc1f421ba870b01a2efbb6904a28bda0ae3ccde57b18eb5e9203c3db05effe9` | 4.03 GiB · 8 GiB |
| Gemma 4 E4B (QAT) | `huihui-ai/Huihui-gemma-4-E4B-it-qat-q4_0-unquantized-abliterated@13b2735b8a50d6bed07f3c85a6a9011cca825f4b` (apache-2.0) | `huihui-ai/Huihui-gemma-4-E4B-it-qat-q4_0-unquantized-abliterated-GGUF@bc37dec4db35ea0fcad97be7a8c6b3f6a499616b` (apache-2.0) · `Huihui-gemma-4-E4B-it-qat-q4_0-unquantized-abliterated-Q4_K.gguf` · 5,302,272,352 · `64434f2da081f912729e5c4732def7303eb5244d3fee493b9675bc4e9af52d4c` | 5.94 GiB · 12 GiB+ |

- **huihui's "Q4_K" is Q4_K_M.** Both Gemma GGUF headers say `general.file_type = 15`, which is `LLAMA_FTYPE_MOSTLY_Q4_K_M` (`include/llama.h:132`). The tensors are F32/Q4_K/Q6_K plus one BF16 (`per_layer_model_proj`). mradermacher's Q4_K_M re-quants of the same QAT abliterations are 1 KB larger (`…E2B…Q4_K_M.gguf` 3,416,119,296 @`305ce5ce…`; `…E4B…Q4_K_M.gguf` 5,302,273,408 @`93a34a48…`), so there is no reason to prefer them.
- **Non-QAT alternatives also exist:** `huihui-ai/Huihui-gemma-4-E2B-it-abliterated` (v1) and `-abliterated-v2@2f7b0884…`, and `huihui-ai/Huihui-gemma-4-E4B-it-abliterated@03ce1f3a…`, all apache-2.0 and GGUF'd only by mradermacher. For example, `…E2B-it-abliterated-v2.Q4_K_M.gguf` is 3,427,874,400 B (`558d20ce…`) and `…E4B-it-abliterated.Q4_K_M.gguf` is 5,335,286,272 B (`d793116e…`). What v2 changes is not stated in what I read (unverified). The QAT variants pair with the first-party QAT bases, so they are the natural pins.
- **Metadata:** huihui's Gemma GGUFs have no `general.license` key; the repo tag is the only licence metadata. Their chat template differs from Google's by md5 but renders identically for the input above (thinking-off, current template marker present). The mradermacher Qwen3.5-9B template equals bartowski's (the official one), so it thinks unless `enable_thinking=false`. I did not read the templates of the mradermacher 2B/4B GGUFs.
- **Quality notes on the cards:** every huihui card, Qwen and Gemma alike, says "This is a crude, proof-of-concept implementation to remove refusals from an LLM model without using TransformerLens". The cards recommend "research, testing, or controlled environments, avoiding direct use in production or public-facing commercial applications". None reports any benchmark or translation quality before and after.

### E. HauhauCS `Q4_K_P`

- **What the card says:** "K_P ("Perfect") quants are HauhauCS custom quantizations that use model-specific analysis to selectively preserve quality where it matters most. Each model gets its own optimized quantization profile. … Fully compatible with llama.cpp, LM Studio, and any GGUF-compatible runtime — no special builds needed." Also: "All quants generated with importance matrix (imatrix)".
- **What the header shows.** I range-read `HauhauCS/Gemma-4-E2B-Uncensored-HauhauCS-Aggressive@da8593c3e407afcd3e7da94ff2d69d77e2a28a48` · `…-Q4_K_P.gguf` (3,450,277,824 B, sha256 `aa866c1e514468f3d0f33971679d63c11b7c9c47acddd1cc5785fc467e52c21d`):
  - `general.file_type = 15` (Q4_K_M).
  - Tensor types: F32 ×283, **Q4_K ×218, Q6_K ×99**, BF16 ×1. These are ggml types 0/12/14/30, all live entries in `ggml/include/ggml.h:390-432`.
  - So "K_P" is not a new ggml type. It is a Q4_K_M-family file with a heavier Q6_K share (my reading of the type counts; the author does not document the recipe).
- **Tensor-set difference.** The file has 601 tensors against 541 in the google and unsloth E2B GGUFs. The 60 extra are `attn_k`/`attn_v`/`attn_k_norm` for the 20 KV-shared layers. The vendored loader creates those with `TENSOR_NOT_REQUIRED` on layers without their own KV (`src/models/gemma4.cpp:73-88`). They are therefore accepted and counted, and do not trip "wrong number of tensors" (`src/llama-model-loader.cpp:1385-1391`). They are dead bytes in the file.
- **Verdict: loads, by static reading of the vendored source. Runtime unverified** (not executed).
- **Other issues:** the header has no `general.license`; the repo tags `license: gemma` over an Apache-2.0 base. The Qwen3.5 HauhauCS repos ship plain Q4_K_M, not K_P. HauhauCS is also not the chosen uncensored source.

### F. Hy-MT2-1.8B prompt

- **Chat template:** `<｜hy_begin▁of▁sentence｜>[{system}<｜hy_place▁holder▁no▁3｜>]<｜hy_User｜>{user}<｜hy_Assistant｜>`. There is no thinking switch. Card: "Note that our models do not have a default system_prompt."
- **Official default prompt** (card table "Default Translation"): "Translate the following text into `{target_lang}`. Note that you should **only output the translated result without any additional explanation**:\n\n`{source_text}`". The Chinese equivalent is "将以下文本翻译为 `{target_lang}`，注意只需要输出翻译后的结果，不要额外解释：". The card also says "both source_lang and target_lang should use the full language names".
- **Other first-party prompt types:** Terminology, Style, Personalization, **Delimiters** ("You must retain the exact same number of delimiters in the translation…"), **Structured Data 1**, and **Structured Data 2** ("[Background Information] … taking the provided background information into consideration").
  - Structured Data 1 reads: "Translate the user-facing text within the following `{format_type}` data into `{target_lang}` … You MUST preserve the original `{format_type}` data structure … NEVER translate or alter code tags, keys, properties…".
- **Single text or multi-segment:** the training example data (`train/data/example_data.jsonl`) and the quick-start show one source text per user turn. There is **no first-party statement about emitting an id-keyed multi-segment JSON response**. The closest first-party support is the "Structured Data 1" prompt, which asks the model to translate values inside a JSON-like structure while keeping keys, and "Delimiters". Whether the 1.8B holds Yomu's grammar-constrained page schema is untested. The Hy-MT2 report (arXiv 2605.22064) was not read.
- **Sampling:** card, for 1.8B and 7B: `"temperature": 0.7, "top_p": 0.6, "top_k": 20, "repetition_penalty": 1.05`. **This conflicts with** `generation_config.json` (`top_p: 0.8`, otherwise the same) and the GGUF header (`general.sampling.top_p = 0.8`, `temp = 0.7`, `top_k = 20`).
- **STQ note:** the GGUF card says "This gguf depends on our STQ kernel, which is released at PR #22836". The pinned Q4_K_M file contains only F32/Q4_K/Q6_K tensors, so the note does not bind it. It probably refers to the 2-bit/1.25-bit repos (unverified).

### G. Vendor sampling parameters (non-thinking mode)

| Model | temperature | top_p | top_k | min_p | penalties | Source |
|---|---|---|---|---|---|---|
| Qwen3.5-0.8B, -2B | 1.0 | 1.00 | 20 | 0.0 | presence 2.0, repetition 1.0 | Card "Non-thinking mode for text tasks". No `generation_config.json`. |
| Qwen3.5-4B, -9B | 0.7 | 0.8 | 20 | 0.0 | presence 1.5, repetition 1.0 | Card "Instruct (or non-thinking) mode for general tasks". The card's two lists disagree for "non-thinking … reasoning tasks" (0.95/20/1.5 vs 1.0/40/2.0). No `generation_config.json`. |
| Qwen3-4B-Instruct-2507 | 0.7 | 0.8 | 20 | 0 | presence "between 0 and 2 to reduce endless repetitions" (optional) | Card; `generation_config.json` agrees (0.7/0.8/20) |
| Gemma 4 E2B-it, E4B-it | 1.0 | 0.95 | 64 | — | — | Card "Use the following standardized sampling configuration across all use cases"; `generation_config.json` and GGUF `general.sampling.*` agree |
| Ministral 3 3B, 8B Instruct 2512 | "below 0.1" | — | — | — | — | Card "Recommended Settings"; `generation_config.json` has no sampling keys |
| Hy-MT2-1.8B | 0.7 | **0.6** (card) / 0.8 (config, GGUF) | 20 | — | repetition 1.05 | Card; `generation_config.json` |
| huihui-ai abliterations | — | — | — | — | — | No sampling guidance on the cards; inherit the base |

These spreads (temperature 1.0 vs <0.1; presence penalty 2.0 vs none) are wide enough to test #192's single global profile directly (see open question 11 above).

### Still unverified after this addendum

- On-device load and speed of every 9B-tier model; real `totalMem` of 12 GB and 16 GB phones.
- Runtime load of the HauhauCS K_P file (static reading only).
- Behaviour of the `common/chat` handler grammars/parsers alongside Yomu's GBNF grammar.
- Whether Hy-MT2-1.8B follows a multi-id JSON schema; which `top_p` Tencent actually intends.
- What `Huihui-gemma-4-E2B-it-abliterated-v2` changes; translation quality of any abliterated model.
- Ministral 3 8B per-language MMLU (the paper was not re-read for 8B).

### Sources (addendum)

All accessed 2026-09-16.
- Local, vendored llama.cpp `37b53fd45`: `common/chat.h:261`, `common/chat.cpp:355-372, 905-925, 1096-1210, 1222-1240`, `common/parsers/{qwen3-coder,ministral3,gemma4}.cpp`, `common/jinja/*`, `src/llama-hparams.cpp:208-254`, `src/models/gemma4.cpp:1-100`, `src/llama-model-loader.cpp:1385-1391`, `ggml/include/ggml.h:390-432`, `include/llama.h:119-132`; `app/src/main/java/com/yomu/app/translation/LlmModelCatalog.kt:55,130-133`.
- HF API (`/api/models/<repo>`, `/tree/main`), `config.json`, `generation_config.json`, `tokenizer_config.json`, `chat_template.jinja`, `README.md`, and GGUF header range reads for:
  - https://huggingface.co/Qwen/Qwen3.5-9B · https://huggingface.co/Qwen/Qwen3.5-0.8B · https://huggingface.co/Qwen/Qwen3.5-2B · https://huggingface.co/Qwen/Qwen3.5-4B · https://huggingface.co/Qwen/Qwen3-4B-Instruct-2507
  - https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF · https://huggingface.co/unsloth/Qwen3.5-2B-GGUF · https://huggingface.co/unsloth/Qwen3.5-4B-GGUF · https://huggingface.co/unsloth/Qwen3.5-9B-GGUF · https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF · https://huggingface.co/bartowski/Qwen_Qwen3.5-9B-GGUF
  - https://huggingface.co/google/gemma-4-E4B-it · https://huggingface.co/google/gemma-4-E2B-it · https://huggingface.co/google/gemma-4-E4B-it-qat-q4_0-gguf · https://huggingface.co/google/gemma-4-E2B-it-qat-q4_0-gguf · https://huggingface.co/unsloth/gemma-4-E4B-it-qat-GGUF · https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF · https://huggingface.co/unsloth/gemma-4-E2B-it-qat-GGUF
  - https://huggingface.co/mistralai/Ministral-3-8B-Instruct-2512 · https://huggingface.co/mistralai/Ministral-3-8B-Instruct-2512-GGUF · https://huggingface.co/mistralai/Ministral-3-14B-Instruct-2512 · https://huggingface.co/mistralai/Ministral-3-14B-Instruct-2512-GGUF · https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512 · https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512-GGUF
  - https://huggingface.co/tencent/Hy-MT2-1.8B (+ `train/data/example_data.jsonl`) · https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF
  - https://huggingface.co/huihui-ai/Huihui-Qwen3.5-2B-abliterated · https://huggingface.co/huihui-ai/Huihui-Qwen3.5-4B-abliterated · https://huggingface.co/huihui-ai/Huihui-Qwen3.5-9B-abliterated · https://huggingface.co/mradermacher/Huihui-Qwen3.5-2B-abliterated-GGUF · https://huggingface.co/mradermacher/Huihui-Qwen3.5-4B-abliterated-GGUF · https://huggingface.co/mradermacher/Huihui-Qwen3.5-9B-abliterated-GGUF
  - https://huggingface.co/huihui-ai/Huihui-gemma-4-E2B-it-qat-q4_0-unquantized-abliterated · https://huggingface.co/huihui-ai/Huihui-gemma-4-E2B-it-qat-q4_0-unquantized-abliterated-GGUF · https://huggingface.co/huihui-ai/Huihui-gemma-4-E4B-it-qat-q4_0-unquantized-abliterated · https://huggingface.co/huihui-ai/Huihui-gemma-4-E4B-it-qat-q4_0-unquantized-abliterated-GGUF · https://huggingface.co/huihui-ai/Huihui-gemma-4-E2B-it-abliterated-v2 · https://huggingface.co/huihui-ai/Huihui-gemma-4-E4B-it-abliterated · https://huggingface.co/mradermacher/Huihui-gemma-4-E2B-it-abliterated-v2-GGUF · https://huggingface.co/mradermacher/Huihui-gemma-4-E4B-it-abliterated-GGUF · https://huggingface.co/mradermacher/Huihui-gemma-4-E2B-it-qat-q4_0-unquantized-abliterated-GGUF · https://huggingface.co/mradermacher/Huihui-gemma-4-E4B-it-qat-q4_0-unquantized-abliterated-GGUF
  - https://huggingface.co/HauhauCS/Gemma-4-E2B-Uncensored-HauhauCS-Aggressive · https://huggingface.co/HauhauCS/Qwen3.5-2B-Uncensored-HauhauCS-Aggressive
  - HF search: `/api/models?author=huihui-ai&search=Qwen3.5`, `…search=gemma-4`, `?author=mradermacher&search=Huihui-…`, `?author=bartowski&search=Huihui-…`
