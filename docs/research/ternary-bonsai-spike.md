# Ternary-Bonsai 4B/8B and Bonsai 2 27B as Experimental deliverables (spike #299)

Research and desktop runs, 2026-09-18. Nothing here was run on a phone. The llama.cpp source is the pinned submodule commit `5266f24` (`git ls-tree HEAD ml/llama.cpp`), read with `git show 5266f24:<path>`. The local checkout is at `37b53fd`, so line numbers below refer to `5266f24`. Model facts come from PrismML's Hugging Face cards, the HF API, and the GGUF headers themselves (fetched with HTTP range requests and parsed). All PrismML benchmark numbers are **self-reported**.

## Questions (from #299)

1. Do the ternary `Q2_0` GGUFs for Ternary-Bonsai-4B and 8B load in the pinned llama.cpp, and which backend path runs? (step 1)
2. Do they translate well enough, compared with Qwen2.5 1.5B Q4_K_M and Qwen3-4B-Instruct-2507 Q4_K_M? (step 2)
3. Does Bonsai 2 27B load in the pinned llama.cpp? If not, what would it need? (step 3)
4. How fast are they? (step 4, on the S23)
5. What are the pin tuples for anything that passes? (step 5, ADR-0014 format)

## Short answer

- **The file the issue names does not load.** `Ternary-Bonsai-4B-Q2_0.gguf` (1.07 GB, the card's "recommended" file) stores **group-128** blocks under type id 42. Mainline `Q2_0` (id 42) uses **group 64**. The pinned build rejects it: `tensor 'blk.0.attn_k.weight' has offset 103145184, expected 109211936`. The same repos also ship a **`Q2_0_g64.gguf`** file, which **loads and runs** on the pinned build (4B: 1,137,806,656 B; 8B: 2,310,125,920 B). So the catalog would use the g64 files, which are about 6% larger than the issue's table says.
- **On desktop CPU, a 1.1 GB ternary 4B is slower than a 2.5 GB Q4_K_M 4B.** Built with Yomu's Android CPU flags (`armv8.2-a+dotprod`, 4 threads, M4 Pro), prompt processing ran at 33 tok/s for Bonsai-4B `Q2_0` against 117 tok/s for Qwen3-4B-Instruct-2507 Q4_K_M. Generation ran at 28 against 49 tok/s. The prompt-processing gap (3.5×) matters most for page calls. This supports the issue's repack concern: `Q2_0` has no repacked kernel, and `Q4_K` does.
- **Bonsai 2 27B cannot load in the pinned llama.cpp.** Its tensors use types 142 (`PQ2_0`) and 143 (`PTQ1_0`), and the pinned build stops at `invalid ggml type 143. should be in [0, 43)`. Its architecture string is `qwen35`, which the pinned build *does* know, so the issue's "no Qwen3.8 architecture" point does not apply. It also needs a runtime Hadamard activation transform that mainline does not have. **Reject** it, as the issue expected.
- **Translation: one 4-line smoke test, not the step-2 comparison.** Both Bonsai models produced fluent English with meaning errors. So did Q4_K_M Qwen3-4B-2507 on the same lines. That is not enough evidence either way.
- **Licence caveat.** Every Bonsai repo ships a `NOTICE.txt`. Under Apache-2.0 §4(d) its contents travel with redistribution. ADR-0017 names "a Notice file" as a condition that disqualifies a licence. Someone needs to decide whether that applies here.
- **Recommendation:** do not add 4B/8B yet. If step 2 (the fixture-page comparison) is still worth running, run it on the **g64** files. Admission would need translation that is clearly better than Qwen3-4B-2507, since the speed case is gone on desktop. Reject Bonsai 2 27B now.

## Decision (2026-09-18)

- **Bonsai 2 27B is rejected.** It needs the Prism fork's private types and runtime, thinks by default, and exceeds ADR-0017's ~9B ceiling. Revisiting it would need mainline support for its formats and an ADR-0017 amendment.
- **Ternary-Bonsai 4B and 8B are added as Experimental deliverables** (#301), using the `Q2_0_g64` files, so readers can choose them. The Apache-2.0 NOTICE is not treated as disqualifying; #301 clarifies ADR-0017 accordingly.
- **Steps 2 and 4 move to the phone check** (#302): the fixture-page side-by-side against Qwen2.5 1.5B and Qwen3-4B-2507, and the Galaxy S23 speed benchmark. Each deliverable keeps or loses its place on that verdict.

## Detailed findings

### 1. `Q2_0` in the pinned llama.cpp is group-64, not the cards' group-128

- The pinned tree defines `GGML_TYPE_Q1_0 = 41`, `GGML_TYPE_Q2_0 = 42` and `GGML_TYPE_COUNT = 43` (`ml/llama.cpp/ggml/include/ggml.h:431-433` @5266f24). `LLAMA_FTYPE_MOSTLY_Q2_0 = 41` (`include/llama.h:158`).
- The block is `#define QK2_0 64`: an fp16 scale plus 16 bytes of 2-bit codes, which is 18 B per 64 weights, or 2.25 bpw (`ggml/src/ggml-common.h:187-192`). gguf-py agrees: `Q2_0: (64, 2 + 16)` (`gguf-py/gguf/constants.py:5718`).
- Dequantization is `(q - 1) * d`, with codes `00=-1, 01=0, 10=+1, 11=+2` (`ggml/src/ggml-quants.c:439-457`). That covers ternary weights, with one code left over.
- The type landed in mainline in `bec4772f6`, "Add Q2_0 quantization: type definition and CPU backend (#24448)", dated 2026-07-07. It added CPU and ARM NEON code only (`git show --stat bec4772f6`).
- The ARM NEON dot product exists (`ggml/src/ggml-cpu/arch/arm/quants.c:222-290`, "group 64: one Q2_0 block (64 weights) maps to two Q8_0 blocks") and is wired in `ggml/src/ggml-cpu/ggml-cpu.c:235-236`. It uses `ggml_vdotq_s32`, so Yomu's `+dotprod` build gets the native instruction.
- The PrismML cards describe the format as "GGUF Q2_0 g128 … One 128-element block is 34 bytes … 2.125 bits/weight". They also say "Q2_0 is not yet in mainline `llama.cpp`. Use our fork" (https://huggingface.co/prism-ml/Ternary-Bonsai-4B-gguf/blob/a3eb42bafe873f9686bc97486c43b72ef7d75ec8/README.md, lines 42, 54-67). The "not in mainline" line is out of date: mainline added `Q2_0` a month after the repos were last modified on 2026-06-10 (HF API `lastModified`).
- PrismML's fork now matches mainline too. On the fork's `prism` branch at `1a07bfa`, `QK2_0` is 64 and the g128 formats have their own type ids: `GGML_TYPE_PQ2_0 = 142`, `GGML_TYPE_PTQ1_0 = 143 // Prism-private ternary, group 128`, `QK_PQ2_0 128` (https://github.com/PrismML-Eng/llama.cpp/blob/prism/ggml/include/ggml.h lines 431-437; https://github.com/PrismML-Eng/llama.cpp/blob/prism/ggml/src/ggml-common.h lines 192, 202, 214).

**What each file in the 4B repo holds.** I parsed each GGUF header and measured the byte gaps between consecutive tensors:

| File | Tensor type id | Measured bpw | Bytes | Pinned `5266f24` |
|---|---|---|---|---|
| `Ternary-Bonsai-4B-Q2_0.gguf` | 42 | 2.125 (g128) | 1,074,969,344 | **Fails:** `gguf_init_from_reader: tensor 'blk.0.attn_k.weight' has offset 103145184, expected 109211936` |
| `Ternary-Bonsai-4B-Q2_0_g64.gguf` | 42 | 2.25 (g64) | 1,137,806,656 | **Loads** and generates |
| `Ternary-Bonsai-4B-PQ2_0.gguf` | 142 | 2.125 | 1,074,969,344 | **Fails:** `invalid ggml type 142. should be in [0, 43)` |

- The failure comes from the offset check in `ggml/src/gguf.cpp:776-785` and the type-range check in `ggml/src/gguf.cpp:714-716`.
- The 8B repo has the same three variants. Its g64 file loads.
- Both g64 headers declare `general.architecture = qwen3` and `general.file_type = 41`. Neither carries `prism.hadamard.*` keys. The 4B has 253 `Q2_0` tensors, including `token_embd.weight`, and 145 f32 tensors.

### 2. Architecture, context, KV cache (4B/8B)

The values below come from the GGUF headers.

- **4B:** `qwen3`, 36 blocks, 32 heads / 8 KV heads, key/value length 128, `context_length = 32768` (YaRN ×4 over 8192), 4.02 B params.
- **8B:** `qwen3`, 36 blocks, 32/8, 128, `context_length = 65536` (YaRN ×4 over 16384), 8.19 B params.
- **KV per token:** 36 × 2 × 8 × 128 × 2 = **147,456 B** for both. This matches the issue and Qwen3-4B-Instruct-2507 (`docs/research/phone-translation-llm-candidates.md:388`).
- **Fit gate** (`app/src/main/java/com/yomu/app/translation/LlmModelCatalog.kt:55,61`; 800 MiB overhead, 60%) at 4096 context:
  - 4B g64: 1,137,806,656 + 838,860,800 + 603,979,776 = 2.40 GiB. Fits the nominal 8 GiB budget of 4.80 GiB.
  - 8B g64: 3.50 GiB. Also fits.
- **Speed label** (ADR-0017: Fast ≤1.5 GB, Medium ≤3.5 GB; PR #297 `docs/adr/0017-experimental-curated-tier.md:53`): 4B g64 is **Fast** and 8B g64 is **Medium**. The measurements in §4 show the 4B label would be wrong.

### 3. Chat template and thinking

- The template embedded in the 4B/8B GGUFs never tests `enable_thinking`. Its generation prompt is always `'<|im_start|>assistant\n<think>\n\n</think>\n\n'` (GGUF `tokenizer.chat_template`, the last block of the 4063-char template). That means Jinja rendering (#281) produces non-thinking prompts with or without the flag.
- The heuristic `llama_chat_apply_template` path Yomu uses today would likely detect ChatML and **omit** the empty think block. This is inferred from how the existing research doc classifies Qwen3 templates (`docs/research/phone-translation-llm-candidates.md:405`). I did not test it for Bonsai. So #281 is still the correct dependency.
- GGUF sampling defaults: `temp 0.5, top_k 20, top_p 0.85, min_p 0` (`general.sampling.*` in both headers). These are PrismML's own values, not Qwen3's.

### 4. Desktop measurements (pinned `5266f24`, CPU only, Apple M4 Pro)

I built from `git archive 5266f24` twice:

- **(a)** with default native flags;
- **(b)** with Yomu's Android CPU flags: `GGML_NATIVE=OFF`, `GGML_CPU_ARM_ARCH=armv8.2-a+dotprod` (`ml/src/main/cpp/CMakeLists.txt:12-13`), with Accelerate/BLAS off.

On the pinned build, `Q4_K` repacks to `q4_K_8x8` when i8mm is available and to `q4_K_8x4` with dotprod alone (`ggml/src/ggml-cpu/repack.cpp:4600-4612`). No `Q2_0` case exists anywhere in `repack.cpp`. `ggml_cpu_has_dotprod` and `ggml_cpu_has_matmul_int8` are compile-time checks (`ggml/src/ggml-cpu/ggml-cpu.c:3811-3833`). Build (b) is therefore the path a Yomu phone build takes.

`llama-bench -p 512 -n 128 -fa 1 -r 2`, build (b), 4 threads:

| Model | File size | pp512 tok/s | tg128 tok/s |
|---|---|---|---|
| Qwen3-4B-Instruct-2507 Q4_K_M (unsloth, pinned sha, sha256 verified) | 2.32 GiB | **117.2** | **49.1** |
| Ternary-Bonsai-4B `Q2_0_g64` | 1.05 GiB | 33.2 | 27.6 |
| Ternary-Bonsai-8B `Q2_0_g64` | 2.15 GiB | 17.7 | 15.2 |

Build (a) at 10 threads: Q4_K_M ran pp512 245 / tg128 72, and Bonsai-4B ran 76 / 59. PrismML's card reports 226 / 56 on the same CPU at 10 threads, but with **their fork** and the g128 file (4B card, lines 110-117). The fork's CPU kernels are evidently much faster at prompt processing than mainline's.

A desktop core is not a phone. The **relative** result still matters: with the kernels the pinned build has, "a smaller file" does not mean "faster". The whitepaper's phone numbers are MLX Swift on an iPhone 17 Pro Max, not llama.cpp on Android (https://github.com/PrismML-Eng/Bonsai-demo/blob/main/ternary-bonsai-8b-whitepaper.pdf, Table 4).

### 5. Translation smoke test (not step 2)

The prompt was a hand-written ChatML one, with 4 JA lines, greedy decoding and build (a). I did **not** use Yomu's page prompt or grammar, or the fixture pages.

| Source | Bonsai-4B g64 | Bonsai-8B g64 | Qwen3-4B-2507 Q4_K_M |
|---|---|---|---|
| お前、まだ生きてたのか！ | You're still alive, huh? | You're still alive! | Are you still alive?! |
| うるせぇ…こんなところで死ねるかよ。 | Ugh... dying here? | Ugh... can you die here? | Shut up... can you die in a place like this? |
| 先輩、私のお弁当食べてくれませんか？ | Senior, can you please eat my lunch? | Senior, can you eat my lunch? | Senior, can I borrow your lunch? |
| べ、別にあんたのために作ったんじゃないんだからね！ | No, I didn't make it for you! | B, it's not like I made it for you! | N-no, I didn't make it for you! |

All three models misread the rhetorical 死ねるかよ ("like I'd die in a place like this"). The output is coherent, not garbage, which rules out a broken kernel. It does not measure quality.

### 6. Published evidence on quality

- **4B card:** average 70.7 against Qwen3-4B's 77.1, about 92% (MMLU-R, MuSR, IFEval, GSM8K, HE+, BFCLv3). Evaluated with EvalScope + vLLM (4B README lines 125-134).
- **8B card:** 75.5 against 79.3, about 95% (https://huggingface.co/prism-ml/Ternary-Bonsai-8B-gguf/blob/c2aefbeb4b24469cd11579c3384b990404c17a30/README.md).
- **1.7B card:** 58.47 against 66.57. This is where the issue's "about 88%" comes from (https://huggingface.co/prism-ml/Ternary-Bonsai-1.7B-gguf/blob/983b5dec2ff16aab79990711ba0f828a499a7e6a/README.md).
- The whitepaper names the base models as Qwen3-8B/4B/1.7B and says "architectures are unchanged". It does **not** say how the ternary weights were produced (no training/QAT/PTQ description I could find). It also contains no multilingual, CJK or translation evaluation (whitepaper text extracted with PDFKit and searched for multilingual/Japanese/Chinese/Korean/translation/language).
- `Ternary-Bonsai-4B-unpacked` (the stated `base_model`) is an FP16 unpacking with no method description (https://huggingface.co/prism-ml/Ternary-Bonsai-4B-unpacked, sha `4485fae`).
- This confirms the issue's main risk: there is no CJK evidence of any kind.

### 7. Bonsai 2 27B

- The header is `general.architecture = qwen35`: 64 blocks, `full_attention_interval = 4`, 4 KV heads, key/value length 256, `context_length = 262144`. It also carries `prism.hadamard.*` keys (block size 1024, Sylvester–Walsh–Hadamard, 401 weight names).
- Tensor types are 143 (`PTQ1_0` file, `file_type = 143`) or 142 (`PQ2_0` file).
- The pinned build knows `qwen35` (`src/llama-arch.cpp:41`) but rejects both files: `tensor 'output.weight' has invalid ggml type 143. should be in [0, 43)` (PTQ1_0) and `… type 142 …` (PQ2_0). I tested with the first 24 MB of each file, since the type check runs while tensor info is read, before any data (`ggml/src/gguf.cpp:714-716`).
- Mainline at the newer `37b53fd` also has no type 142/143 (`GGML_TYPE_COUNT = 43`), so a submodule bump alone would not help.
- The card: "Stock llama.cpp will not run these files. It rejects `PQ2_0` and `PTQ1_0` as unknown types, and it loads `Q2_0` without any warning and produces garbage, because it has no Hadamard activation runtime" (https://huggingface.co/prism-ml/Ternary-Bonsai-2-27B-gguf/blob/6ed5e12bf84b7a63069882c91dd9e9218647d17b/README.md, lines 134-140). Mainline's `llama_mul_mat_hadamard` rotates the KV path (`src/llama-graph.cpp:2800-2836`). It is not the per-weight activation transform the file declares.
- **What it would need:** the PrismML fork (`prism` branch). That is outside the "pinned mainline" rule in #300.
- It is also a thinking model by default: "This is a reasoning model and it thinks by default" (27B card, line 167). Its published speeds are for GPUs and Apple laptops only (card, lines 172-204).
- **Sizes:** `PTQ1_0` is 5,946,648,928 B and `PQ2_0` is 7,206,168,928 B (HF API). KV per token is 16 attention layers × 2 × 4 × 256 × 2 = 65,536 B.
- **Verdict:** reject on format and runtime (it needs a fork), on thinking-by-default, and on ADR-0017's ~9B ceiling (27.36 B total params, card line 59).

### 8. Licence

- All three repos are `apache-2.0` and ungated per the HF API (`cardData.license`, `gated: false`), and the GGUFs declare `general.license = apache-2.0`. The base models Qwen/Qwen3-4B (sha `1cfa9a7`) and Qwen/Qwen3.8-27B (sha `1d4bf0f`) are both `apache-2.0` per the HF API.
- **Each Bonsai repo contains `NOTICE.txt`.** It gives the Prism ML copyright and "If you publicly deploy or redistribute this software, we would appreciate attribution such as: 'Created using Bonsai by Prism ML.'", plus the Qwen3 base attribution (https://huggingface.co/prism-ml/Ternary-Bonsai-4B-gguf/blob/a3eb42bafe873f9686bc97486c43b72ef7d75ec8/NOTICE.txt).
  - Apache-2.0 §4(d) requires a redistributor to include a readable copy of the attribution notices in a NOTICE file (https://www.apache.org/licenses/LICENSE-2.0#redistribution).
  - Yomu downloads the file from HF rather than bundling it, so whether Yomu counts as redistributing is a judgment call.
  - ADR-0017 lists "a Notice file" among the conditions that disqualify a licence (PR #297, `docs/adr/0017-experimental-curated-tier.md:39`). That rule seems aimed at licence terms, not at Apache repos that happen to ship a NOTICE. It needs an explicit decision.

## Pin tuples (ADR-0014 format; loadable files only)

The sha256 values are the HF LFS oids. They match `shasum -a 256` on the downloaded files.

| Model | Repo @ commit | File | Bytes | sha256 | KV B/token | Need @4096 · 8 GiB |
|---|---|---|---|---|---|---|
| Ternary-Bonsai-4B | `prism-ml/Ternary-Bonsai-4B-gguf@a3eb42bafe873f9686bc97486c43b72ef7d75ec8` | `Ternary-Bonsai-4B-Q2_0_g64.gguf` | 1,137,806,656 | `9d968b04a3c9a794897bcc744c8072fb6a061c0e42efd03c989401ddf8baef0c` | 147,456 | 2.40 GiB · fits |
| Ternary-Bonsai-8B | `prism-ml/Ternary-Bonsai-8B-gguf@c2aefbeb4b24469cd11579c3384b990404c17a30` | `Ternary-Bonsai-8B-Q2_0_g64.gguf` | 2,310,125,920 | `e17b298d84ee78797916ae5c2ecc8211469cc65cccfe3080cd9a9bb503fbc55e` | 147,456 | 3.50 GiB · fits |

**Not loadable on `5266f24`, recorded for completeness:**

- `Ternary-Bonsai-4B-Q2_0.gguf`: 1,074,969,344 B, `4e0bf8b737b0431552f8c2c97695ab7c0cb214c94bcdeb4f5f267e67ddf28b8b`
- `Ternary-Bonsai-8B-Q2_0.gguf`: 2,182,184,672 B, `3c8d70470a5d97e5a2b9410ddd899cb740116591462626c60cb2fead6448f60b`
- `Ternary-Bonsai-2-27B-PTQ1_0.gguf` @`6ed5e12bf84b7a63069882c91dd9e9218647d17b`: 5,946,648,928 B, `53107f530aa52eb00912263ab1ee29bd199261c87cd7b4ad4ca1318c1fe33ee3`

## Open questions / not verified

- **Step 2 was not done.** There was no side-by-side on the fixture pages with Yomu's page prompt and GBNF grammar. Whether Bonsai holds the ADR-0013 page schema is unknown.
- **Step 4 was not done.** Nothing ran on the Galaxy S23. The desktop ratios (Q4_K_M 3.5× faster at prompt processing, 1.8× at generation) are a guess at the phone result, not a measurement.
- I could not find how PrismML produces the ternary weights (training/QAT/PTQ) or what data it used. Neither the cards nor the whitepaper say.
- There is no CJK or translation evidence for any Bonsai model. None of the sources publish any.
- The heuristic-template behaviour for the Bonsai GGUFs (whether an empty think block is emitted) is inferred, not run.
- Whether the Bonsai `NOTICE.txt` triggers ADR-0017's "Notice file" exclusion is a policy question, not a fact to verify.
- Qwen/Qwen3.8-27B exists on HF (API sha `1d4bf0f`), but I did not read its card.

## Sources

- GitHub issues: #299, #300, #280 (https://github.com/artsaraiva/yomu/issues/299, /300, /280); ADR-0017 in PR #297 (branch `feat/280-adr-0017`, `docs/adr/0017-experimental-curated-tier.md`)
- Repo: `ml/llama.cpp` @`5266f24`: `ggml/include/ggml.h`, `include/llama.h`, `ggml/src/ggml-common.h`, `ggml/src/ggml-quants.c`, `ggml/src/ggml-cpu/arch/arm/quants.c`, `ggml/src/ggml-cpu/ggml-cpu.c`, `ggml/src/ggml-cpu/repack.cpp`, `ggml/src/gguf.cpp`, `gguf-py/gguf/constants.py`, `src/llama-arch.cpp`, `src/llama-graph.cpp`; commit `bec4772f6`
- Repo: `ml/src/main/cpp/CMakeLists.txt`, `app/src/main/java/com/yomu/app/translation/LlmModelCatalog.kt`, `docs/research/phone-translation-llm-candidates.md`, `docs/adr/0014-quantization-deliverables-and-revision-pinning.md`
- https://huggingface.co/prism-ml/Ternary-Bonsai-4B-gguf (README, NOTICE.txt, GGUF headers @`a3eb42b`)
- https://huggingface.co/prism-ml/Ternary-Bonsai-8B-gguf (README, GGUF header @`c2aefbe`)
- https://huggingface.co/prism-ml/Ternary-Bonsai-2-27B-gguf (README, NOTICE.txt, GGUF headers @`6ed5e12`)
- https://huggingface.co/prism-ml/Ternary-Bonsai-1.7B-gguf (README @`983b5de`)
- https://huggingface.co/prism-ml/Ternary-Bonsai-4B-unpacked
- HF API: `https://huggingface.co/api/models/<repo>?blobs=true` for the repos above, plus Qwen/Qwen3-4B and Qwen/Qwen3.8-27B
- https://github.com/PrismML-Eng/Bonsai-demo/blob/main/ternary-bonsai-8b-whitepaper.pdf
- https://github.com/PrismML-Eng/llama.cpp (`prism` branch @`1a07bfa`: `ggml/include/ggml.h`, `ggml/src/ggml-common.h`)
- https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF @`a06e946` (comparison file)
- https://www.apache.org/licenses/LICENSE-2.0 §4(d)
