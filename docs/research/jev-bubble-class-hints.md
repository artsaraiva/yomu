# Jev-style typed-question models as helpers for Yomu's page translation (research #350)

Research and desktop runs, 2026-10-16. [§6](#6-real-ocr-from-the-emulator-and-the-validator-idea) on real emulator OCR and the owner's validator idea was added on 2026-10-17. The issue asked whether TypeSafe's cloud Jev could label each bubble as speech, narration or SFX. At the owner's request, this note widens the question. Can a typed-question model of Jev's kind make Qwen2.5-1.5B's page translation better or faster in any way? It is the kind of model that reads a state and returns, in one pass, probabilities over options the caller defines. And can a local open-weight model of the same kind do the work on the phone after a llama.cpp pin bump?

**What was run, and on what.**

- **Nothing ran on a phone, and cloud Jev was not called.** This environment has no TypeSafe or Vercel AI Gateway key.
- **The desktop runs used an Apple M4 Pro (48 GiB), CPU only, 4 threads.** They used llama.cpp built in the session scratchpad at upstream `7182ef7` (2026-10-16). One load test also used Yomu's pin `7fe450e`. The repository's `ml/llama.cpp` submodule was not touched.
- **§2–§5 use my own transcription of the fixture pages.** I read the three pages by eye ([Appendix A](#appendix-a-hand-transcription-and-reference-labels)). [§6](#6-real-ocr-from-the-emulator-and-the-validator-idea) repeats the key runs on what Yomu's OCR actually read on an emulator ([Appendix D](#appendix-d-the-real-emulator-ocr)), and shows where the transcription flattered the models.
- **Every vendor figure is a vendor claim.**

## Questions, and where the issue's checks went

1. Which questions could a Jev-style model answer about a page that would improve Qwen2.5-1.5B's translation or speed it up?
2. Can Qwen2.5-1.5B answer the same questions itself, with no new model ("rung zero")?
3. Is there a local open-weight model of the same kind? What would a llama.cpp bump and the port into Yomu's JNI cost, and do the ADRs allow it?

| Issue check | Where it is answered |
|---|---|
| 1. Access and terms | [§1](#1-cloud-jev-access-and-terms) (cloud Jev, unchanged and condensed) |
| 2. Are the labels right? | [§5](#5-desktop-experiment) and, on real OCR, [§6](#6-real-ocr-from-the-emulator-and-the-validator-idea), with local models instead of Jev, plus the [Jev-key procedure](#procedure-for-someone-with-a-jev-key) |
| 3. Does the local model use them? | [§5](#5-desktop-experiment), with hand-written (best case) and model-made hints; the validator idea in [§6](#6-real-ocr-from-the-emulator-and-the-validator-idea) |
| 4. Cost of a second path | [§7](#7-cost-of-a-second-path) |
| 5. A local source | [§4](#4-local-jev-like-models) |

## Short answer

- **Cloud Jev stays closed.** ADR-0018 runs every stage of a page on the phone. Keeping a Yomu-held key secret would need a server, which ADR-0018 rules out. Signups have been paused since 2026-09-22. TypeSafe's own docs say CJK text is handled "not equally well". The details are in [§1](#1-cloud-jev-access-and-terms).
- **Typed hints did not visibly improve Qwen2.5-1.5B, even when they were perfect.** I wrote bubble-class, speaker-gender and sentence-continuation marks by hand and added them to the shipped batch prompt. They changed the wording, but not the quality, and the changes stayed within what a change of seed alone produces.
  - **Register marks were the only positive sign.** I tried them with a French target, which is not wired into the app yet (#337). Rough lines then came out with *tu* in both runs, where one unmarked run mixed in *vous*, and one polite line used *vous*.
- **Every kind of mark leaked into the reply.** Qwen copied the marks, translated them ("(rude)"), and invented marks for lines that had none. The shipped grammar allows all of this, and the leaked marks are text the reader would see.
- **The local decision models I ran could not produce the hints on Japanese manga text.**
  - **d1-omni-600M** answered "speech" for every bubble. That is 34 of 41, exactly the share of speech, with 0 of 7 non-speech bubbles right. With the page image it found the four captions on one page but mislabelled three speech bubbles as narration, and it took 45–101 s per page on CPU.
  - **Julia-1** answered "sfx" for every bubble on two pages, at about 0.99 confidence, and "narration" for every bubble on the third. Asked one bubble at a time, it said "sfx" for all 41, and for English control lines too.
  - **d1-3B** got 32 of 41, again with 0 of 7 non-speech bubbles right. Asked one bubble at a time, it reached 35 of 41 but still only 2 of 7, the two name-and-age captions. It took 16–56 s per page on desktop CPU, against 1.7–3.0 s for Qwen's whole translation call.
  - **Gender, register and translation checking:** none of the models was usable for any of these.
- **Rung zero is also weak.** Qwen2.5-1.5B labelling its own page got 23 of 41 classes, and it called every speaker on one page male. The extra pass cost 1.2–2.0 s per page on desktop CPU, two thirds to three quarters of the translation call itself.
- **The speed gain is small.** Yomu already drops text-free bubbles from the prompt. SFX lines cost about four reply tokens each, and decode time goes mostly to lines that run on to the 160-character cap, which no classifier prevents.
- **Real OCR moves the problem.** On the emulator, Yomu's OCR read 28 of the 41 hand bubbles exactly, and 4 more correctly but merged in pairs. It garbled 4 and missed 5. It also read 2 non-text areas: art, and a text-free balloon.
  - **Speech is read almost perfectly, but only 1 of the 7 non-speech bubbles arrives intact,** so a class labeller has little to work on.
  - **Six of the 36 prompt lines are garbage,** and Qwen turned each into confident wrong English.
- **The owner's validator works as an input gate, and it needs no new model.** The OCR decoder's own mean token probability separated all 6 garbage reads from the 30 readable ones on these pages: garbage scored 0.40–0.88, readable 0.976 or above. `OcrEngine` already computes the logits and then throws them away (`confidence = 1.0f`).
  - **Decision models and Qwen were worse gates.** d1-3B, d1-omni, Julia-1 and Qwen either overlapped, inverted, or flagged most lines, at up to 13 s per page.
  - **As an output check after Qwen, no judge caught garbage-in translations.**
- **llama.cpp support exists upstream, and the readout is what Yomu would port.**
  - **Upstream:** the `/v1/systemone` endpoint arrived with [ggml-org/llama.cpp#29818](https://github.com/ggml-org/llama.cpp/pull/29818) (`a4cb4c6`, 2026-10-02), 215 commits after Yomu's pin.
  - **API breaks:** the C API Yomu's JNI uses is unchanged up to the d1 support commit `a657f7e`. On current master, one Yomu line breaks.
  - **What Yomu would port:** the typed-question readout lives in the server tool, about 1,300 lines in `tools/server/server-decision.cpp`, not in libllama.
  - **Which models need the bump:** d1-3B and vLLM's d3 family use architectures the pin already knows. Laya, Julia-1 and d1-omni need libllama changes that came after the pin. The pinned build fails to load d1-omni-600M, but it loads and runs d1-3B's language model.
- **Conclusions:**
  - **(a) Cloud Jev: not worth it,** for access reasons.
  - **(b) Typed hints: not worth pursuing for Qwen2.5-1.5B's English output,** because perfect hints made no visible difference. The register hint might help non-English targets once #337 lands. It is worth one re-test with a prompt or grammar that stops leaks, not a build.
  - **(c) Local Jev-like path: no follow-up issue now.** The candidates failed on this input, and (b) shows even perfect answers would not help today. The [conclusion](#conclusion) names what would reopen it.
  - **Worth a follow-up issue of its own:** an OCR-confidence gate, using the probabilities `OcrEngine` already computes. It is cheaper than any Jev-like model. Its threshold comes from three pages and one emulator run.

## How a page reaches Qwen today (verified on `main`, `d0a4b7e`)

I traced the path myself rather than trust older notes.

1. **Detection:** `BubbleDetector.detect` returns boxes (`pipeline/src/main/java/com/yomu/pipeline/TranslationPipeline.kt:161`).
2. **OCR:** each box is cropped and read by `OcrEngine.extractText` (`:193`). OCR confidence is a constant: `confidence = 1.0f` (`pipeline/src/main/java/com/yomu/pipeline/ocr/OcrEngine.kt:93`). Nothing downstream can therefore tell a clean read from a garbled one, although the decoder's own probabilities can ([§6](#6-real-ocr-from-the-emulator-and-the-validator-idea)).
3. **Context assembly:** `ContextAssembler.assemble` groups the bubbles into panels and orders them right to left, then top to bottom (`TranslationPipeline.kt:207`; `pipeline/src/main/java/com/yomu/pipeline/context/ContextAssembler.kt:42-114`). Nothing else is added.
4. **Projection to the page input:** `TranslationEngine.translate` (`TranslationPipeline.kt:235`) projects the blocks into `TranslatablePage(panels: List<List<TranslatableBubble(bubbleId, sourceText)>>)` (`core/src/main/java/com/yomu/core/TranslationSlot.kt:12-14`; `pipeline/src/main/java/com/yomu/pipeline/translation/TranslationEngine.kt:154`).
   - Source text is cut at `MAX_SOURCE_CHARS = 300` (`:98`).
   - Bubbles without a letter or digit are dropped from the prompt (`carriesText`, `:50`, used at `:115`). That covers a lone `……` or `!?`.
5. **The prompt:** `LlamaTranslationBridge.buildBatchPrompt` writes a header line, then `[id] text` lines, with `---` between panels (`ml/src/main/java/com/yomu/ml/LlamaTranslationBridge.kt:225-234`).
   - **Grammar:** `buildBatchGrammar` pins one `[id] line` per bubble, in prompt order. Each line matches `[^\r\n\[]{1,160}` (`:245-249`).
   - **Chat template:** the JNI renders the GGUF's own Jinja template with one user message (`ml/src/main/cpp/chat_prompt.cpp`). There is no system message, because Qwen's catalog entry keeps the default `systemMessage = ""` (`app/src/main/java/com/yomu/app/translation/LlmModelCatalog.kt:64`). For Qwen2.5 that template adds its default "You are Qwen, created by Alibaba Cloud" system turn, which I confirmed with `/apply-template` on desktop.
6. **The target language is hard-coded:** every prompt builder says "English" (`LlamaTranslationBridge.kt:210-223, 225-229`). The settings preference exists (`app/src/main/java/com/yomu/app/ui/settings/SettingsViewModel.kt:33`), but no prompt reads it, and #337 is the open work to wire it.

**So the only context Qwen gets is the current page's own bubbles, in panels and reading order.** There is no session context: ADR-0013's #138 amendment deleted it, and no code mentions `sessionContext`. There are no speaker, class or OCR-quality signals. Any hint would need a new field on `TranslatableBubble` and a change to `buildBatchPrompt`, and probably to the grammar too (see the leaks in [§5](#5-desktop-experiment)).

## 1. Cloud Jev: access and terms

This section is unchanged in substance from the first version of this note.

- **Access.** The launch post (2026-09-15) says Jev is "available today in early access … bringing developers off the waitlist" ([launch post](https://typesafe.ai/blog/introducing-system-one-models-and-jev)). TypeSafe then [posted on X](https://x.com/typesafeai/status/2101786156572823624) "Jev is now available to everyone. No waitlist." (2026-09-20), and [two days later](https://x.com/typesafeai/status/2102281508950307159) said it had "to temporarily pause signups" (2026-09-22). I found no primary statement that signups have reopened. Jev is also served through [Vercel AI Gateway](https://vercel.com/docs/ai-gateway/sdks-and-apis/typesafe) in the same request shape, at the same price (`GET https://ai-gateway.vercel.sh/v1/models`).
- **API.** It is `POST https://api.typesafe.ai/v1/systemone`. A request carries a `state` and named `noul`, `choice` or `score` questions; a Choice takes up to 255 options. There are versioned model ids, and aliases that "can change without any change to your code" ([API reference](https://docs.typesafe.ai/api.md), [Models](https://docs.typesafe.ai/models.md)).
  - **Stability:** there is no formal deprecation policy yet ([Jev 1.14 post](https://typesafe.ai/blog/jev-1-14)). The Python SDK is pre-1.0 and made a breaking change on 2026-09-18 ([changelog](https://docs.typesafe.ai/sdk/python/changelog.md)).
  - **Images:** they work only behind a `TypeSafe-Danger-Zone-Images: true` header, and TypeSafe says they are not ready for production ([Images](https://docs.typesafe.ai/images.md)).
- **Price.** $0.042 per million input tokens, with output free (vendor). The docs' own examples imply about $0.00008 for a 12-bubble page, so cost is not the obstacle.
- **Data.** "We will not train or fine tune … on your prompts or other Input" ([Privacy Policy](https://typesafe.ai/legal/privacy-policy)). Retention is "as long as reasonably necessary", with a perpetual right to process Customer Data for telemetry, which includes "classifications" ([MCA](https://typesafe.ai/legal/mca) §4.1, §4.3). Zero data retention is for enterprise customers only ([Legal](https://docs.typesafe.ai/legal.md)). The service is hosted in the US.
- **Who holds the key.**
  - **Yomu:** a Yomu-held key must stay confidential (MCA §2.4). A key inside an APK is exposed to everyone who installs it, which is what TypeSafe's own SDK warns about with `dangerouslyAllowBrowser` ([SDK config](https://docs.typesafe.ai/sdk/javascript/api/interfaces/TypeSafeClientConfig.md)). Keeping it secret needs a server, and ADR-0018 says Yomu has "no server of its own".
  - **Each reader:** readers bringing their own key works under the terms but serves almost nobody.
- **Language.** "English is the primary training language … Other languages, including CJK scripts, are handled but not equally well" ([Models](https://docs.typesafe.ai/models.md)). Number handling is a documented weakness too ([Jev 1.13 jaggedness](https://docs.typesafe.ai/model-jaggedness/jev-1.13.md)).
- **ADR-0018:** "Every stage of a page, from capture through typesetting, runs on the reader's phone … The network is used only to download curated models." A Jev call on every page breaks that sentence whatever the call is for.

## 2. What a typed-question model could do for the page

The uses below are ideas to test, not a fixed list. "Reaches the pipeline" names where the answer would enter today's code ([previous section](#how-a-page-reaches-qwen-today-verified-on-main-d0a4b7e)). "Result" is what the desktop experiment in [§5](#5-desktop-experiment) found.

| Use | Questions | Reaches the pipeline | Helps | Plausibility before testing | Result |
|---|---|---|---|---|---|
| Bubble class | Choice per bubble: speech / narration / sfx | A mark in `buildBatchPrompt`, via a new `TranslatableBubble` field | Quality | ADR-0002 expected it to stop narration being "read as a character speaking". The narration cue is the box shape, which text cannot carry. | Perfect labels: no visible gain, and the marks leak. No model labelled it reliably. |
| Register / formality | Choice per bubble: polite / casual / rough | Same mark | Quality, for languages with a T–V distinction (pt-BR, es, fr, de) | Moot for English. The prompt is English-only until #337. | French: *tu* consistent on rough lines, one *vous* for a polite line; marks leak and get invented. pt-BR: no change in *você*. d1-omni: "polite" for everything; d1-3B: "casual" for every polite line. |
| Speaker gender / identity | Choice per bubble: male / female / unknown (or a speaker list) | Same mark | Quality: pronouns from zero-pronoun Japanese | ADR-0002 named a JA→EN zero-pronoun set as its acceptance gate, so the problem is real. But speaker identity comes from the art, not the text. | Perfect labels: no change to the wrong pronoun in tojime [17]. Models: d1-omni and Julia-1 said "female" for nearly everything, d1-3B mostly "unknown", Qwen "male" for everything. |
| Sentence continues across bubbles | Noul for each adjacent pair in a panel | Merge the two lines before the prompt, or mark the second | Quality | The whole page is already in one call, so Qwen sees the adjacent lines. TypeSafe's "Structure recovery" cookbook does the same for hard-wrapped text ([docs index](https://docs.typesafe.ai/llms.txt)). | Perfect mark on tojime [11]→[12]: no gain, mark leaked as "(continues 11)". Models missed the one real case (d1-3B 0.48, d1-omni 0.43, Julia-1 0.01). |
| Skip generation (speed) | Noul: "is this readable text worth translating?"; class = sfx | Drop the bubble from `TranslatablePage`, as `carriesText` already does for text-free bubbles | Speed, and, for garbled reads, quality | Small for speed. Text-free bubbles are already dropped, and SFX were 3 of 41 bubbles at about 4 tokens each of about 510 reply tokens. The garbled-read check is the owner's "validator" idea. | On real OCR ([§6](#6-real-ocr-from-the-emulator-and-the-validator-idea)), the OCR decoder's own mean token probability flagged all 6 garbage reads and none of the 30 readable ones. Decision models and Qwen did much worse. |
| Route hard bubbles to a bigger model | Score per bubble: difficulty; or Noul on the reply | A second LLM call | Quality, at a large speed cost | Poor on a phone. The slot holds "Exactly one LLM deliverable … at a time" (`CONTEXT.md`). A second model means a second resident model or a reload, against ~11.7 s pages on the S23 ([ADR-0013](../adr/0013-grammar-constrained-page-level-batch.md), #214 amendment). Vercel's [decision fallbacks](https://vercel.com/docs/ai-gateway/sdks-and-apis/typesafe) do this server-side. | Not built. |
| Verify / rerank translations | Noul per (Japanese, English) pair: "faithful?"; or Choice over N candidates | Retry flagged lines per line, or pick a candidate | Quality, at a speed cost (retries or N× generation) | TypeSafe has guardrail and re-ranking cookbooks ([docs index](https://docs.typesafe.ai/llms.txt)), and d1 lists "LLM-as-a-judge scoring" ([card](https://huggingface.co/LiquidAI/d1-3B)). The judge must read Japanese and English, the hardest of these tasks. | 11 pairs (5 wrong). d1-3B gave 0.35–0.69, d1-omni 0.57–0.66 and Julia-1 0.005–0.08, so none separated right from wrong. Qwen judging itself got 7 of 11 right and caught 2 of the 5 wrong lines. On real OCR ([§6](#6-real-ocr-from-the-emulator-and-the-validator-idea)), no judge caught translations of garbage input. d1-3B separated faithful from unfaithful clean lines (AUC 0.86) at 0.7 s per line. |
| Out-of-scope text (title, credit) | Noul: "is this a title or credit line?" | Drop it, as ADR-0007 already puts it out of scope | Speed, plus fewer garbled lines | A real case: bourei [3] is the title and author credit, which ADR-0007 excludes but the prompt still carries. | Asked only as part of the class question ("narration" in my labels). Text-only models did not separate it. |
| Name reading for the glossary (#327) | Choice over candidate readings found in code (furigana, katakana) | A glossary entry, not the prompt | Quality, across pages | Fits TypeSafe's "pre-parsed value extraction" pattern ([docs index](https://docs.typesafe.ai/llms.txt)). The fixture caption carries its own furigana (`烏丸 柩(からすま くるる)`), and every Qwen run still garbled it (Kusumaru, Kusumokuro, Kusumakura). | Not tested. It belongs to #327's design, not to this note. |

**Summary of the uses.** On today's English-only prompt, the uses that change what Qwen reads (class, gender, continuation) showed no visible gain even with perfect answers. The uses that cut work (skipping bubbles) save almost nothing. The uses that add a second model call (routing, reranking) cost more time than a phone page can spare. Register is the one hint with a visible effect, and it only matters after #337. Real OCR adds one use that does work: rejecting garbled reads before they reach Qwen. It works best with no Jev-like model at all ([§6](#6-real-ocr-from-the-emulator-and-the-validator-idea)).

## 3. Rung zero: Qwen2.5-1.5B as its own labeller

Causal decision models such as OpenJev, d1-3B and pplx-decider answer from "logits of one label token per option, read at the last prompt token" (`common/common.h`, `enum common_decision_type`, at upstream `a657f7e`). Yomu's JNI already reads logits for every sampled token (`llama_get_logits_ith`, `ml/src/main/cpp/grammar_sampler.cpp`). Qwen2.5-1.5B can therefore answer the same questions in the shipped runtime, with only another prompt.

I ran the equivalent: one grammar-constrained pass per page that must emit `[id] speech|narration|sfx` for every bubble, at temperature 0 (scratchpad `exp/qwen.py`, `self_labels`).

- **Class:** 23 of 41 right, with 3 of the 7 non-speech bubbles. It called plain dialogue "narration" across balloon-dense-dialogue ([6], [8], [9], [11]–[13], [15]), and called three of the four captions on bourei-p04 "speech" ([Appendix B](#appendix-b-bubble-class-answers-per-model)).
- **Gender (tojime, 16 lines):** "male" for all 16, which is right only where the speaker happens to be male (10 of 16).
- **Verification (11 pairs):** 7 right, catching 2 of the 5 wrong translations.
- **Cost:** a second prefill of the whole page plus about 3 tokens per bubble. On desktop CPU that was 1,202–2,040 ms per page, against 1,672–3,049 ms for the translation call itself. The phone ratio is not measured, but the label pass repeats the same prefill, so it would stay a large share.

The labels could come out of the translation call itself, as `[id] (label) text`, with no second prefill. But §5 shows labels in the prompt do not help, and labels Qwen writes alongside its own translation cannot add information it lacked while translating. **Rung zero fails on accuracy, and its cost is the same order as the translation call it is meant to improve.**

## 4. Local Jev-like models

### Candidates

All facts come from each model's own Hugging Face card or API entry, read on 2026-10-16. "llama.cpp" names the PR that added support. "Needs libllama change" says whether the model needs libllama code newer than Yomu's pin (`7fe450e`).

| Model | Params | Licence | GGUF for llama.cpp | Japanese evidence (vendor) | Images | Published speed (vendor) | llama.cpp · needs libllama change |
|---|---|---|---|---|---|---|---|
| `SupersonicLabs/Julia-1` (mmBERT-small) | 144M | Apache-2.0 | `ggml-org/Julia-1-GGUF` @`16fee17`, Q8_0 168,166,496 B | MASSIVE ja-JP scenario accuracy 82.2% (`metrics/accuracy-20260924.json`) | No | Not published | #29818 · yes (`modern-bert` decision head) |
| `convaiinnovations/laya` (ModernBERT-large) | 421M | Apache-2.0 | `ggml-org/Laya-GGUF` @`2226500`, Q8_0 449,397,600 B | None. The laya-multilingual card says to use that checkpoint "for anything that is not English" | No | Not published | #29818 · yes |
| `convaiinnovations/laya-multilingual` (mmBERT-base) | 322M | Apache-2.0 | **None for llama.cpp.** `mys/laya-multilingual-GGUF` uses an `ggmlc` architecture string that llama.cpp does not define. ONNX exports exist (`mizchi/laya-multilingual-onnx`, 646,870,871 B) | `ja` listed among 100+ languages | No | ~1.7 s for a 4,000-token input "on an Apple GPU" | — |
| `LiquidAI/d1-omni-600M` (LFM2.5 encoder) | 587M | LFM Open License v1.0 | `LiquidAI/d1-omni-600M-GGUF` @`0439714`, Q8_0 407,207,584 B + mmproj 262,791,232 B | `ja` listed | Images and audio | "We don't report inference numbers … early research release" | #30114 · yes (`src/models/lfm2.cpp` +291) |
| `vllm-sr/d3-edge` (Qwen3.5-0.8B base) | 0.59B incl. 0.10B vision | Apache-2.0 | `ggml-org/vllm-d3-edge-GGUF` @`753af7a`, Q8_0 806,232,416 B + mmproj 115,923,744 B | Inherits Qwen3.5; no Japanese figure found | Images, video | 6.6 ms per text request on an AMD MI325X GPU | #30383 · **no** (server-side only; `qwen35` is at the pin) |
| `vllm-sr/Decision-2.0-Kai-0.6B` (Qwen3-0.6B base) | 0.6B | Apache-2.0 | `vllm-sr/Decision-2.0-Kai-0.6B-GGUF` @`633669d`, Q4_K_M 400,918,688 B | None found | No | Not checked | #30378 · yes (new `vllm-decision2` graph) |
| `LiquidAI/d1-3B` (LFM2.5-VL-3B) | 3.12B | LFM Open License v1.0 | `LiquidAI/d1-3B-GGUF` @`bb1e436`, Q4_K_M 1,674,456,672 B + mmproj Q8_0 583,109,728 B | `ja` listed | Images | 30 ms per decision on an Apple M5 Pro (`mps`), 50 ms on a Jetson Orin Nano | #30110 · **no** (server and mtmd only; `lfm2` is at the pin) |
| `interfaze-ai/lev`, `jaredpalmer/kev-4b` (Qwen3.5-4B) | 4.2B | Apache-2.0 | `ggml-org/lev-GGUF`, `ggml-org/Kev-4B-GGUF`, Q4_K_M ≈3.0 GB | Cards declare `en` | No | Not checked | #29818 · probably small (that PR touched `qwen35.cpp`, +14 lines) |
| OpenJev, Clef, Clef-Flash, pplx-decider, Bespoke-Nimble-9B | 9B and up | CC-BY-NC-4.0 (OpenJev), Apache-2.0 (Clef, Clef-Flash) | ggml-org GGUFs of OpenJev, Clef and Clef-Flash are 6.5–19 GB at Q4_K_M; the others were not sized | — | Some | — | Too large for a phone beside Qwen |

**Licences.**

- **Apache-2.0** for Julia-1, Laya, laya-multilingual, d3-edge, Kai, lev and Kev (cardData `license`).
- **LFM Open License v1.0** for the Liquid d1 models. It makes "Commercial Use" conditional on staying below a "Threshold" of $10M annual revenue (§1, §5 of `LICENSE` at `LiquidAI/d1-3B@051bcc4`), and it carries Apache-style NOTICE terms.
- **CC-BY-NC-4.0** for OpenJev. A non-commercial licence does not fit a closed-source app (ADR-0001).

**Fit.**

- **Phone-sized:** Julia-1, Laya, d1-omni, d3-edge and Kai are under ~0.9 GB.
- **Too big beside Qwen:** d1-3B adds 1.67 GB to Qwen's 0.99 GB file, plus its own context.
- **The fit gate checks one model:** `canRunOnDevice` in `LlmModelCatalog` checks one deliverable at a time. Two resident models are a case it has never had to budget.

### llama.cpp support and the distance from Yomu's pin

- **The first commit with any support is `a4cb4c6`,** "llama, server: add /v1/systemone API (models: laya, julia-1, lev, openjev, kev) (#29818)", 2026-10-02. It is 215 commits after the pin `7fe450e` (2026-09-23), which is its ancestor (`git rev-list --count`).
- **Follow-ups, merged:**
  - nimble (#29844, `46ca246`) and clef (#29831, `99b9548`), both 2026-10-02/03;
  - d1-3B (#30110, `88dcc46`, 2026-10-07);
  - d1-omni-600M (#30114, `a657f7e`, 2026-10-08, 342 commits after the pin);
  - vLLM decision-2 (#30378) and d3 (#30383), 2026-10-12.
  - Open, unmerged: an H2O-Lightning model (#30361), GLiNER2.5-Decide (#30504), and model-agnostic decision support for ordinary LLMs (#29832).
- **What #29818 adds.** In its own words: "changes to libllama are minimal. Main part is `server_decision_context`". Concretely:
  - **Conversion:** new GGUF metadata (`<arch>.decision.*`) and a `systemone` chat template injected into the GGUF.
  - **libllama:** a decision head in the graph for the BERT-type models, and getters.
  - **common:** `common_get_decision_type`.
  - **Server:** the endpoint itself, `tools/server/server-decision.cpp` (681 lines in that PR, 1,290 at `7182ef7`).
  - **Models that need no libllama change:** d1-3B and d3 changed only `common/`, `tools/mtmd` and `tools/server` (PR file lists). Their architectures (`lfm2`, `qwen35`) already exist at the pin (`src/llama-arch.cpp`).
- **Pin test:** at `7fe450e`, `llama-server` fails on d1-omni-600M with `check_tensor_dims: tensor 'blk.16.ffn_gate.weight' not found`, as expected. The same build loads d1-3B (`lfm2`) and decodes from it (a plain `/completion` call returned text). For that model the pin's libllama is enough, and only the readout is missing.
- **C API breaks for Yomu's JNI.**
  - **What the JNI uses:** `ml/src/main/cpp/llama_jni.cpp`, `grammar_sampler.cpp` and `chat_prompt.cpp` use 53 distinct `llama_*` and `common_*` names (functions and types). These cover model and context setup, `llama_batch_get_one`, `llama_decode`, `llama_get_logits_ith`, the sampler chain, `llama_sampler_init_grammar`, `llama_memory_clear`, `llama_model_chat_template` and `common_chat_templates_init/apply`.
  - **Pin to `a657f7e`:** `include/llama.h` only adds (the `llama_batch_ext` API and `llama_process`). Its removed lines are two version constants and comments. `common/chat.h` keeps `common_chat_params.prompt` as `std::string`, along with the `add_generation_prompt`, `enable_thinking` and `force_pure_content` inputs Yomu sets. **Yomu's JNI would compile unchanged at the d1-omni commit.** I checked this by diff, not by building the app.
  - **Pin to master `7182ef7`:**
    - `ff026f8` "chat: replace output string with parts (#30389)", 2026-10-14, turns `common_chat_params.prompt` into a `common_chat_prompt` struct. `chat_prompt.cpp:41` (`return common_chat_templates_apply(templates, inputs).prompt;`) then needs `.prompt.str()`.
    - `llama_context_params.embeddings` was removed. Yomu does not set it.
    - The CMake options and targets Yomu uses (`LLAMA_BUILD_COMMON`, `LLAMA_BUILD_TOOLS`, `llama-common`) are unchanged.

### What moving the readout into Yomu would take

1. **A submodule bump.** For laya, Julia-1 and d1-omni it must reach at least `a657f7e`. d1-3B and d3 can stay on the current pin. Under AGENTS.md, "a llama.cpp bump also needs an on-device translation check", and a runtime change runs `scripts/run-speed-benchmark.sh` on a phone with the table pasted in the PR.
2. **Port the readout from `server-decision.cpp` into `ml/src/main/cpp`.** This is the bulk of the work. Per model type, it covers:
   - reading the `<arch>.decision.*` metadata (type, temperatures, label tokens) and the `systemone` template (`llama_model_chat_template(model, "systemone")`, already in the pinned API);
   - rendering the state and each question through that template (`common_chat_template` in llama-common);
   - one prompt per option order (`n_variants`);
   - reading the scores;
   - softmax with the type's temperature, averaging the variants, and the confidence formula (`format_answer`).

   There are two ways to read the scores. Label-logit types (d1-3B, d3, OpenJev) read `llama_get_logits_ith` at the last prompt token, which the JNI already does. Embedding types (Laya, Julia-1, d1-omni) need `llama_set_embeddings` and `llama_get_embeddings_ith` at marker rows (`send_decision` in `tools/server/server-context.cpp`). For one model type the port is a few hundred lines. Questions over one shared state also want prefix sharing, which the server does with `can_share_prompt`. Embedding types cannot share a prefix (`can_share_prompt` is false for them), so every question re-reads the whole state.
3. **Run a second model beside Qwen.** The JNI holds exactly one model in globals: `g_model`, `g_ctx`, `g_vocab`, `g_sampler`, `g_grammar`, `g_chat_templates` (`llama_jni.cpp:20-25`). A second model needs handles instead of globals, or a second native instance. On the Kotlin side it needs a new curated-model slot (`CONTEXT.md` lists detection, OCR and translation only), a catalog entry, and a fit budget for two resident models.
4. **Images**, if the bubble shape is the point, need `tools/mtmd`, which Yomu does not build (`LLAMA_BUILD_TOOLS OFF`, `ml/src/main/cpp/CMakeLists.txt:10`). That means another library and the image preprocessing.
5. **Prompt and grammar changes:** a field on `TranslatableBubble`, marks in `buildBatchPrompt`, and a grammar that stops marks leaking. Page recall must also key on whether hints arrived (`PageSettings`, `app/src/main/java/com/yomu/app/service/PageRecall.kt:9`), and page reports must record them.

**An alternative that avoids the bump:** Yomu already ships ONNX Runtime 1.26.0 for detection and OCR (`ml/build.gradle.kts:40`). ONNX exports exist for laya-multilingual and Decision-2.0-Kai-0.6B (`onnx-community/Decision-2.0-Kai-0.6B-ONNX`, 611,867,535 B). Step 2 would then move to Kotlin over ONNX instead of C++ over llama.cpp. This route is not tested here.

### ADR fit

- **ADR-0018.** A curated classifier downloaded once and run on the phone fits it for every local candidate: "The network is used only to download curated models." Images are processed on the phone too.
- **ADR-0007's "fabricated field" objection.** The objection reads: "Deriving the class heuristically at runtime from box geometry was rejected outright: it manufactures a field flowing into the translation prompt … A fifth, invented on purpose, is not a trade worth making."
  - **A model's answer is not a heuristic,** so the letter of the objection does not apply to any candidate.
  - **The spirit does apply:** a field that reads as wired but carries wrong values. On the fixture pages Julia-1 put "sfx" on dialogue at 0.99 confidence, and d1-omni put "speech" on every caption. For these candidates, the labels *would* be fabricated in that sense.
- **ADR-0007's revisit clause.** "Revisit only if a multi-class manga detector with published weights appears." None of these models is a detector, so any of them needs an ADR-0007 amendment. All have published weights, which Jev does not.
- **ADR-0015.** A model is chosen by "a manual look at output on the fixture pages". This note is that look, and it says no.
- **ADR-0001 / ADR-0017 (licences).** Apache-2.0 candidates are clean. The LFM licence's revenue threshold is a condition Yomu would have to accept. CC-BY-NC rules out OpenJev.

## 5. Desktop experiment

### Setup

- **Builds:** llama.cpp `7182ef7` (upstream master, 2026-10-16), built in the session scratchpad with the Android SDK's CMake 3.22.1 and Ninja (`cmake --build build --target llama-server`, 24 s). A second build at the pin `7fe450e` was used only to test model loading.
- **Translation model:** the app's pinned Qwen2.5-1.5B-Instruct Q4_K_M. That is `bartowski/Qwen2.5-1.5B-Instruct-GGUF@9eadc66`, 986,048,768 B, sha256 `1adf0b11…3370`, which matches `ModelManager.kt`'s checksum.
- **Server:** `llama-server --jinja -c 4096 -b 2048 -ub 512 -t 4 -ngl 0`. `-c 4096` is above the app's 2048 default. Every prompt here is under 400 tokens, so that does not change the output.
- **Prompt rendering:** each prompt went through `/apply-template` (one user message, as `chat_prompt.cpp` sends) and then `/completion`.
  - The sampler matched the app's chain and values (`rebuild_sampler`; `GenerationParams`): `samplers: [penalties, top_k, top_p, temperature]`, `temperature 0.2`, `top_k 40`, `top_p 0.9`, `min_p 0`, `repeat_penalty 1.0`, `repeat_last_n 64`, `n_predict 768`.
  - The grammar was built exactly as `buildBatchGrammar` builds it.
  - The phone draws a random seed per page (`seed = -1`). Here every arm ran with fixed seeds (42, then 7, 8 and 9), so that seed-to-seed variation is visible beside the arm-to-arm difference.
- **Decision models:** `d1-omni-600M` Q8_0 with its mmproj, `Julia-1` Q8_0 and `d1-3B` Q4_K_M with its Q8_0 mmproj. All ran on `llama-server` `/v1/systemone`, CPU, 4 threads, with checksums matching the HF LFS ids.
- **State and questions:** the state was `{"page": [{"id", "text"}, …]}` in reading order. Each question named its bubble by path (`` `page[3].text` ``), as TypeSafe's docs recommend. Each class question was also asked with its options reversed, and once more with the bubble's text alone as the state. For d1-omni and d1-3B, the whole page image was also tried as input.
- **Input text:** my transcription of the three fixture pages (`scripts/speed-fixtures/pages/`, OpenMantra, CC BY-NC 4.0), with my own labels (Appendix A). Real OCR errors are therefore absent, which flatters every model.
- **Scripts:** `exp/` in the session scratchpad. They are not part of the repo.

### Labels: who got them right

41 bubbles, of which 34 are speech, 4 narration and 3 sfx (my labels).

| Labeller | Class, all | Class, non-speech (7) | Desktop time per page |
|---|---|---|---|
| Qwen2.5-1.5B, grammar-constrained (rung zero) | 23/41 | 3/7 | 1.2–2.0 s |
| d1-omni-600M, page state | 34/41 (all "speech") | 0/7 | 1.8–6.3 s |
| d1-omni-600M, options reversed | 34/41 (all "speech") | 0/7 | 1.8–6.3 s |
| d1-omni-600M, one bubble per request | 17/41 | — | 3.4 s for all 41 |
| d1-omni-600M, page state + page image | 35/41 | 4/7 (the four captions; sfx 0/3) | 45–101 s |
| Julia-1, page state | 7/41 ("sfx" for every bubble on two pages, "narration" for every bubble on the third) | 7/7 by accident | 0.4–1.7 s |
| Julia-1, one bubble per request | 3/41 (all "sfx", ~1.0) | — | 0.9 s for all 41 |
| d1-3B, page state | 32/41 | 0/7 | 16–56 s |
| d1-3B, options reversed | 32/41 | 0/7 | 16–56 s |
| d1-3B, one bubble per request | 35/41 | 2/7 (the two name-and-age captions; sfx 0/3) | 0.66 s per bubble, 27 s for all 41 |
| d1-3B, page state + page image | 31/41 | 2/7 | 252–574 s |

**Julia-1 is not a broken install.** Its own card example works: "I was charged twice … refund" came back `billing` at 0.99. But it answered "sfx" at probability 1.0 for English control lines too ("Stop it! Not here!", "Tokyo, 1999. The rain had not stopped for three days."), even with plain option descriptions. The class question as worded is outside what it handles.

The other questions, asked on the same pages:

- **Speaker gender (tojime, 16 lines):**
  - d1-omni: 6/16. It said "female" for nearly every line, confidence 0.01–0.13.
  - Julia-1: 6/16, also "female" for everything.
  - Qwen: "male" for all 16, right on the 10 lines a man speaks.
  - d1-3B: 3/16, mostly "unknown".
- **Register (bourei and tojime):**
  - d1-omni: "polite" for every line, right on 3/8 and 1/18.
  - d1-3B: tojime's rough lines were mostly right, 6 of the 11 marked lines. But every polite bourei line came back "casual" (0/8), 死んでるんですよねぇ included, and the one polite tojime line came back "rough".
- **Continuation:** the one real split sentence (tojime [11] お前もしかして → [12] 女か?) scored 0.43 on d1-omni and 0.01 on Julia-1. Other pairs scored up to 0.85 on Julia-1 and 0.82 on d1-omni. d1-3B scored the split sentence 0.48, below pairs that are not one sentence (bourei [5]→[6] 0.63, tojime [12]→[13] 0.61).
- **Translation check (11 pairs, 5 wrong):**
  - d1-omni gave every pair 0.57–0.66; the right ones averaged 0.641 and the wrong ones 0.617.
  - Julia-1 gave every pair 0.005–0.08.
  - Qwen got 7 of 11 right.
  - d1-3B gave 0.35–0.69: the right pairs averaged 0.560 and the wrong ones 0.556.

### Does Qwen2.5-1.5B use the hints? (perfect hints first)

The prompts are the shipped prompt plus one sentence in the header and a mark after each tag. For example, the bubble-class arm:

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (speech), (narration) or (sfx) mark after its tag; it is not part of the text, so do not copy it into your reply.
[1] (speech) 死んでるんですよねぇ
[2] (narration) 「視える」人間ってヤツでして
```

Arms:

- `none`: the shipped prompt.
- `hand`: every bubble marked.
- `hand_nonspeech_only`: only narration and sfx marked, which is the form ADR-0002 described.
- On tojime only: speaker gender, and a continuation mark on [12].
- On bourei and tojime: register marks with a French and a Brazilian Portuguese target. The header names the language the way #337 plans to.

[Appendix C](#appendix-c-every-qwen25-15b-reply-in-full) quotes every reply in full. What I read in them:

- **Narration captions were not misread as speech, with or without marks.** In the `none` arm, bourei's caption lines [7] and [8] already come out as name-and-age captions ("The funeral requestor, Sakura Yume 19 years old"). With marks they say the same thing, with the same garbled names. The `none` arm puts quotation marks around every bourei line. With only narration marked, the narration lines kept their quotation marks and two speech lines lost theirs ([5], [6]), the opposite of what ADR-0002 hoped marks would do. The same arm turned [4] え? into a bare "?" in all four runs.
- **Class marks did not change the SFX renderings.** グッ became "GG"/"Goo" and プラーン became "PLAN"/"PLAIN"/"PLANE" with or without them.
- **The worst lines stayed wrong in every arm and every seed.** These are tojime [1] マフィア舐めんのも大概にしやがれ ("Mafia licking my face…", "It's a shame that the Mafia is licking its lips"), [4] 閉じ眼が用心棒だ? ("Closing my eyes is my guard dog") and [17] 閉じ眼でも器量が良けりゃ買い手は… (never "if she's pretty"). These are comprehension failures, not class or pronoun failures.
- **The gender marks did not fix [17]'s subject:** "If you close your eyes, you can still be a good quality…". The continuation mark did not merge [11] and [12]; "You too, maybe?" was followed by "Girl?".
- **Register marks with French did have a visible effect.**
  - On tojime, unmarked runs mixed *vous* imperatives into rough lines in one of two seeds ("Fermez les yeux" in [4] and [17], seed 7). Marked runs used *tu* throughout.
  - On bourei [5], the polite client question took *vous* ("Avez-vous vu … de votre tante ?") or a third-person polite form ("de la sœur de monsieur"). The meaning was wrong both times.
  - In Brazilian Portuguese, *você* appeared in both arms. Marks added "o Sr." / "a dona" to captions that had no mark.
  - **A side finding for #337:** Qwen2.5-1.5B's French and Portuguese on these pages are much weaker than its English. English words leak into Portuguese lines, as in "Olá, fantasma funeral house, good to meet you!".
- **Every hint kind leaked, in every run:**
  - marks copied into the reply: "(narration)", "(sfx)", "(male)", "(continues 11)";
  - marks translated: "(rude)" for "(rough)" in French;
  - marks invented for lines that carried none: "(polite)" on unmarked bourei captions [7] and [8], "(rough)" on [3];
  - in the `hand` arm on balloon-dense-dialogue, run-on text after the last line: `(narration) "Just because I collect balloons…"`.

  The shipped grammar's `line` rule excludes `[` but admits `(`. A real build would have to forbid marks in the reply. These are not hard to remove, but nothing gained from the hints would justify the work.
- **The extra length costs a little time.** Marks added 44–96 prompt tokens, and the copied marks lengthened the reply. Desktop call time rose from 1,672–3,049 ms (`none`) to 2,090–4,642 ms (`hand`). Part of that is the longer run-on in two runs.
- **Run-on was the largest time cost in every arm.** The last line of a page ran to the 160-character cap in most tojime runs and in several balloon runs, for example "Just a little...!  (pause)  (repeats) …". ADR-0013 reports the same class on 4 of 17 pages. No question to a decision model would prevent it. It is a grammar and sampling matter.

**Answer to the experiment's question.** On these three pages, hints that were perfect by construction did not make Qwen2.5-1.5B's English visibly better. Their effect was smaller than the effect of changing the seed. A labeller of any kind, cloud or local, is therefore not worth pursuing for the shipped English prompt. Register is the exception that might matter once #337 lands. It would be re-tested with a reply that cannot carry marks, after which it could become its own small question.

## 6. Real OCR from the emulator, and the validator idea

Added 2026-10-17.

**The inputs.**

- **Three page reports from the `sdk_gphone16k_arm64` emulator, one per fixture page.** Each holds the exact 1280×2856 frame the OCR read (`capture.png`), each bubble's detector box and OCR text (`page.json`), and Qwen's raw reply (`reply.txt`).
- **The run setup:** Photos in full screen with its toolbars hidden; the app on Qwen2.5-1.5B, Japanese → English; fresh runs (`recalled=false`).
- **A fourth run is set aside.** With Photos' toolbar visible, it read interface text as bubbles: `Sep15.2.50AN`, `oloEdit`, and the screen-share chip as `回00.37`. That is being filed as a separate bug and is not used here.
- **The emulator was only read**, with `adb exec-out run-as com.yomu.app cat`. Everything else ran on the same desktop as §5. Appendix D has every read, verdict and reply.

### What the real OCR reads

I mapped each OCR read to Appendix A by its text. Where the text was unclear, I drew the detector boxes on `capture.png` and decided by position.

| Page | Hand bubbles | Read exactly | Merged with a neighbour | Garbled | Missed (no box) | Reads with no hand bubble |
|---|---|---|---|---|---|---|
| balloon-dense-dialogue | 15 | 11 | 2 (as 1 read) | 1 | 1 | 0 (the `・・・・・・?` read is dropped as text-free) |
| bourei-p04 | 8 | 5 | 0 | 2 | 1 | 1 (the skyline strip, read as `前回!!これは!??`) |
| tojime-dense-action | 18 | 12 | 2 (as 1 read) | 1 | 3 | 1 (a `……` balloon, read as `ここ`) |
| **Total** | **41** | **28** | **4 (as 2 reads)** | **4** | **5** | **2** |

"Exact" ignores `…` coming out as `...`.

- **Speech is read well.** 27 of the 34 speech bubbles are read exactly, and 4 more arrive correctly but merged in pairs: なにあ然と… with ヘリウム…, and 腰の銃で…か? with あ?. One is garbled (ほぉ → `まお`) and two are missed (ただ空気いれた…, えっ).
- **Non-speech mostly does not arrive.** **Only 1 of the 7 non-speech bubbles arrives intact**, the 「視える」 caption.
  - **Garbled (3):** the title, the 烏丸 caption, and the camera text, read as `よくある`.
  - **Missed (3):** the client caption 葬儀依頼人…, グッ and プラーン.
- **On the bourei page:** the read `前回!!これは!??` is not the client caption. Its box sits on the skyline strip across the top panel, and the client caption has no box at all.
- **Six of the 36 lines that reach the prompt are garbage:** 4 garbled reads, plus 2 reads of art or of a text-free balloon. Qwen translated every one of them into confident English: "It's common", "Last time!! This!!?", "The general manager of the participants' comprehensive register, Nitori, 25 years old", "Welcome to the recruitment office, high school student", "Momosha" and "Here".

**Where the hand transcription flattered the models.**

- **The non-speech text §5 asked about mostly never arrives.** §5 gave every labeller all 7 non-speech bubbles as clean text. On the real page, 6 of the 7 are missing or garbled. Whatever a class labeller can do, it has almost nothing to work on.
- **No merges, art or garbage.** The real prompt has two merged pairs. Each asks Qwen to translate two bubbles as one line, as in tojime [1] ( "Let's see if you're the one that got my brain between the ribs?"). The prompt also has two reads of things that are not text.
- **Reading order.** Appendix A is in true reading order. The app's prompt is in `ContextAssembler`'s order. A Python port of `ContextAssembler.kt:66-114` reproduces the order in `reply.txt` on all three pages. On balloon-dense-dialogue that port puts the whole page in one panel, so the third-tier line 地球の写真… comes before the first-tier line なにあ然と….
- **What still holds from §5:** speech was read almost perfectly, so the finding that hints do not help Qwen with speech carries over to real OCR.

### The class labellers on real OCR

Each read was asked one bubble at a time (Qwen: one grammar-constrained pass per page). Scoring covers the 34 reads that came from a hand bubble; the 2 art and text-free reads have no class.

| Labeller | Hand text, §5 | Real OCR | Non-speech on real OCR (4 arrive: 1 clean, 3 garbled) |
|---|---|---|---|
| d1-3B | 35/41 | 30/34 | 1/4 (the garbled 烏丸 caption as narration) |
| d1-omni-600M | 17/41 | 15/34 | 2/4 |
| Julia-1 | 3/41 | 2/34 | 2/4 ("sfx" or "narration" for almost every read) |
| Qwen2.5-1.5B (rung zero) | 23/41 | 21/34 | 1/4 |

**Nothing changes.** Each model's accuracy on speech is what it was on hand text, and the non-speech class it was meant to find barely reaches the pipeline.

### Validator (a): an input gate between OCR and Qwen

This tests the owner's idea that a validator "could sit in between the ocr and the llm". Each of the 36 reads in the prompt was asked one yes/no question: "Is this readable Japanese, or a garbled OCR read?" Six of the reads are garbage and 30 are readable. The decision models got one request per read. Qwen got one grammar-constrained pass per page that answers `yes|no` for each line.

| Gate | Separates garbage from readable | Flags among the 30 readable reads, at a threshold that catches all 6 garbage reads | Cost |
|---|---|---|---|
| **OCR's own mean token probability** | **Completely (AUC 1.00).** Garbage 0.398–0.879, readable 0.976–1.000 | **0** (threshold 0.95) | About nothing: a softmax over logits OcrEngine already computes |
| OCR's minimum token probability | Almost (AUC 0.99); one merged clean read at 0.540 | 1 | About nothing |
| d1-3B | Overlapping (AUC 0.85): garbage 0.39–0.72, readable 0.58–0.91 | 12 | 0.93 s per read on desktop CPU, about 13 s for a 14-read page |
| d1-omni-600M | Weak (AUC 0.72) | 19 | 0.12 s per read |
| Julia-1 | Worse than chance (AUC 0.21); garbage scored ≥ 0.91 | 30 | 0.03 s per read |
| Qwen2.5-1.5B (rung zero) | Said "garbled" for 28 of 36 lines | Caught 3 of the 6, with 25 false flags | 2.6–3.5 s per page on desktop CPU |

AUC is the chance that a readable read scores above a garbage one.

**How the OCR-confidence baseline was measured.**

- **The model.** `OcrEngine` runs an ONNX VisionEncoderDecoder (`l0wgear/manga-ocr-2025-onnx@e8b27bb`). The decoder returns logits at every step; `OcrEngine` takes the argmax and returns `confidence = 1.0f` (`pipeline/src/main/java/com/yomu/pipeline/ocr/OcrEngine.kt:93`).
- **The files:** I pulled the app's own encoder, decoder and vocabulary from the emulator. Their checksums match `ModelManager.kt` (`f87668ae…`, `6b1fb216…`).
- **The replica:** I rebuilt `OcrEngine` in Python with onnxruntime, keeping the softmax probability of each chosen token:
  - the crop from `capture.png`, cut from the detector box as `CropBoundsCalculator.clamp` cuts it;
  - a bilinear resize to 224×224, scaled to [-1, 1];
  - decoding from start token 0, greedy, stopping at `[SEP]` within 128 steps.
- **Fidelity:** the replica reproduced the app's text exactly on 34 of 37 reads. The three it read differently are the three garbled bourei reads, which it also read as garbage, so their probabilities are the replica's. The likely cause is a small resampling difference from Android's `createScaledBitmap`.
- **Calibration:** the 0.95 threshold was chosen on these same 36 reads, and only 6 of them are garbage. It is a result to confirm, not a tuned value.

**What the app would do with a flag, and where.**

- **Compute it.** In `OcrEngine.runDecoder` (`:151`), keep `softmax(logits)[argmax]` at each step and return the mean as `OcrResult.confidence`. The field already exists and already travels with the text into `ConversationBlock`. The cost is one exponential pass over the 6,144 logits per step the decoder already returns, which is negligible beside the decode itself.
- **Act on it.** The cheapest action is to skip a low-confidence read where OCR results are kept (`TranslationPipeline.kt:195`, `if (result != null && result.text.isNotEmpty())`).
  - **A skipped read is never answered.** An unanswered bubble "gets no box and the reader sees the art under it" (`TranslationPipeline.kt:231`, #330). The reader then sees the original Japanese instead of confident wrong English.
  - **Do not route it through `carriesText`.** That filter renders the source text itself, and here the source is the garbled read.
  - **Keep the evidence.** The page report should still record the skipped read and its confidence.
- **Effect on the rest of the page** (Appendix D, three seeds each):
  - **The garbage lines go.** Without the 6 flagged reads, the 6 wrong English lines were gone in every run.
  - **One run-on goes with them.** In two of the three seeds, bourei's shipped prompt made Qwen write commentary after the garbled title ("(The line is incomplete and seems to be a joke or a typo) "ビエ" is likely a misspelling…"). Gating removed it.
  - **Other lines are unchanged.** The remaining lines did not visibly change. On bourei the run-on moved to the new last line ("[4] Yes... (pause) -_-; (sigh) …").
  - **Prompts shrink:** on bourei, from 161 to 107 tokens. Page times did not fall consistently, because run-on dominates them.
- **Alternatives to skipping:**
  - **Re-read the flagged crop,** padded or with the decoding the model ships with (next paragraph). That costs one more OCR decode per flagged bubble. The emulator's OCR stage took 1.4–2.7 s for 8–15 reads, about 0.15–0.2 s each.
  - **Keep the line with an "unsure" mark.** §5 shows that marks leak into the reply.

**A side finding about the OCR decoder: its own issue, not a validator.**

- **Settings mismatch.** The model's `config.json` says `decoder_start_token_id: 2` (`[CLS]`), and its `generation_config.json` asks for `num_beams: 4` and `no_repeat_ngram_size: 3` ([repo @e8b27bb](https://huggingface.co/l0wgear/manga-ocr-2025-onnx/tree/e8b27bbd3f424fe3877e0bda704d6a920e4f0a33)). `OcrEngine` starts from `BOS_TOKEN_ID = 0` (`[PAD]`, `OcrEngine.kt:22`) and decodes greedily.
- **Start token 2 in the replica changed four reads, all garbage ones; no clean read changed:**
  - the title became `ようこそビエ葬儀屋さん高良いと`, with 葬儀屋 right and 吉良 close;
  - the 烏丸 caption gained its furigana, `(からすまくるる)`;
  - the tojime `……` balloon came out as `・・・・・・`, which `carriesText` would then drop;
  - the skyline read stayed garbage.
- **Not tried:** beam search.

### Validator (b): an output check after Qwen

Each pair of real OCR Japanese and Qwen's line from `reply.txt` was asked: "Is `english` a faithful English translation of the Japanese manga line `japanese`?" My verdicts are in Appendix D: 22 faithful, 8 not, and 6 garbage-in. For garbage-in, the right answer is no.

| Judge | Faithful vs unfaithful (clean input) | Garbage-in caught? | Cost |
|---|---|---|---|
| d1-3B | AUC 0.86 (faithful mean 0.786, unfaithful 0.572) | **No.** Garbage-in mean 0.789, as high as faithful (AUC 0.48) | 0.69 s per pair on desktop CPU |
| d1-omni-600M | AUC 0.57 | Partly (AUC 0.66); 2 of 6 below 0.3 | 0.11 s per pair |
| Julia-1 | AUC 0.47 | No (AUC 0.40) | 0.02 s per pair |
| Qwen2.5-1.5B (rung zero) | Caught 0 of 8 unfaithful lines; 4 false alarms among the 22 faithful | 1 of 6 | 2.5–4.3 s per page |

**Comparison with the hand text.** There, d1-3B gave 0.560 to right lines against 0.556 to wrong ones. On real pairs it separates faithful from unfaithful lines better (AUC 0.86). One likely reason: the hand set was chosen to be hard, while these are the lines Qwen actually wrote. But no judge can tell that the Japanese itself is garbage, and garbage-in is the failure the input gate already catches for free.

**What the app would do with a flag, and where.**

- **Where it would sit:** today's output guard is `deadTranslationReason` (empty, non-translation, Japanese residue), applied in `TranslationEngine.translate` (`TranslationEngine.kt:41`, used at `:124` and `:132`). A judge would add one more test there.
- **The actions:** leave the line unanswered so the art shows, or retry it alone with `translatePerLine`, at about one prompt prefill plus one decode per retried bubble.
- **The cost:** a second resident model (d1-3B) or a second Qwen pass, on the critical path after decoding.
  - **It clashes with streaming:** since #348, a line is shown the moment it decodes. A check after decoding either delays every line or takes back lines already shown.
- **Verdict:** not worth it on these results.

## 7. Cost of a second path

- **Cloud (Jev or any hosted labeller).** It needs a reopening ADR, since ADR-0018 says "Reopening takes a new ADR that supersedes this one, and the README's promise changes in the same pull request". The README promises "Nothing leaves your phone". It also needs a consent screen naming TypeSafe and US hosting, and either a Yomu server or keys readers bring themselves.
  - **Fallback and latency:** without network, without a key, or on failure, the page falls back to the unlabelled prompt within a fixed deadline. The call sits between OCR and translation, adding a round trip (vendor: 70–500 ms, measured from the US West Coast) to a page the S23 translates in ~11.7 s median (ADR-0013, #214 amendment). Measured against the first bubble, which arrives 0.7–1.1 s into translation on the `Yomu_16G` emulator ([PR #348](https://github.com/artsaraiva/yomu/pull/348), emulator numbers), the round trip is a large share.
- **Local.** Everything in [§4's port list](#what-moving-the-readout-into-yomu-would-take). The decision pass also sits on the critical path:
  - d1-omni took 1.8–6.3 s per page for text on desktop CPU, which is longer than Qwen's whole translation call there.
  - Julia-1 took 0.4–1.7 s.
  - d1-3B took 16–56 s per page for the class question (0.66 s per bubble when asked singly), roughly 4 to 18 times the translation call.
  - The phone would be slower than the desktop. None of this is measured on a phone.
  - The input gate that worked in §6 is the exception: it reads probabilities the OCR decoder already produces, so it adds no model and almost no time.

## Conclusion

**(a) Cloud Jev: Not worth it, on access.**

- **No way to ship it:** a Yomu-held key needs a Yomu server, which ADR-0018 forbids. MCA §2.4 requires the key to stay confidential, and an APK cannot keep it so. Keys readers bring themselves serve almost nobody. Signups are paused.
- **Weak on this input by its own account:** TypeSafe's docs flag CJK text and numbers as weak spots.
- **Nothing unique about it:** local open-weight models now offer the same typed-question interface. None of them answered well here either.
- **Real OCR changes nothing here.**

**(b) Do typed hints help Qwen2.5-1.5B?**

| Use | Answer |
|---|---|
| Bubble class | No. |
| Speaker gender | No. |
| Continuation | No. |
| Register | Possibly, only for non-English targets once #337 lands. It needs a leak-proof reply first. |
| Skipping bubbles for speed | No. Almost nothing to save. |
| **Input gate (validator a): OCR's own token probability** | **Yes, on these pages.** It separated all 6 garbage reads from the 30 readable ones, with no new model, at almost no cost. It would be its own follow-up issue. |
| Input gate: a decision model or Qwen | No. d1-3B overlapped, d1-omni was weak, Julia-1 was inverted, and Qwen flagged most lines. They cost up to 13 s per page. |
| Output check (validator b) after Qwen | No. No judge caught translations of garbage input. Only d1-3B separated faithful from unfaithful clean lines, at 0.7 s per line, and a check after decoding fights streaming. |
| Routing / reranking | No. Too slow on a phone, and no judge here caught a translation of garbage input. |
| Out-of-scope text (title, credit) | Untested. |
| Name reading | Belongs to #327. |

For bubble class, gender and continuation, perfect hand-written hints made no visible difference on the fixture pages. The cost, on the other hand, is visible: prompt length, and marks leaking into the reply.

**What real OCR changes in (b).** The class row gets weaker: only 1 of 7 non-speech bubbles reaches the prompt as clean text. The validator rows are new. The useful validator is a gate on the OCR's own confidence, not a model of Jev's kind. It turns six confident wrong English lines into untouched art on these pages.

**Calibration.** This rests on three pages, one emulator run and six garbage reads, and the threshold was picked on the same data. The follow-up must confirm it on more pages, and on the phone.

**(c) Local Jev-like path: no follow-up issue now.**

- **The models are there:** Apache-2.0 decision models with GGUFs and llama.cpp support exist.
- **The bump is small:** by header diff, the pinned JNI compiles unchanged up to `a657f7e`, and needs a one-line change on master.
- **The real cost is elsewhere:** porting the readout, and running a second resident model.
- **No candidate passed:** on Japanese manga text, every candidate failed the questions Yomu would ask (the best, d1-3B, found 2 of 7 non-speech bubbles and took several times longer than the translation call on desktop CPU).
- **(b) blocks it anyway:** even correct answers would not improve today's English output.
- **Real OCR strengthens this:**
  - the class question has little text to work on;
  - the one validator that works needs no Jev-like model;
  - the decision models were worse gates than the OCR's own probabilities.

**What would reopen (c):**

1. **#337 lands,** and a leak-proof register re-test shows a visible gain in the target languages.
2. **A decision model then answers register correctly on real page-report OCR** for the fixture pages. Class is a weaker reason now: on real OCR it mostly lacks text to classify, because non-speech bubbles are missed or garbled before any labeller sees them.

If both hold, the follow-up issue would cover the rest:

- pick the candidate (Apache-2.0, under ~0.9 GB);
- bump llama.cpp to at least `a657f7e`, or stay on the pin for an `lfm2`/`qwen35` model;
- port that one model type's readout from `server-decision.cpp` into `ml/src/main/cpp`;
- move the JNI from globals to model handles, and add a curated decision slot and a two-model fit budget;
- add the hint field and a grammar that forbids marks in the reply;
- add the recall-key and page-report fields;
- amend ADR-0007's revisit clause to admit a checked class source with published weights;
- run AGENTS.md's on-device translation check and `scripts/run-speed-benchmark.sh` table.

**Follow-ups this note points to (not filed yet):**

1. **An OCR-confidence gate.**
   - **Compute:** the mean token probability in `OcrEngine.runDecoder`, returned as `OcrResult.confidence`.
   - **Skip:** reads below a threshold at `TranslationPipeline.kt:195`, so they stay unanswered and the art shows.
   - **Record:** each read and its confidence in the page report.
   - **Re-check:** the threshold on more pages.
2. **OCR decoding settings.** Use the model's own start token (2, not 0), and try its beam settings. The replica's garbled reads got closer to the page with start token 2.
3. **Interface text read as bubbles when Photos shows its toolbar.** This is being filed separately.

## Procedure for someone with a Jev key

This is kept from the first version of this note. It is the same experiment as §5, with Jev as the labeller. Use `jev-1.14.0`, or `typesafe-ai/jev-1.14.0` on Vercel AI Gateway, and pin the versioned id. Send the same state and questions as `exp/d1.py`: one Choice per bubble, a reversed-options pass, and optionally the page image under `TypeSafe-Danger-Zone-Images: true`. Read the answers against the labels in Appendix A. Given (b), only the register question is still worth asking. Run it only if #337's re-test shows register marks help.

## Open questions / not verified

- **Nothing here ran on a phone.** No decision-model latency on a phone is known; the desktop timings are a lower bound.
- **§2–§5 rest on my transcription.** §6 tests real OCR, but from one emulator run of three pages, not a phone.
- **The OCR replica is close, not exact.** It reproduced the app's text on 34 of 37 reads. The three it read differently are garbage either way. The resampling differs slightly from Android's `createScaledBitmap`.
- **The 0.95 gate threshold is fitted to six garbage reads.** No held-out pages were used.
- **The hint wording is one wording.** A different phrasing, or hints in a system turn, was not tried.
- **d3-edge, Kai-0.6B, laya-multilingual (ONNX) and the Laya English model were not run.** d1-3B's speed was measured with `llama-server` defaults on CPU. A tuned port might be faster, and the vendor's 30 ms figure is on a GPU.
- **The ONNX route over Yomu's existing ONNX Runtime is untested.**
- **Whether TypeSafe signups are open today is unknown.** The console refuses unauthenticated fetches.
- **The French and Portuguese weakness of Qwen2.5-1.5B is seen on two pages only.** #337's S23 check is where it gets confirmed.

## Sources

- GitHub (artsaraiva/yomu): #350, #324, #20, #337, #327, PR #348.
- GitHub (ggml-org/llama.cpp): #29818 (`a4cb4c6`), #29844, #29831, #30110 (`88dcc46`), #30114 (`a657f7e`), #30378, #30383, #30389 (`ff026f8`), and the open PRs #29832, #30361, #30504. Read files: `include/llama.h`, `common/chat.h`, `common/common.h`, `common/CMakeLists.txt`, `tools/server/server-decision.{h,cpp}`, `tools/server/server-context.cpp`, `tools/server/README.md` at `7fe450e`, `a657f7e` and `7182ef7`.
- Repo: ADR-0001, 0002, 0007, 0010, 0013, 0015, 0017, 0018; `CONTEXT.md`; `AGENTS.md`; `README.md`; `pipeline/…/TranslationPipeline.kt`, `context/ContextAssembler.kt`, `translation/TranslationEngine.kt`, `ocr/OcrEngine.kt`; `core/…/TranslationSlot.kt`; `ml/…/LlamaTranslationBridge.kt`; `ml/src/main/cpp/llama_jni.cpp`, `chat_prompt.cpp`, `grammar_sampler.cpp`, `CMakeLists.txt`; `ml/build.gradle.kts`; `app/…/service/PageReport.kt`, `PageRecall.kt`, `ModelManager.kt`, `OverlayService.kt`; `app/…/ui/settings/SettingsViewModel.kt`; `app/…/translation/LlmModelCatalog.kt`; `scripts/run-speed-benchmark.sh`.
- TypeSafe: https://docs.typesafe.ai/llms.txt and the pages `introduction`, `quickstart`, `system-one`, `primitives`, `api`, `models`, `images`, `model-jaggedness/jev-1.13`, `legal`, `sdk/python/changelog` and `sdk/javascript/api/interfaces/TypeSafeClientConfig` (all as `.md`); https://typesafe.ai/blog/introducing-system-one-models-and-jev; https://typesafe.ai/blog/jev-1-14; https://typesafe.ai/legal/mca, `/privacy-policy`, `/data-processing`, `/acceptable-use-policy`, `/terms`; X posts 2101786156572823624 and 2102281508950307159.
- Vercel: https://vercel.com/docs/ai-gateway/sdks-and-apis/typesafe; `GET https://ai-gateway.vercel.sh/v1/models`.
- Hugging Face cards and API entries: `LiquidAI/d1-3B` @`051bcc4` (README, LICENSE), `LiquidAI/d1-3B-GGUF` @`bb1e436`, `LiquidAI/d1-omni-600M` @`02b55d7`, `LiquidAI/d1-omni-600M-GGUF` @`0439714`, `SupersonicLabs/Julia-1` (README, `metrics/accuracy-20260924.json`), `ggml-org/Julia-1-GGUF` @`16fee17`, `convaiinnovations/laya` @`7b928d8`, `convaiinnovations/laya-multilingual` @`1720e3e`, `ggml-org/Laya-GGUF` @`2226500`, `mys/laya-multilingual-GGUF` @`3b645ae`, `vllm-sr/d3-edge` (README), `ggml-org/vllm-d3-edge-GGUF` @`753af7a`, `vllm-sr/Decision-2.0-Kai-0.6B` and `-GGUF` @`633669d`, `onnx-community/Decision-2.0-Kai-0.6B-ONNX` @`13ab550`, `mizchi/laya-multilingual-onnx` @`d9d003d`, `ggml-org/lev-GGUF`, `ggml-org/Kev-4B-GGUF`, `ggml-org/OpenJev-GGUF`, `ggml-org/Clef-GGUF`, `ggml-org/Clef-Flash-GGUF`, `bartowski/Qwen2.5-1.5B-Instruct-GGUF` @`9eadc66`.
- Pydantic AI: `pydantic_ai_slim/pydantic_ai/profiles/typesafe.py` at `7db7a4d`.
- Real OCR (§6):
  - page reports from the `sdk_gphone16k_arm64` emulator, 2026-10-17 (`page.json`, `capture.png`, `reply.txt` per page);
  - the app's MangaOCR ONNX files and vocabulary, pulled read-only from the emulator;
  - `l0wgear/manga-ocr-2025-onnx` @`e8b27bb` (`config.json`, `generation_config.json`, `preprocessor_config.json`, README);
  - repo files `pipeline/…/ocr/OcrEngine.kt`, `CropBoundsCalculator.kt`, `TranslationPipeline.kt` and `context/ContextAssembler.kt`, and `app/…/service/ModelManager.kt`.
- Secondary, used only to find primary pages: Wikipedia "Jev (AI model)", flaviocopes.com/jev and /jev-api-key, and the Requesty and apidog posts. The claim that Cloudflare AI Gateway serves Jev rests on these alone.

## Appendix A: hand transcription and reference labels

Read by eye from `scripts/speed-fixtures/pages/*.jpg`, not by Yomu's OCR. Ids are mine, in reading order, and "(new panel)" marks the first bubble of a new drawn panel. Bubbles with no letter or digit (`……?`, `……`) are left out, as `TranslationEngine` drops them from the prompt. Two labels are judgment calls: balloon-dense-dialogue [7] is product text drawn on the camera (my reading of it is uncertain), labelled sfx for want of a closer class; bourei-p04 [3] is the title and author credit, which ADR-0007 puts out of scope, labelled narration.

**balloon-dense-dialogue**

| id | text | hand label |
|---|---|---|
| 1 | ただ空気いれた風船をいくら集めても浮くわけねーだろ!! | speech |
| 2 | こいつ絶対バカだし | speech |
| 3 | むぐっ | speech |
| 4 (new panel) | なにあ然としてんの!? | speech |
| 5 | ヘリウム入れないと風船は浮かねーよ!! | speech |
| 6 (new panel) | 大体なんで使い捨てカメラなんだよ! | speech |
| 7 | すご～くよく写るンズ | sfx |
| 8 | あっ安かったので… | speech |
| 9 | 宇宙でフィルム巻いてシャッターきる気か!? | speech |
| 10 (new panel) | ったく | speech |
| 11 | 確かに原理的には可能かもしれないけどな… | speech |
| 12 (new panel) | 地球の写真なんてネット上にいくらでも落ちてるだろ | speech |
| 13 | わざわざ自分で撮らなくたって… | speech |
| 14 (new panel) | ああ | speech |
| 15 | それはですね… | speech |

**bourei-p04**

| id | text | hand label |
|---|---|---|
| 1 | 死んでるんですよねぇ | speech |
| 2 | 「視える」人間ってヤツでして | narration |
| 3 | ようこそ亡霊葬儀屋さん 吉良いと | narration |
| 4 (new panel) | え? | speech |
| 5 | 亡くなったお姉さんのお化粧を? | speech |
| 6 | はい… | speech |
| 7 | 烏丸葬儀社社長 烏丸 柩(からすま くるる) 25歳 | narration |
| 8 | 葬儀依頼人 桜野 すずめ 19歳 | narration |

**tojime-dense-action**

| id | text | hand label |
|---|---|---|
| 1 | マフィア舐めんのも大概にしやがれ | speech |
| 2 | グッ | sfx |
| 3 | あっ | speech |
| 4 | 閉じ眼が用心棒だ? | speech |
| 5 (new panel) | 腰の銃でイかれた脳みそぶちまけてみるか? | speech |
| 6 | あ? | speech |
| 7 | あ、あの… | speech |
| 8 | ちょっとやめてよ!こんなとこで! | speech |
| 9 | プラーン | sfx |
| 10 (new panel) | ん? | speech |
| 11 (new panel) | お前もしかして | speech |
| 12 | 女か? | speech |
| 13 | えっはい…そうですけど… | speech |
| 14 (new panel) | ほぉ | speech |
| 15 (new panel) | 面見せてみろ | speech |
| 16 | えっ | speech |
| 17 | 閉じ眼でも器量が良けりゃ買い手は… | speech |
| 18 | やっちょっと…! | speech |

## Appendix B: bubble-class answers per model

**balloon-dense-dialogue**

| id | hand | Qwen2.5-1.5B (grammar) | d1-3B text | d1-3B + page image | d1-omni-600M text | d1-omni-600M + page image | Julia-1 text |
|---|---|---|---|---|---|---|---|
| 1 | speech | speech | speech 0.77 | speech 0.35 | speech 0.49 | speech 0.45 | sfx 0.97 |
| 2 | speech | speech | speech 0.75 | speech 0.48 | speech 0.67 | speech 0.41 | sfx 1.00 |
| 3 | speech | sfx | speech 0.80 | speech 0.66 | speech 0.84 | speech 0.64 | sfx 0.99 |
| 4 | speech | speech | sfx 0.81 | speech 0.62 | speech 0.80 | speech 0.69 | sfx 0.99 |
| 5 | speech | speech | speech 0.56 | speech 0.72 | speech 0.82 | speech 0.65 | sfx 0.98 |
| 6 | speech | narration | speech 0.57 | speech 0.75 | speech 0.75 | speech 0.70 | sfx 0.99 |
| 7 | sfx | speech | speech 0.31 | speech 0.53 | speech 0.76 | speech 0.71 | sfx 0.99 |
| 8 | speech | narration | speech 0.71 | speech 0.63 | speech 0.71 | speech 0.64 | sfx 0.98 |
| 9 | speech | narration | speech 0.38 | speech 0.68 | speech 0.72 | speech 0.59 | sfx 0.95 |
| 10 | speech | speech | speech 0.19 | speech 0.54 | speech 0.61 | speech 0.54 | sfx 0.98 |
| 11 | speech | narration | speech 0.57 | speech 0.62 | speech 0.66 | speech 0.55 | sfx 0.91 |
| 12 | speech | narration | speech 0.75 | speech 0.47 | speech 0.70 | speech 0.59 | sfx 0.99 |
| 13 | speech | narration | speech 0.38 | speech 0.78 | speech 0.72 | speech 0.68 | sfx 0.98 |
| 14 | speech | speech | speech 0.78 | speech 0.92 | speech 0.63 | speech 0.64 | sfx 0.99 |
| 15 | speech | narration | speech 0.89 | speech 0.93 | speech 0.60 | speech 0.62 | sfx 0.99 |

**bourei-p04**

| id | hand | Qwen2.5-1.5B (grammar) | d1-3B text | d1-3B + page image | d1-omni-600M text | d1-omni-600M + page image | Julia-1 text |
|---|---|---|---|---|---|---|---|
| 1 | speech | sfx | speech 0.88 | narration 0.55 | speech 0.87 | narration 0.43 | narration 0.76 |
| 2 | narration | speech | speech 0.89 | narration 0.39 | speech 0.91 | narration 0.43 | narration 0.74 |
| 3 | narration | narration | speech 0.89 | speech 0.73 | speech 0.91 | narration 0.28 | narration 0.28 |
| 4 | speech | speech | speech 0.33 | narration 0.60 | speech 0.92 | narration 0.31 | narration 0.79 |
| 5 | speech | narration | speech 0.92 | speech 0.74 | speech 0.93 | speech 0.29 | narration 0.56 |
| 6 | speech | speech | speech 0.90 | speech 0.77 | speech 0.92 | narration 0.28 | narration 0.78 |
| 7 | narration | speech | speech 0.90 | speech 0.90 | speech 0.91 | narration 0.25 | narration 0.54 |
| 8 | narration | speech | speech 0.58 | speech 0.37 | speech 0.90 | narration 0.30 | narration 0.78 |

**tojime-dense-action**

| id | hand | Qwen2.5-1.5B (grammar) | d1-3B text | d1-3B + page image | d1-omni-600M text | d1-omni-600M + page image | Julia-1 text |
|---|---|---|---|---|---|---|---|
| 1 | speech | speech | speech 0.80 | speech 0.74 | speech 0.88 | speech 0.69 | sfx 1.00 |
| 2 | sfx | sfx | speech 0.83 | speech 0.80 | speech 0.90 | speech 0.65 | sfx 0.99 |
| 3 | speech | sfx | speech 0.92 | sfx 0.45 | speech 0.94 | speech 0.74 | sfx 0.99 |
| 4 | speech | speech | speech 0.80 | speech 0.64 | speech 0.94 | speech 0.75 | sfx 1.00 |
| 5 | speech | speech | speech 0.75 | speech 0.39 | speech 0.95 | speech 0.73 | sfx 0.99 |
| 6 | speech | speech | speech 0.74 | speech 0.42 | speech 0.91 | speech 0.78 | sfx 0.98 |
| 7 | speech | speech | speech 0.90 | speech 0.61 | speech 0.94 | speech 0.75 | sfx 0.99 |
| 8 | speech | narration | speech 0.92 | sfx 0.50 | speech 0.89 | speech 0.76 | sfx 0.98 |
| 9 | sfx | sfx | speech 0.91 | sfx 0.56 | speech 0.92 | speech 0.68 | sfx 1.00 |
| 10 | speech | speech | sfx 0.32 | sfx 0.90 | speech 0.90 | speech 0.71 | sfx 0.99 |
| 11 | speech | speech | speech 0.83 | speech 0.69 | speech 0.93 | speech 0.71 | sfx 0.99 |
| 12 | speech | speech | speech 0.91 | speech 0.78 | speech 0.92 | speech 0.71 | sfx 0.99 |
| 13 | speech | narration | speech 0.90 | speech 0.87 | speech 0.94 | speech 0.71 | sfx 1.00 |
| 14 | speech | speech | speech 0.86 | speech 0.83 | speech 0.92 | speech 0.65 | sfx 0.99 |
| 15 | speech | speech | speech 0.68 | speech 0.86 | speech 0.91 | speech 0.70 | sfx 0.99 |
| 16 | speech | speech | speech 0.76 | speech 0.76 | speech 0.91 | speech 0.70 | sfx 0.99 |
| 17 | speech | narration | speech 0.87 | speech 0.63 | speech 0.93 | speech 0.74 | sfx 0.99 |
| 18 | speech | speech | speech 0.82 | speech 0.83 | speech 0.93 | speech 0.72 | sfx 0.99 |

## Appendix C: every Qwen2.5-1.5B reply, in full

Desktop llama.cpp `7182ef7`, CPU, 4 threads, the app's sampler chain and batch grammar. `none` is the shipped prompt. Each page's section opens with the full prompts of its arms.

### balloon-dense-dialogue

**none prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.
[1] ただ空気いれた風船をいくら集めても浮くわけねーだろ!!
[2] こいつ絶対バカだし
[3] むぐっ
---
[4] なにあ然としてんの!?
[5] ヘリウム入れないと風船は浮かねーよ!!
---
[6] 大体なんで使い捨てカメラなんだよ!
[7] すご～くよく写るンズ
[8] あっ安かったので…
[9] 宇宙でフィルム巻いてシャッターきる気か!?
---
[10] ったく
[11] 確かに原理的には可能かもしれないけどな…
---
[12] 地球の写真なんてネット上にいくらでも落ちてるだろ
[13] わざわざ自分で撮らなくたって…
---
[14] ああ
[15] それはですね…
```

**hand prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (speech), (narration) or (sfx) mark after its tag; it is not part of the text, so do not copy it into your reply.
[1] (speech) ただ空気いれた風船をいくら集めても浮くわけねーだろ!!
[2] (speech) こいつ絶対バカだし
[3] (speech) むぐっ
---
[4] (speech) なにあ然としてんの!?
[5] (speech) ヘリウム入れないと風船は浮かねーよ!!
---
[6] (speech) 大体なんで使い捨てカメラなんだよ!
[7] (sfx) すご～くよく写るンズ
[8] (speech) あっ安かったので…
[9] (speech) 宇宙でフィルム巻いてシャッターきる気か!?
---
[10] (speech) ったく
[11] (speech) 確かに原理的には可能かもしれないけどな…
---
[12] (speech) 地球の写真なんてネット上にいくらでも落ちてるだろ
[13] (speech) わざわざ自分で撮らなくたって…
---
[14] (speech) ああ
[15] (speech) それはですね…
```

**hand_nonspeech_only prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (speech), (narration) or (sfx) mark after its tag; it is not part of the text, so do not copy it into your reply.
[1] ただ空気いれた風船をいくら集めても浮くわけねーだろ!!
[2] こいつ絶対バカだし
[3] むぐっ
---
[4] なにあ然としてんの!?
[5] ヘリウム入れないと風船は浮かねーよ!!
---
[6] 大体なんで使い捨てカメラなんだよ!
[7] (sfx) すご～くよく写るンズ
[8] あっ安かったので…
[9] 宇宙でフィルム巻いてシャッターきる気か!?
---
[10] ったく
[11] 確かに原理的には可能かもしれないけどな…
---
[12] 地球の写真なんてネット上にいくらでも落ちてるだろ
[13] わざわざ自分で撮らなくたって…
---
[14] ああ
[15] それはですね…
```

**none, seed 42**

```text
[1] Only by collecting as many balloons as possible will they not float!
[2] He's definitely a fool!
[3] mu-gg
[4] What the hell!?
[5] Helium is needed for the balloon to float!
[6] It's a disposable camera, basically!
[7] It's really good at taking pictures!
[8] Ah, it was too cheap...
[9] Are you trying to take pictures in space?!
[10] What the hell!
[11] Yes, theoretically it's possible, but...
[12] Photos of the Earth can be found on the internet for free!
[13] Even if you don't take them yourself...
[14] Ah...
[15] That's right...!
```

**hand, seed 42**

```text
[1] (speech) Just because I collect balloons, no matter how many I gather, they will never float!
[2] (speech) He's definitely a fool!
[3] (speech) Mu-gg!
[4] (speech) What the hell!?
[5] (speech) Helium is needed to make the balloon float!
[6] (speech) It's a disposable camera, right?
[7] (sfx) Wow, it's so good!
[8] (speech) Ah, it was too cheap...
[9] (speech) Can you even take a picture in space?
[10] (speech) What a shame!
[11] (speech) Yes, technically it's possible, but...
[12] (speech) There are plenty of pictures of Earth on the internet, right?
[13] (speech) You can't even take a picture by yourself...
[14] (speech) Ah...
[15] (speech) That's right... (narration) "Just because I collect balloons, no matter how many I gather, they will never float!" "He's definitely a fool!" "Mu-gg!" "
```

**hand_nonspeech_only, seed 42**

```text
[1] Only by collecting as many helium balloons as possible will they float!
[2] He's absolutely a fool!
[3] mu-gg
[4] What the hell!?
[5] Without helium, the balloons won't float!
[6] It's a disposable camera, right?
[7] (sfx) It's so good!
[8] It was too cheap!
[9] Can't even take a picture in space?
[10] What the hell!
[11] Yes, it's possible, but...
[12] There are tons of photos of Earth on the internet.
[13] Even if you take it yourself...
[14] Ah...
[15] That's right... "Aah" "That's right" "Ah" "That's right" "Ah" "That's right" "Ah" "That's right" "Ah" "That's right" "Ah" "That's right" "Ah" "That's right" "Ah
```

**none, seed 7**

```text
[1] Only by collecting as many balloons as possible will they float, no matter how much air you put in them!!
[2] He's definitely a fool!!
[3] mugu
[4] What nonsense!?
[5] Helium is needed to make the balloon float!!
[6] It's a disposable camera, basically!!
[7] It's really good at taking photos!!
[8] Ah, it was too cheap!!
[9] Can you even take a picture with film in space?!!
[10] What a shame!!
[11] Yes, technically it's possible, but...
[12] Photos of the Earth can be found on the internet for free!!
[13] Even if you take it yourself, it's not worth it...
[14] Ah
[15] That's right...!
```

**none, seed 8**

```text
[1] Only by collecting as many balloons as possible will they float, no matter how much air is put in them!!
[2] This guy is definitely a fool!!
[3] mugu
[4] What the hell!!?
[5] Helium is needed to make the balloon float!!
[6] It's a disposable camera, basically!!
[7] It's amazing!! (This is a translation of "すごい!!" which is an exclamation of admiration or surprise)
[8] Ah, it was cheap!!
[9] Can you even take a picture in space with film??!!
[10] What the hell!!?
[11] Yes, theoretically it is possible, but...
[12] Photos of the Earth are everywhere on the internet, right?
[13] Even if you take it yourself...
[14] Ah
[15] That's right... (This is a translation of "それでは" which means "Then" or "So" in English)
```

**none, seed 9**

```text
[1] Only by collecting as many balloons as possible will they float, no matter how much air is put in them!!
[2] This guy is definitely a fool!!
[3] mugu
[4] What the hell!?
[5] Helium is needed to make the balloon float!!
[6] It's a disposable camera, basically!!
[7] It's really good at taking photos!!
[8] Oh, it was too cheap!!
[9] Can you even take pictures in space with film???
[10] What a waste of time!!
[11] Yes, theoretically it's possible, but...
[12] There are plenty of pictures of the Earth on the internet, right?
[13] You wouldn't even take a picture yourself...
[14] Ah
[15] That's right... "I'm sorry" (I'm sorry) "I'm sorry" "I'm sorry" "I'm sorry" "I'm sorry" "I'm sorry" "I'm sorry" "I'm sorry" "I'm sorry" "I'm sorry" "I'm sorry" 
```

**hand_nonspeech_only, seed 7**

```text
[1] Only by collecting as many balloons as possible will they float!
[2] He's definitely a fool!
[3] mu-gg
[4] What on earth is this for?
[5] Helium is needed to make the balloon float!
[6] It's a disposable camera, right?
[7] (sfx) So vivid!
[8] Ah, it was too cheap!
[9] Can you take pictures in space with film rolls?
[10] I can't believe it!
[11] Yes, technically it's possible, but...
[12] Photos of the Earth can be found on the internet for free!
[13] Even if you take it yourself, you can't!
[14] Ah...
[15] That's right... "I can't believe it!" "I can't believe it!" "I can't believe it!" "I can't believe it!" "I can't believe it!" "I can't believe it!" "I can't...!
```

**hand_nonspeech_only, seed 8**

```text
[1] Only by collecting as many as possible, the balloons will not float!
[2] He is definitely a fool!
[3] mu-gg
[4] What the hell!?
[5] Helium is needed to make the balloon float!
[6] It's a disposable camera, right?
[7] (sfx) So good at taking photos!
[8] Ah, it was too cheap...
[9] Can't even take a picture in space?
[10] What the hell!
[11] Yes, technically it's possible, but...
[12] Photos of the Earth can be found on the internet for free!
[13] Even if you don't take it yourself...
[14] Ah...
[15] That's right.../So it is.../That's the case.../That's the way it is.../That's the truth.../That's the reality.../That's the truth.../That's the way it is.../It,
```

**hand_nonspeech_only, seed 9**

```text
[1] Only by collecting as many balloons as possible will they float!
[2] He's definitely a fool!
[3] mu-gg
[4] What the hell!?
[5] Helium is needed to make the balloon float!
[6] It's a disposable camera, right?
[7] (sfx) So good at taking photos!
[8] Ah, it was too cheap...
[9] Can you take a picture in space with film rolls and shutter?
[10] What the hell!
[11] Yes, technically it's possible, but...
[12] Photos of the Earth can be found on the internet for free!
[13] Even if you take it yourself...
[14] Ah...
[15] That's right... " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " 
```

### bourei-p04

**none prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.
[1] 死んでるんですよねぇ
[2] 「視える」人間ってヤツでして
[3] ようこそ亡霊葬儀屋さん 吉良いと
---
[4] え?
[5] 亡くなったお姉さんのお化粧を?
[6] はい…
[7] 烏丸葬儀社社長 烏丸 柩(からすま くるる) 25歳
[8] 葬儀依頼人 桜野 すずめ 19歳
```

**hand prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (speech), (narration) or (sfx) mark after its tag; it is not part of the text, so do not copy it into your reply.
[1] (speech) 死んでるんですよねぇ
[2] (narration) 「視える」人間ってヤツでして
[3] (narration) ようこそ亡霊葬儀屋さん 吉良いと
---
[4] (speech) え?
[5] (speech) 亡くなったお姉さんのお化粧を?
[6] (speech) はい…
[7] (narration) 烏丸葬儀社社長 烏丸 柩(からすま くるる) 25歳
[8] (narration) 葬儀依頼人 桜野 すずめ 19歳
```

**hand_nonspeech_only prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (speech), (narration) or (sfx) mark after its tag; it is not part of the text, so do not copy it into your reply.
[1] 死んでるんですよねぇ
[2] (narration) 「視える」人間ってヤツでして
[3] (narration) ようこそ亡霊葬儀屋さん 吉良いと
---
[4] え?
[5] 亡くなったお姉さんのお化粧を?
[6] はい…
[7] (narration) 烏丸葬儀社社長 烏丸 柩(からすま くるる) 25歳
[8] (narration) 葬儀依頼人 桜野 すずめ 19歳
```

**none, seed 42**

```text
[1] "I'm dead, aren't I?"
[2] "The 'visible' human, right?"
[3] "Welcome to the funeral home of the ghost, good day!"
[4] "Eh?"
[5] "What about my sister's makeup after death?"
[6] "Yes..."
[7] "The head of the Urahama Funeral Home, Urahama Kusumaru, 25 years old"
[8] "The funeral home requester, Sakura no Mizu, 19 years old"
```

**hand, seed 42**

```text
[1] (speech) Are you dead?
[2] (narration) "Visible" people like you.
[3] (narration) Welcome to the funeral home of the ghost, good luck.
[4] (speech) E?
[5] (speech) Did you die, my sister?
[6] (speech) Yes...
[7] (narration) The funeral director of Uragami Funeral Home, Uragami Kusumakura, 25 years old.
[8] (narration) The funeral requestor, Sakura Yuki, 19 years old.
```

**hand_nonspeech_only, seed 42**

```text
[1] "I'm dead, aren't I?"
[2] (narration) "There are people you can see."
[3] (narration) "Welcome to the funeral home, good luck."
[4] ?
[5] The makeup of the deceased sister?
[6] Yes...
[7] (narration) "The funeral director of Uragamori Funeral Home, Uragami Kusumaru, 25 years old."
[8] (narration) "The funeral request person, Sakurae Suzuki, 19 years old."
```

**none, seed 7**

```text
[1] "I'm dead, aren't I?"
[2] "You mean 'visible' people, right?"
[3] "Welcome to the funeral home, good day!"
[4] "Eh?"
[5] "What about the makeup of the dead sister?"
[6] "Yes..."
[7] "Funeral director Uchigusa Funeral Service, Uchigusa Kusumokuro 25 years old"
[8] "The funeral requestor, Sakura Yume 19 years old"
```

**none, seed 8**

```text
[1] "I'm dead, aren't I?"
[2] "People you can see are like that, aren't they?"
[3] "Welcome to the funeral home, good luck!"
[4] "Eh?"
[5] "What about the makeup of the dead sister?"
[6] "Yes..."
[7] "Oroku Funeral Home President Oroku Kusumaru 25 years old"
[8] "The funeral requestor Sakurae Suzume 19 years old"
```

**none, seed 9**

```text
[1] "I'm dead, aren't I?"
[2] "You mean 'visible' people, right?"
[3] "Welcome to the funeral home of the dead, good day!"
[4] "Eh?"
[5] "Did you mean to ask about my sister's makeup after she died?"
[6] "Yes..."
[7] "The Funeral Director of Uchigusa Funeral Home, Uchigusa Kusumokuro 25 years old"
[8] "The Funeral Director of Uchigusa Funeral Home, Uchigusa Kusumokuro 19 years old"
```

**hand_nonspeech_only, seed 7**

```text
[1] "I'm dead, aren't I?"
[2] (narration) "Visible" humans are these guys.
[3] (narration) "Welcome to the funeral home, good luck!"
[4] ?
[5] The makeup of the deceased aunt?
[6] Yes...
[7] (narration) "Uragami Funeral Home" President, Uragami Kusumaru, 25 years old.
[8] (narration) "The funeral request person, Sakurae Suzuki, 19 years old."
```

**hand_nonspeech_only, seed 8**

```text
[1] "I'm dead, aren't I?"
[2] (narration) "Visible" people like you
[3] (narration) "Welcome to the funeral home of the deceased, good luck"
[4] ?
[5] The makeup of the deceased's sister?
[6] Yes...
[7] (narration) "The funeral director of Uraga Funeral Home, Uraga Kusumakuru, 25 years old"
[8] (narration) "The funeral requestor, Sakura Yuki, 19 years old"
```

**hand_nonspeech_only, seed 9**

```text
[1] "I'm dead, aren't I?"
[2] (narration) "Visible" people are like you.
[3] (narration) "Welcome to the funeral home, Mr. Kusumaru, good luck"
[4] ?
[5] The makeup of the deceased sister?
[6] Yes...
[7] (narration) "Mr. Kusumaru, the funeral director, 25 years old"
[8] (narration) "The funeral request person, Sakura Yama, 19 years old"
```

### tojime-dense-action

**none prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.
[1] マフィア舐めんのも大概にしやがれ
[2] グッ
[3] あっ
[4] 閉じ眼が用心棒だ?
---
[5] 腰の銃でイかれた脳みそぶちまけてみるか?
[6] あ?
[7] あ、あの…
[8] ちょっとやめてよ!こんなとこで!
[9] プラーン
---
[10] ん?
---
[11] お前もしかして
[12] 女か?
[13] えっはい…そうですけど…
---
[14] ほぉ
---
[15] 面見せてみろ
[16] えっ
[17] 閉じ眼でも器量が良けりゃ買い手は…
[18] やっちょっと…!
```

**hand prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (speech), (narration) or (sfx) mark after its tag; it is not part of the text, so do not copy it into your reply.
[1] (speech) マフィア舐めんのも大概にしやがれ
[2] (sfx) グッ
[3] (speech) あっ
[4] (speech) 閉じ眼が用心棒だ?
---
[5] (speech) 腰の銃でイかれた脳みそぶちまけてみるか?
[6] (speech) あ?
[7] (speech) あ、あの…
[8] (speech) ちょっとやめてよ!こんなとこで!
[9] (sfx) プラーン
---
[10] (speech) ん?
---
[11] (speech) お前もしかして
[12] (speech) 女か?
[13] (speech) えっはい…そうですけど…
---
[14] (speech) ほぉ
---
[15] (speech) 面見せてみろ
[16] (speech) えっ
[17] (speech) 閉じ眼でも器量が良けりゃ買い手は…
[18] (speech) やっちょっと…!
```

**hand_nonspeech_only prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (speech), (narration) or (sfx) mark after its tag; it is not part of the text, so do not copy it into your reply.
[1] マフィア舐めんのも大概にしやがれ
[2] (sfx) グッ
[3] あっ
[4] 閉じ眼が用心棒だ?
---
[5] 腰の銃でイかれた脳みそぶちまけてみるか?
[6] あ?
[7] あ、あの…
[8] ちょっとやめてよ!こんなとこで!
[9] (sfx) プラーン
---
[10] ん?
---
[11] お前もしかして
[12] 女か?
[13] えっはい…そうですけど…
---
[14] ほぉ
---
[15] 面見せてみろ
[16] えっ
[17] 閉じ眼でも器量が良けりゃ買い手は…
[18] やっちょっと…!
```

**none, seed 42**

```text
[1] Mafia licking my face is about as likely as it getting wet.
[2] Goo
[3] Ah
[4] Closing my eyes is my guard dog.
[5] I'll show you my brain if you shoot my ass off.
[6] Ah?
[7] Ah, that's it...
[8] Stop it! This is not the place!
[9] PLAN
[10] Nn?
[11] You might be a girl, right?
[12] Girl?
[13] Huh... Yes, but...
[14] Hoho
[15] Show me your face.
[16] Ah?
[17] Closing my eyes, I can still be a good customer.
[18] Just a little...!  (pause)  (repeats) Just a little...!  (pause)  (repeats) Just a little...!  (pause)  (repeats) Just a little...!  (pause)  (repeats) Just a l
```

**hand, seed 42**

```text
[1] (speech) I'm going to lick your mafia, aren't I?
[2] (sfx) GG
[3] (speech) Ah
[4] (speech) Closing your eyes is a good dagger?
[5] (speech) With a gun to the brain, let's see if you can survive?
[6] (speech) Ah?
[7] (speech) Ah, that's it...
[8] (speech) Stop it! This is too low!
[9] (sfx) PLAIN
[10] (speech) Nn?
[11] (speech) You too, maybe?
[12] (speech) Girl?
[13] (speech) Oh, yes... but...
[14] (speech) Hoo
[15] (speech) Show me your face
[16] (speech) Ah?
[17] (speech) Closing your eyes, you can still be a good deal...
[18] (speech) Yat's...! ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！
```

**hand_nonspeech_only, seed 42**

```text
[1] It's a shame that the Mafia is licking its lips.
[2] (sfx) GG
[3] Ah
[4] Closing eyes is a trickster?
[5] I'll show you my brain after you shoot my gun.
[6] Ah?
[7] Ah, that's…
[8] Stop it! This is not the place!
[9] (sfx) PLAN
[10] N?
[11] You too, maybe?
[12] Girl?
[13] Huh? Yes… but…
[14] Hoho
[15] Show me your face
[16] Ah?
[17] Closing eyes, even if you're not smart, buyers will still buy…
[18] Ah, just a little…!
```

**none, seed 7**

```text
[1] Mafia licking my face is about to happen
[2] GG
[3] Ah
[4] Closing my eyes is my guard dog
[5] I'll see if I can get my brain out of the gun wound on your waist
[6] Ah?
[7] Ah, that's it...
[8] Stop it! This is not the place!
[9] PLAN
[10] Nn?
[11] You might be a girl too, right?
[12] Girl?
[13] Huh... Yes, but...
[14] Hoho
[15] Show me your face
[16] Ah?
[17] Closing my eyes, I can still be a good buyer...
[18] Just a little...!  (translation of "ちょっと" as "just a little")  (translation of "器量" as "ability")  (translation of "買い手" as "buyer")  (translation of "器量" as "m
```

**none, seed 8**

```text
[1] Mafia sucking mouth is almost all
[2] GG
[3] Ah
[4] Closing eyes are the dagger?
[5] Shot in the waist, see if you can open your brain?
[6] ?
[7] Ah, that's it...
[8] Stop, this is not a place for this!
[9] PLAN
[10] ?
[11] You too, maybe
[12] Girl?
[13] Oh, yes... but...
[14] Hoho
[15] Show me your face
[16] ?
[17] Closing eyes, even if you're not smart, buyers will still buy...
[18] Just a little...!  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  !  ! !
```

**none, seed 9**

```text
[1] Mafia sucking my cock is about as likely as it is to happen.
[2] GG
[3] Ah
[4] Closing my eyes is my guard dog.
[5] I'd like to see you shoot your gun into my brain.
[6] Ah?
[7] Ah, that's it...
[8] Stop it! You're not in this situation.
[9] PLAN
[10] Nn?
[11] You might be a girl too, right?
[12] Girl?
[13] Huh, yeah... but I'm not.
[14] Hoho
[15] Show me your face.
[16] Ah?
[17] Closing my eyes, I'm still a good customer.
[18] Just a little...!  (pause)  (repeating) Just a little...!  (pause)  (repeating) Just a little...!  (pause)  (repeating) Just a little...!  (pause)  (repeating) 
```

**hand_nonspeech_only, seed 7**

```text
[1] It's a shame that the Mafia is licking its lips.
[2] (sfx) GG
[3] Ah
[4] Closing your eyes is a spy?
[5] I'll see if I can get a look at the brain in your waist-shot.
[6] ?
[7] Ah, that's it...
[8] Stop it! This is no place for this!
[9] (sfx) PLAN
[10] ?
[11] You might be a girl, too.
[12] Is that so?
[13] Huh... Yes, but...
[14] Hoho
[15] Show me your face.
[16] ?
[17] Closing your eyes, you can still be a good quality...
[18] Just a little...!']
```

**hand_nonspeech_only, seed 8**

```text
[1] It's normal for mafia to lick their victims' throats.
[2] (sfx) Goo
[3] Ah
[4] Closing eyes is a vigilante?
[5] Show me your brain after being shot with a gun on your waist?
[6] ?
[7] Ah, that's…
[8] Stop it! This is too low!
[9] (sfx) Plan
[10] Nn?
[11] You might be a girl, too?
[12] Is that so?
[13] Huh? Yes… but…
[14] Hoho
[15] Show me your face
[16] Ah?
[17] Closing eyes, even if you're not smart enough, someone will still buy you.
[18] Y-You're kidding!?! (laughs) - You're kidding!?! (laughs) - You're kidding!?! (laughs) - You're kidding!?! (laughs) - You're kidding!?! (laughs) - You're... (s)
```

**hand_nonspeech_only, seed 9**

```text
[1] It's a shame that the mafia would lick its lips.
[2] (sfx) GG
[3] Ah
[4] Closing your eyes is a trickster?
[5] With a gun to the brain, let's see if you can still make out the outline of the waist?
[6] ?
[7] Ah, that's it...
[8] Stop it! This is no place for this!
[9] (sfx) PLANE
[10] ?
[11] You too, maybe?
[12] Girl?
[13] Huh? Yes... but...
[14] Hoho
[15] Show me your face
[16] ?
[17] Closing your eyes, even if you're not smart, there will still be buyers...
[18] Y-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-You-Oh
```

### tojime-dense-action: speaker-gender and continuation marks

**gender prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (male) or (female) mark after its tag: the speaker's gender. It is not part of the text, so do not copy it into your reply.
[1] (male) マフィア舐めんのも大概にしやがれ
[2] グッ
[3] (female) あっ
[4] (male) 閉じ眼が用心棒だ?
---
[5] (male) 腰の銃でイかれた脳みそぶちまけてみるか?
[6] (male) あ?
[7] (female) あ、あの…
[8] (female) ちょっとやめてよ!こんなとこで!
[9] プラーン
---
[10] (male) ん?
---
[11] (male) お前もしかして
[12] (male) 女か?
[13] (female) えっはい…そうですけど…
---
[14] (male) ほぉ
---
[15] (male) 面見せてみろ
[16] (female) えっ
[17] (male) 閉じ眼でも器量が良けりゃ買い手は…
[18] (female) やっちょっと…!
```

**gender, seed 7**

```text
[1] (male) I'm going to lick your mafia.
[2] Goo
[3] (female) Ah
[4] (male) Are you the one who's being careful with your eyes?
[5] (male) If you're going to be shot in the head with a gun, you can see what's inside my brain.
[6] (male) Ah?
[7] (female) Ah, that's it...
[8] (female) Stop it! This is not the place!
[9] PLAN
[10] (male) Nn?
[11] (male) You, too, maybe?
[12] (male) Girl?
[13] (female) Oh, yeah... but it's true...
[14] (male) Hoo
[15] (male) Show me your face
[16] (female) Ah?
[17] (male) If you close your eyes, you can still be a good quality...
[18] (female) Y-You're kidding?! ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ 
```

**gender, seed 8**

```text
[1] (male) I'm not going to lick the mafia's face.
[2] GG
[3] (female) Ah
[4] (male) Are you trying to be a vigilante?
[5] (male) Let's see if I can make your brain pop with a bullet to the head?
[6] (male) Ah?
[7] (female) Ah, that's it...
[8] (female) Stop it! This isn't a place for that!
[9] PLAN
[10] (male) What?
[11] (male) Are you, too?
[12] (male) Girl?
[13] (female) Oh, yes... but...
[14] (male) Huh
[15] (male) Show me your face.
[16] (female) Ah?
[17] (male) If you close your eyes, you still have an ability.
[18] (female) Just a little...! ```lua
```

**gender, seed 9**

```text
[1] (male) I'm going to lick the Mafia's ass.
[2] GG
[3] (female) Ah
[4] (male) Are you the one who's being careful?
[5] (male) If you're so desperate, let's see what happens if I shoot your brain with a gun?
[6] (male) Ah?
[7] (female) Ah, that...
[8] (female) Stop it! This isn't the place!
[9] PLANNER
[10] (male) Nn?
[11] (male) You too, maybe?
[12] (male) Girl?
[13] (female) Uh... Yes, but...
[14] (male) Hoo
[15] (male) Show me your face
[16] (female) Ah?
[17] (male) If you close your eyes, you can still be a valuable asset...
[18] (female) Y-You're kidding! ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！ ！
```

**continuation prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (continues [n]) mark after its tag: it finishes the sentence begun in line n. It is not part of the text, so do not copy it into your reply.
[1] マフィア舐めんのも大概にしやがれ
[2] グッ
[3] あっ
[4] 閉じ眼が用心棒だ?
---
[5] 腰の銃でイかれた脳みそぶちまけてみるか?
[6] あ?
[7] あ、あの…
[8] ちょっとやめてよ!こんなとこで!
[9] プラーン
---
[10] ん?
---
[11] お前もしかして
[12] (continues [11]) 女か?
[13] えっはい…そうですけど…
---
[14] ほぉ
---
[15] 面見せてみろ
[16] えっ
[17] 閉じ眼でも器量が良けりゃ買い手は…
[18] やっちょっと…!
```

**continuation, seed 7**

```text
[1] Mafia licking my ass is probably true
[2] Goo
[3] Ah
[4] Closing eyes is the wise man?
[5] Shot in the waist, what's the brain?
[6] ?
[7] Ah, that...
[8] Stop it! This is not the place!
[9] PLAN
[10] N?
[11] You too, maybe
[12] (continues 11) Girl?
[13] Huh, yeah... but...
[14] Hoho
[15] Show me your face
[16] Huh
[17] Closing eyes, even if you're stupid, there are buyers...
[18] Y-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya-ya
```

**continuation, seed 8**

```text
[1] Mafia sucking is not a big deal
[2] GG
[3] Ah
[4] Closing eyes is a vigilante
[5] Shot in the waist, brain in the chest, let's see if you can take it?
[6] ?
[7] Ah, that's it...
[8] Stop it! This is not the place!
[9] PLAN
[10] ?
[11] You might be a girl too
[12] (continues 11) Is it a girl?
[13] Oh, yeah... but it's true...
[14] Huh
[15] Show me your face
[16] ?
[17] Closing eyes, even if you're not smart, there's still someone who will buy it...
[18] Just a little bit...!']
```

**continuation, seed 9**

```text
[1] Mafia sucking mouth is mostly done
[2] GG
[3] Ah
[4] Closing eyes is the guard dog?
[5] Shot in the waist, what's the brain? Let's see it?
[6] ?
[7] Ah, that...
[8] Stop, here!
[9] PLAN
[10] ?
[11] You too, maybe?
[12] (continues 11) Girl?
[13] Oh, yeah... but...
[14] Hoho
[15] Show me your face
[16] Oh
[17] Closing eyes, even if you're not smart, you can buy...
[18] Just a little...! ]<tool_call>
```

### Register marks with a French and a Brazilian Portuguese target

**bourei-p04, French, register prompt** (the Brazilian Portuguese prompt differs only in the language name; the `none` prompt drops the second sentence and the marks)

```text
Translate each Japanese line to French. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (polite), (casual) or (rough) mark after its tag: how formally the speaker talks. It is not part of the text, so do not copy it into your reply.
[1] (polite) 死んでるんですよねぇ
[2] (polite) 「視える」人間ってヤツでして
[3] ようこそ亡霊葬儀屋さん 吉良いと
---
[4] え?
[5] (polite) 亡くなったお姉さんのお化粧を?
[6] (polite) はい…
[7] 烏丸葬儀社社長 烏丸 柩(からすま くるる) 25歳
[8] 葬儀依頼人 桜野 すずめ 19歳
```

**tojime-dense-action, French, register prompt** (the Brazilian Portuguese prompt differs only in the language name; the `none` prompt drops the second sentence and the marks)

```text
Translate each Japanese line to French. Keep the [id] tag before each line. Panels are separated by ---. A line may carry a (polite), (casual) or (rough) mark after its tag: how formally the speaker talks. It is not part of the text, so do not copy it into your reply.
[1] (rough) マフィア舐めんのも大概にしやがれ
[2] グッ
[3] あっ
[4] (rough) 閉じ眼が用心棒だ?
---
[5] (rough) 腰の銃でイかれた脳みそぶちまけてみるか?
[6] (rough) あ?
[7] あ、あの…
[8] (casual) ちょっとやめてよ!こんなとこで!
[9] プラーン
---
[10] ん?
---
[11] (rough) お前もしかして
[12] (rough) 女か?
[13] (polite) えっはい…そうですけど…
---
[14] ほぉ
---
[15] (rough) 面見せてみろ
[16] えっ
[17] (rough) 閉じ眼でも器量が良けりゃ買い手は…
[18] (casual) やっちょっと…!
```

**bourei-p04, French, none, seed 7**

```text
[1] Je suis mort.
[2] "Les gens que l'on voit" sont des êtres.
[3] Bienvenue chez le cérémonialiste de morts, bonjour !
[4] ?
[5] L'apothéose de ma sœur ?
[6] Oui...
[7] Le président de la société de cérémonial des urnes, Kusumakura Kururin, 25 ans.
[8] Le cérémonialiste de morts, Sakurae Suzzume, 19 ans.
```

**bourei-p04, French, none, seed 8**

```text
[1] Je suis mort.
[2] "Les gens que l'on voit" sont des êtres.
[3] Bienvenue chez le cérémonial des morts, Monsieur Kusumura.
[4] ?
[5] L'apothéose de ma sœur ?
[6] Oui...
[7] Le président de la société de cérémonies de l'ombre, Kusumura Kusumura, 25 ans.
[8] Le cérémonial de la tombe, Suzu, 19 ans.
```

**bourei-p04, French, register, seed 7**

```text
[1] (polite) Je suis en train de mourir
[2] (polite) "Les gens que l'on peut voir" sont des gens
[3] (rough) Bienvenue chez le cérémonie de dépoussiérage de fantômes, bonjour
[4] ?
[5] (polite) Avez-vous vu l'odeur de l'odeur de la mort de votre tante ?
[6] (polite) Oui...
[7] (polite) Le président de l'entreprise de dépoussiérage de fantômes, Ouchin, 25 ans
[8] (polite) Le cérémonier de la demande de dépoussiérage, Sazume, 19 ans.
```

**bourei-p04, French, register, seed 8**

```text
[1] (polite) Je suis mort
[2] (polite) "Les gens que l'on peut voir" sont des gens
[3] (rough) Bienvenue chez le cérémonial des morts, bonjour
[4] ?
[5] (polite) L'odeur de la mort de la sœur de monsieur
[6] (polite) Oui...
[7] (polite) Le président de l'Ushigome Cérémonial
[8] (polite) Le cérémonial de l'Ushigome, Sakurae Suzuki, 19 ans.
```

**bourei-p04, Brazilian Portuguese, none, seed 7**

```text
[1] Estou morto, hein?
[2] "Os que podem ser vistos" são esses, hein?
[3] Olá, fantasma funeral house, good to meet you!
[4] ?
[5] A makeup of the dead sister?
[6] Sim...
[7] O dono da Urasu Funeral House, Urasu Kururin, 25 anos
[8] O solicitante de funeral, Sakurae Suzume, 19 anos.
```

**bourei-p04, Brazilian Portuguese, none, seed 8**

```text
[1] Estou morto, né
[2] "Os humanos que podem ser vistos" são eles
[3] Olá, fantasma funerário, bom dia
[4] ?
[5] A maquiagem da minha irmã falecida?
[6] Sim...
[7] O chefe da Ushiro Funeral Service, Ushiro Kusumaru, 25 anos
[8] O solicitante de funeral, Sakurae Suzuki, 19 anos.
```

**bourei-p04, Brazilian Portuguese, register, seed 7**

```text
[1] (polite) Estou morto, hein?
[2] (polite) "Vizível" pessoa, hein?
[3] (rough) Olá, fantasma funeral house, good to meet you
[4] ?
[5] (polite) O maestrinha da minha tia que morreu?
[6] (polite) Sim...
[7] (polite) Mr. Kusumasa, funeral house, good to meet you
[8] (polite) Miss Sakura, funeral house, good to meet you. I am 19 years old. I am the one who is requesting the funeral. I am Kusunomiya Suzume. I am 19 years old.
```

**bourei-p04, Brazilian Portuguese, register, seed 8**

```text
[1] (polite) Está morto, hein?
[2] (polite) "Os que se podem ver" são esses, hein?
[3] (rough) Olá, morto, hein? Que bom!
[4] (polite) Oh?
[5] (polite) A maquiagem da tia morta?
[6] (polite) Sim...
[7] (polite) O Sr. Kusumasa, dono da firma de enterros, 25 anos
[8] (polite) A dona Sakura, 19 anos, solicitou o funeral.
```

**tojime-dense-action, French, none, seed 7**

```text
[1] Le mafieux qui se léchette n'est pas un peu
[2] G'gu
[3] Ah
[4] Fermez les yeux, c'est un couteau!
[5] Avec le couteau au côté du torse, je peux voir ce que ça donne?
[6] Ah?
[7] Ah, ah...
[8] Arrête, c'est pas le moment!
[9] PLAN
[10] N'importe quoi?
[11] Tu sais peut-être...
[12] Fille?
[13] Ah, oui... mais...
[14] Hohoho
[15] Montrons-nous!
[16] Ah?
[17] Fermez les yeux, mais tu as une intelligence!
[18] Un peu...!
```

**tojime-dense-action, French, none, seed 8**

```text
[1] Le mafieux qui me suche n'est pas un gars
[2] Grrrr
[3] Ah
[4] Ferme les yeux, tu es un couteau à sang
[5] Avec un fusil sur la cuisse, tu peux-je te montrer?
[6] Ah?
[7] Ah, c'est vrai...
[8] Arrête, c'est pas la peine!
[9] PLAN
[10] ?
[11] Tu sais, peut-être...
[12] Fille?
[13] Ah, oui... mais...
[14] Hohoho
[15] Montrons-nous les dents
[16] Ah?
[17] Ferme les yeux, mais tu as un bon esprit, tu peux le vendre
[18] Ah, un peu...!
```

**tojime-dense-action, French, register, seed 7**

```text
[1] (rude) Je ne veux pas être touché par la mafia.
[2] Grrr
[3] Ah
[4] (rude) Est-ce que tu te caches derrière un masque de cerveau ?
[5] (rude) Tu as l'air d'avoir un arme cachée dans ton ventre ?
[6] (rude) Ah ?
[7] Ah, ah...
[8] (casual) Arrête, c'est pas grave !
[9] PLAN
[10] ?
[11] (rude) Tu penses quoi ?
[12] (rude) Tu es une fille ?
[13] (polite) Oui, mais... c'est vrai ?
[14] Hohoho
[15] (rude) Montrons-nous nos poches !
[16] (rude) Ah ?
[17] (rude) Si tu te caches derrière un masque, tu as une intelligence supérieure, alors tu peux acheter quoi que ce soit !
[18] (casual) Ah, ah... ! (rude) ! (casual) Ah, ah... ! (rude) ! (casual) Ah, ah... ! (rude) ! (casual) Ah, ah... ! (rude) ! (casual) Ah, ah... ! (rude) ! (casual)Ah
```

**tojime-dense-action, French, register, seed 8**

```text
[1] (rude) Je ne veux pas être touché par la mafia
[2] Grrrrrr
[3] Ah
[4] (rude) Est-ce que tu veux que je ferme les yeux pour t'attaquer?
[5] (rude) Tu as des chances si tu es armé
[6] (rude) Ah?
[7] Ah, ah...
[8] (casual) Arrête, c'est pas le moment!
[9] PLAN
[10] ?
[11] (rude) Tu sais quoi?
[12] (rude) Tu es une fille?
[13] (polite) Oui, mais... c'est vrai...
[14] Hohoho
[15] (rude) Montrons-nous la face
[16] (rude) Ah?
[17] (casual) Montrons-nous la face, alors?
[18] (casual) Ah, ah, ah! (j'aime bien) ! (j'adore) ! (j'adore) ! (j'adore) ! (j'adore) ! (j'adore) ! (j'adore) ! (j'adore) ! (j'adore) ! (j'adore) ! (j'adore) ! (j'
```

**tojime-dense-action, Brazilian Portuguese, none, seed 7**

```text
[1] O começo da mafia é geralmente um estupro
[2] GG
[3] Ah
[4] Fechando os olhos é o meu guarda-chuva?
[5] Usando um tiro na perna do abdômen, como vai?
[6] Ah?
[7] Ah, aquilo…
[8] Tá, vamos parar por enquanto! Nesse lugar!
[9] PLAN
[10] N?
[11] Você também pode ser?
[12] Mulher?
[13] Ah, sim... mas...
[14] Huh
[15] Mostra a cara
[16] Ah?
[17] Fechando os olhos, também pode ser um bom negócio...
[18] Tá, um pouco...!
```

**tojime-dense-action, Brazilian Portuguese, none, seed 8**

```text
[1] O que você está fazendo com o nariz domafia?
[2] Gua
[3] Ah
[4] Fechando os olhos é o seu guardião?
[5] Usando o tiro na cintura para ver o cérebro?
[6] Ah?
[7] Ah, aquele…
[8] Pode parar um pouco! Isso não é lugar para isso!
[9] Plano
[10] N?
[11] Você também pode ser?
[12] Mulher?
[13] Ah, sim…mas…
[14] Huh
[15] Mostre o rosto para eu ver.
[16] Ah?
[17] Fechando os olhos, também é uma habilidade.
[18] Um pouco…!
```

**tojime-dense-action, Brazilian Portuguese, register, seed 7**

```text
[1] (rough) A mafia toshin ni mo ageru
[2] GG
[3] Ah
[4] (rough) Fechar os olhos é um guarda-chuva?
[5] (rough) Com um tiro na cintura, verá se o cérebro está vivo?
[6] (rough) Ah?
[7] Ah, ah…
[8] (casual) Tchau! Não é assim que se fala!
[9] PLAN
[10] ?
[11] (rough) Você também, de jeito nenhum?
[12] (rough) Mulher?
[13] (polite) Ah, ah... Sim, mas...
[14] Hohoho
[15] (rough) Mostre o rosto
[16] Ah?
[17] (rough) Fechar os olhos também pode ser uma habilidade?
[18] (casual) Ah, ah...! " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " " 
```

**tojime-dense-action, Brazilian Portuguese, register, seed 8**

```text
[1] (rough) A mafia tá na boca deles, geralmente.
[2] GG
[3] Ah
[4] (rough) Fechando os olhos é um ateu?
[5] (rough) Sei lá, com um tiro na cabeça, o cérebro fica lá pra fora?
[6] (rough) Ah?
[7] Ah, ah…
[8] (casual) Vamos parar! Não está na hora!
[9] PLÁN
[10] N?
[11] (rough) Você também, né?
[12] (rough) Mulher?
[13] (polite) Ah, ah... Sim, mas...
[14] Hoh
[15] (rough) Mostra o rosto.
[16] Ah?
[17] (rough) Fechando os olhos, o seu juízo é bom?
[18] (casual) Vamos, um pouco...!
```

## Appendix D: the real emulator OCR

Source: page reports from the `sdk_gphone16k_arm64` emulator, Qwen2.5-1.5B, Japanese → English, fresh runs (`recalled=false`), Photos full-screen with its toolbars hidden. Rows are in prompt order, which is the order of the `[id]` lines in `reply.txt`. OCR ids are detection ids. "Hand" names the Appendix A bubble(s) a read came from. `mean p` and `min p` are the replica decoder's token probabilities (§7). The three bourei reads marked † were read differently by the replica than by the app (both garbled); their probabilities are the replica's.

### balloon-dense-dialogue (real OCR)

| OCR id | OCR text (app) | status | hand | mean p | min p | gate: d1-3B | gate: d1-omni | gate: Julia-1 | gate: Qwen | Qwen's reply (app run) | my verdict on the reply | judge: d1-3B | judge: Qwen |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10 | こいつ絶対バカだし | exact | [2] こいつ絶対バカだし | 1.000 | 0.996 | 0.72 | 0.16 | 0.00 | no | This guy is definitely dumber than a box of rocks | yes | 0.73 | yes |
| 9 | むぐっ | exact | [3] むぐっ | 1.000 | 0.999 | 0.58 | 0.22 | 0.00 | no | mu-guu | yes | 0.63 | no |
| 4 | 大体なんで使い捨てカメラなんだよ! | exact | [6] 大体なんで使い捨てカメラなんだよ! | 1.000 | 0.999 | 0.78 | 0.56 | 0.04 | no | It's probably because he's a disposable camera | no | 0.27 | yes |
| 14 | よくある | garbled | [7] すご～くよく写るンズ | 0.879 | 0.398 | 0.64 | 0.34 | 0.91 | no | It's common | garbage | 0.93 | yes |
| 1 | 地球の写真なんてネット上にいくらでも落ちてるだろ | exact | [12] 地球の写真なんてネット上にいくらでも落ちてるだろ | 1.000 | 0.996 | 0.83 | 0.29 | 0.00 | no | Earth's photos are all over the internet, right? | yes | 0.84 | yes |
| 11 | ったく | exact | [10] ったく | 0.998 | 0.990 | 0.61 | 0.33 | 0.99 | no | What the hell | yes | 0.78 | no |
| 2 | 確かに原理的には可能かもしれないけどな... | exact | [11] 確かに原理的には可能かもしれないけどな… | 0.999 | 0.983 | 0.90 | 0.31 | 1.00 | no | I think it's possible, but... | yes | 0.81 | yes |
| 13 | なにあ然としてんの!?ヘリウム入れないと風船は浮かねーよ!! | merged | [4] なにあ然としてんの!?, [5] ヘリウム入れないと風船は浮かねーよ!! | 0.977 | 0.540 | 0.77 | 0.50 | 0.03 | no | What the hell!? You can't float a balloon without helium! | yes | 0.86 | yes |
| 5 | 宇宙でフィルム巻いてシャッターきる気か!? | exact | [9] 宇宙でフィルム巻いてシャッターきる気か!? | 0.999 | 0.991 | 0.80 | 0.43 | 0.00 | no | Can you even take a film roll and shutter in space?! | yes | 0.75 | yes |
| 8 | あっ安かったので... | exact | [8] あっ安かったので… | 0.996 | 0.961 | 0.72 | 0.47 | 0.24 | no | Ah, it was too cheap... | yes | 0.70 | yes |
| 7 | ああ | exact | [14] ああ | 0.990 | 0.970 | 0.75 | 0.27 | 1.00 | no | Ah | yes | 0.96 | yes |
| 3 | わざわざ自分で撮らなくたって... | exact | [13] わざわざ自分で撮らなくたって… | 1.000 | 0.996 | 0.81 | 0.34 | 1.00 | no | Why would you even take a picture yourself? | yes | 0.78 | yes |
| 6 | それはですね... | exact | [15] それはですね… | 1.000 | 0.999 | 0.85 | 0.69 | 1.00 | no | That's right... (pause) I'm sorry, I didn't mean to say that. I'm just a little drunk. (laughs) I'm sorry. (pause) I'm sorry. (pause) I'm sorry. (pause) I'm so, | no | 0.49 | yes |

Hand bubbles with no OCR read: [1] ただ空気いれた風船をいくら集めても浮くわけねーだろ!!.

### bourei-p04 (real OCR)

| OCR id | OCR text (app) | status | hand | mean p | min p | gate: d1-3B | gate: d1-omni | gate: Julia-1 | gate: Qwen | Qwen's reply (app run) | my verdict on the reply | judge: d1-3B | judge: Qwen |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 3 | 死んでるんですよねぇ | exact | [1] 死んでるんですよねぇ | 0.988 | 0.868 | 0.70 | 0.33 | 0.00 | yes | "He's dead, you know" | yes | 0.73 | yes |
| 6 | 前回!!これは!?? | extra | — (art) | 0.398 † | 0.066 | 0.70 | 0.32 | 1.00 | yes | "Last time!! This!!?" | garbage | 0.82 | yes |
| 5 | 参加者総合社長簿九和のらすまくるるし25歳 | garbled | [7] 烏丸葬儀社社長 烏丸 柩(からすま くるる) 25歳 | 0.738 † | 0.239 | 0.58 | 0.19 | 1.00 | yes | "The general manager of the participants' comprehensive register, Nitori, 25 years old" | garbage | 0.65 | yes |
| 1 | 「視える」人間ってヤツでして | exact | [2] 「視える」人間ってヤツでして | 0.996 | 0.966 | 0.68 | 0.26 | 0.99 | yes | "People who can be seen are you, right?" | no | 0.66 | yes |
| 2 | 亡くなったお姉さんのお化粧を? | exact | [5] 亡くなったお姉さんのお化粧を? | 1.000 | 1.000 | 0.77 | 0.66 | 0.40 | yes | "What about my sister who died? Her makeup?" | no | 0.66 | yes |
| 7 | え? | exact | [4] え? | 0.976 | 0.953 | 0.68 | 0.56 | 0.26 | no | "Eh?" | yes | 0.91 | no |
| 4 | はい... | exact | [6] はい… | 0.981 | 0.886 | 0.87 | 0.69 | 1.00 | yes | "Yes..." | yes | 0.96 | yes |
| 8 | ようこそビエ募集屋さん高校いと | garbled | [3] ようこそ亡霊葬儀屋さん 吉良いと | 0.819 † | 0.208 | 0.51 | 0.23 | 1.00 | yes | "Welcome to the recruitment office, high school student" | garbage | 0.81 | yes |

Hand bubbles with no OCR read: [8] 葬儀依頼人 桜野 すずめ 19歳.

### tojime-dense-action (real OCR)

| OCR id | OCR text (app) | status | hand | mean p | min p | gate: d1-3B | gate: d1-omni | gate: Julia-1 | gate: Qwen | Qwen's reply (app run) | my verdict on the reply | judge: d1-3B | judge: Qwen |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 5 | マフィア舐めんのも大概にしやがれ | exact | [1] マフィア舐めんのも大概にしやがれ | 0.996 | 0.947 | 0.60 | 0.20 | 0.00 | no | It's about time the mafia licked my ass. | no | 0.62 | yes |
| 1 | 腰の銃でイかれた脳みそぶちまけてみるか?あ? | merged | [5] 腰の銃でイかれた脳みそぶちまけてみるか?, [6] あ? | 0.987 | 0.731 | 0.66 | 0.46 | 0.22 | yes | Let's see if you're the one that got my brain between the ribs? | no | 0.50 | yes |
| 7 | お前もしかして | exact | [11] お前もしかして | 1.000 | 1.000 | 0.60 | 0.31 | 0.08 | no | Are you? | yes | 0.59 | yes |
| 4 | ちょっとやめてよ!こんなとこで! | exact | [8] ちょっとやめてよ!こんなとこで! | 0.989 | 0.814 | 0.87 | 0.67 | 1.00 | no | Stop it! This is not the place! | yes | 0.94 | yes |
| 11 | あ、あの... | exact | [7] あ、あの… | 0.999 | 0.992 | 0.76 | 0.57 | 1.00 | no | Ah, that... | yes | 0.88 | yes |
| 8 | 女か? | exact | [12] 女か? | 0.997 | 0.986 | 0.66 | 0.53 | 0.93 | no | Is it a girl? | yes | 0.90 | yes |
| 3 | えっはい...そうですけど... | exact | [13] えっはい…そうですけど… | 1.000 | 0.997 | 0.91 | 0.31 | 0.99 | no | Oh, yes... but... | yes | 0.49 | yes |
| 13 | まお | garbled | [14] ほぉ | 0.783 | 0.641 | 0.72 | 0.26 | 1.00 | no | Momosha | garbage | 0.61 | no |
| 14 | ここ | extra | — (text-free balloon `……`) | 0.567 | 0.202 | 0.39 | 0.48 | 1.00 | no | Here | garbage | 0.91 | yes |
| 10 | あっ | exact | [3] あっ | 0.997 | 0.990 | 0.70 | 0.28 | 1.00 | no | Ah | yes | 0.90 | yes |
| 9 | 面見せてみろ | exact | [15] 面見せてみろ | 1.000 | 0.998 | 0.84 | 0.34 | 1.00 | no | Show me your face | yes | 0.72 | yes |
| 6 | 閉じ眼が用心棒だ? | exact | [4] 閉じ眼が用心棒だ? | 1.000 | 0.999 | 0.75 | 0.53 | 0.27 | no | Is it that my eyes are the dagger of the mafia? | no | 0.55 | yes |
| 15 | ん? | exact | [10] ん? | 1.000 | 0.999 | 0.62 | 0.59 | 0.99 | no | Nn? | yes | 0.77 | no |
| 2 | 閉じ眼でも器量が良けりゃ買い手は... | exact | [17] 閉じ眼でも器量が良けりゃ買い手は… | 1.000 | 0.997 | 0.73 | 0.44 | 0.99 | no | Even if you close your eyes, you have the potential to be bought. | yes | 0.67 | yes |
| 12 | やっちょっと...! | exact | [18] やっちょっと…! | 0.996 | 0.970 | 0.79 | 0.34 | 1.00 | no | Just a little...! | no | 0.83 | yes |

Hand bubbles with no OCR read: [2] グッ, [9] プラーン, [16] えっ.

### Qwen2.5-1.5B with and without the OCR-confidence gate

Desktop llama.cpp `7182ef7`, CPU, the app's sampler and grammar, seeds 7, 8 and 9. The prompt is the app's own, rebuilt from each page report with a port of `ContextAssembler`'s panel grouping; the port reproduces the `reply.txt` order on all three pages. "Gated" drops the reads whose mean token probability is below 0.95.

**balloon-dense-dialogue: shipped prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.
[10] こいつ絶対バカだし
[9] むぐっ
[4] 大体なんで使い捨てカメラなんだよ!
[14] よくある
[1] 地球の写真なんてネット上にいくらでも落ちてるだろ
[11] ったく
[2] 確かに原理的には可能かもしれないけどな...
[13] なにあ然としてんの!?ヘリウム入れないと風船は浮かねーよ!!
[5] 宇宙でフィルム巻いてシャッターきる気か!?
[8] あっ安かったので...
[7] ああ
[3] わざわざ自分で撮らなくたって...
[6] それはですね...
```

**balloon-dense-dialogue: gated prompt** (drops [14])

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.
[10] こいつ絶対バカだし
[9] むぐっ
[4] 大体なんで使い捨てカメラなんだよ!
[1] 地球の写真なんてネット上にいくらでも落ちてるだろ
[11] ったく
[2] 確かに原理的には可能かもしれないけどな...
[13] なにあ然としてんの!?ヘリウム入れないと風船は浮かねーよ!!
[5] 宇宙でフィルム巻いてシャッターきる気か!?
[8] あっ安かったので...
[7] ああ
[3] わざわざ自分で撮らなくたって...
[6] それはですね...
```

**balloon-dense-dialogue, shipped, seed 7** (5349 ms, 229 prompt / 195 reply tokens)

```text
[10] This guy is definitely dumber than dirt
[9] muuuu
[4] It's probably because it's a disposable camera
[14] It's common
[1] Earth's photos are all over the internet, right?
[11] What the hell
[2] I guess it's theoretically possible, but...
[13] What the hell! Without helium, a balloon won't float!
[5] Can you film in space with film rolls?!
[8] It was cheap, so...
[7] Aaah
[3] Why would you even take a picture yourself?
[6] That's right... (pause) ...I guess? (pause) ...I'm sorry? (pause) ...I'm sorry? (pause) ...I'm sorry? (pause) ...I'm sorry? (pause) ...I'm sorry? (pause) ...I'm
```

**balloon-dense-dialogue, shipped, seed 8** (5043 ms, 229 prompt / 184 reply tokens)

```text
[10] This guy is definitely a dolt
[9] mu-gg
[4] It's probably because it's a disposable camera
[14] It's common
[1] Earth's photos are all over the internet, right?
[11] F*ck off
[2] I guess it's theoretically possible, but...
[13] What the hell! Without helium, a balloon won't float!
[5] Is it possible to shoot film in space?!
[8] I was just being cheap
[7] Aaah
[3] Why would you even take a picture yourself?
[6] That's right... (pause) ...I was just being cheap. (pause) ...I was just being cheap. (pause) ...I was just being cheap. (pause) ...I was just being cheap. (pa
```

**balloon-dense-dialogue, shipped, seed 9** (5153 ms, 229 prompt / 195 reply tokens)

```text
[10] This guy is definitely dumber than dirt.
[9] Mugu
[4] It's just a disposable camera, basically.
[14] It's common.
[1] Earth's photos are all over the internet, right?
[11] What the heck.
[2] Indeed, theoretically it's possible, but...
[13] What the hell! Without helium, the balloon won't float!
[5] Is there any chance of actually taking a film roll and pressing the shutter in space?
[8] It was cheap, so...
[7] Aaah
[3] Why would you even take a picture yourself?
[6] That's right... (pause) ... (end of scene) (cut to credits) (fade to black) (end of scene) (fade to black) (end of scene) (fade to black) (end of scene) (fade) 
```

**balloon-dense-dialogue, gated, seed 7** (4582 ms, 220 prompt / 181 reply tokens)

```text
[10] This guy is definitely dumber than dirt
[9] Mugu
[4] It's probably because it's a disposable camera
[1] Earth's photos are all over the internet, right?
[11] F*ck off
[2] Indeed, theoretically, it's possible, but...
[13] What the hell! Without helium, the balloon won't float!
[5] Have you ever thought of taking a roll of film and pressing the shutter in space?!
[8] Oh, I was just being cheap
[7] Ah
[3] Why would you even take a picture yourself?
[6] That's right... (pause) I was being cheap. I didn't want to use a disposable camera. I wanted to take a real photo. I wanted to take a real photo of the Earth. 
```

**balloon-dense-dialogue, gated, seed 8** (4782 ms, 220 prompt / 183 reply tokens)

```text
[10] He's definitely a dummy
[9] muuuu
[4] It's basically disposable cameras for me!
[1] Earth's photos are all over the internet, right?
[11] F*ck off
[2] Indeed, theoretically it's possible, but...
[13] What the hell!? Without helium, balloons won't float!
[5] Do you have the guts to film in space?!
[8] Ah, it was cheap...
[7] Ah
[3] Why would you take it yourself if you could just buy it?
[6] That's right... (pause) ... (end of line) (pause) ... (end of line) (pause) ... (end of line) (pause) ... (end of line) (pause) ... (end of line) (pause) ... (n
```

**balloon-dense-dialogue, gated, seed 9** (4607 ms, 220 prompt / 187 reply tokens)

```text
[10] He's definitely a dolt
[9] muuuu
[4] It's just a disposable camera, anyway!
[1] Earth's photos are all over the internet, right?
[11] Darn
[2] Indeed, theoretically it's possible, but...
[13] What the hell! Without helium, balloons won't float!
[5] Can you shoot with film in space?!
[8] Ah, it was cheap...
[7] Ah
[3] Why would you take it yourself if you don't have to?
[6] That's right... (pause) ... (end) (laughs) (pause) (end) (laughs) (pause) (end) (laughs) (pause) (end) (laughs) (pause) (end) (laughs) (pause) (end) (laughs) (1
```

**bourei-p04: shipped prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.
[3] 死んでるんですよねぇ
[6] 前回!!これは!??
[5] 参加者総合社長簿九和のらすまくるるし25歳
---
[1] 「視える」人間ってヤツでして
[2] 亡くなったお姉さんのお化粧を?
[7] え?
[4] はい...
---
[8] ようこそビエ募集屋さん高校いと
```

**bourei-p04: gated prompt** (drops [6], [5], [8])

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.
[3] 死んでるんですよねぇ
---
[1] 「視える」人間ってヤツでして
[2] 亡くなったお姉さんのお化粧を?
[7] え?
[4] はい...
```

**bourei-p04, shipped, seed 7** (3212 ms, 161 prompt / 126 reply tokens)

```text
[3] "He's dead, you know."
[6] "Last time!! This!!?"
[5] The General Manager of the Participant's Comprehensive Social Club, Nishimura, is 25 years old.
[1] "Visible" people, you know.
[2] The makeup of the deceased aunt?
[7] ?
[4] Yes...
[8] Welcome to the recruitment office, high school student. (The line is incomplete and seems to be a joke or a typo) "ビエ" is likely a misspelling of "ビール" (beer) -
```

**bourei-p04, shipped, seed 8** (2595 ms, 161 prompt / 103 reply tokens)

```text
[3] "He's dead, you know."
[6] "Last time!! This!!?"
[5] "The General Manager of the Participant's Comprehensive Social Club, Kusukusu, 25 years old."
[1] "The visible person, you know."
[2] "What about the makeup of the late aunt?"
[7] "Eh?"
[4] "Yes..."
[8] "Welcome to the recruitment office of the Bi-e High School."
```

**bourei-p04, shipped, seed 9** (3413 ms, 161 prompt / 122 reply tokens)

```text
[3] "You're dead, aren't you?"
[6] "Last time!! This!!?"
[5] Chairman of the Participants' Comprehensive Social Secretary, Ninoshima, 25 years old
[1] "Visible" people are you, aren't you?
[2] The makeup of the deceased aunt?
[7] ?
[4] Yes...
[8] Welcome to the recruitment office, high school student. (Ninoshima, 25 years old) - This is a translation of the Japanese text, but the exact meaning and tone, 
```

**bourei-p04, gated, seed 7** (3457 ms, 107 prompt / 138 reply tokens)

```text
[3] You're dead, aren't you?
[1] "Visible" people are you, aren't they?
[2] What about your sister's makeup after she died?
[7] E?
[4] Yes... (pause) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -
```

**bourei-p04, gated, seed 8** (3356 ms, 107 prompt / 135 reply tokens)

```text
[3] You're dead, aren't you?
[1] "Visible" people are you, aren't they?
[2] What about your sister's makeup?
[7] E?
[4] Yes... (pause) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -_-; (sigh) -
```

**bourei-p04, gated, seed 9** (2431 ms, 107 prompt / 82 reply tokens)

```text
[3] You're dead, aren't you?
[1] "Visible" people are you, aren't they?
[2] What about your sister's makeup?
[7] E?
[4] Yes... (pause) ... (end) - This indicates a dramatic or serious situation, possibly involving a death or a serious event. The speaker seems to be in a state of愕
```

**tojime-dense-action: shipped prompt**

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.
[5] マフィア舐めんのも大概にしやがれ
[1] 腰の銃でイかれた脳みそぶちまけてみるか?あ?
[7] お前もしかして
[4] ちょっとやめてよ!こんなとこで!
[11] あ、あの...
[8] 女か?
[3] えっはい...そうですけど...
[13] まお
---
[14] ここ
[10] あっ
[9] 面見せてみろ
---
[6] 閉じ眼が用心棒だ?
[15] ん?
[2] 閉じ眼でも器量が良けりゃ買い手は...
[12] やっちょっと...!
```

**tojime-dense-action: gated prompt** (drops [13], [14])

```text
Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.
[5] マフィア舐めんのも大概にしやがれ
[1] 腰の銃でイかれた脳みそぶちまけてみるか?あ?
[7] お前もしかして
[4] ちょっとやめてよ!こんなとこで!
[11] あ、あの...
[8] 女か?
[3] えっはい...そうですけど...
---
[10] あっ
[9] 面見せてみろ
---
[6] 閉じ眼が用心棒だ?
[15] ん?
[2] 閉じ眼でも器量が良けりゃ買い手は...
[12] やっちょっと...!
```

**tojime-dense-action, shipped, seed 7** (5391 ms, 234 prompt / 201 reply tokens)

```text
[5] Mafia licking my ass is about as likely as it getting me off.
[1] I'm going to see if you're willing to watch me shoot your brain out with a gun to your waist? Are you?
[7] Are you, too?
[4] Stop it! You're not in this kind of situation!
[11] Ah, that...
[8] Is it a girl?
[3] Oh, yeah... but...
[13] Maou
[14] Here
[10] Ah
[9] Show me your face
[6] Are you trying to be a vigilante with your closed eyes?
[15] Nn?
[2] Even with your eyes closed, you have enough brains to buy me.
[12] Just a little...! ](This is a translation of the Japanese text provided. The content is a series of statements and questions in Japanese, and the English text I
```

**tojime-dense-action, shipped, seed 8** (4029 ms, 234 prompt / 158 reply tokens)

```text
[5] Mafia sucking my cock is about as common as it gets.
[1] Are you going to watch me rip out your brain and shoot it?!
[7] Are you, too?
[4] Stop it! This is not the place!
[11] Ah, that...
[8] Is it a girl?
[3] Uh... yes, but...
[13] Maou
[14] Here
[10] Ah
[9] Show me your face
[6] Are you so blind that you can't even see the dagger in my eye?
[15] Nn?
[2] Closed eyes don't make you any smarter, do they?
[12] Just a little...!
```

**tojime-dense-action, shipped, seed 9** (4006 ms, 234 prompt / 154 reply tokens)

```text
[5] Mafia sucking my cock is about as likely as it getting wet.
[1] Are you going to see my brain if I shoot you with a gun to the waist?!
[7] Are you... maybe?
[4] Stop it! This is not the place!
[11] Ah, that...
[8] Is it a girl?
[3] Oh, yeah... but...
[13] Ma~o
[14] Here
[10] Ah
[9] Show me your face
[6] Are you blind?!
[15] Nn?!
[2] Closed eyes can be good even if they're not good.
[12] Just a little...!
```

**tojime-dense-action, gated, seed 7** (3654 ms, 219 prompt / 131 reply tokens)

```text
[5] I'm not going to lick the mafia's ass.
[1] Are you going to see the brain in your waist-shot?
[7] Are you, maybe?
[4] Stop it! This is too far!
[11] Ah, that...
[8] Is it a girl?
[3] Oh, yeah... but...
[10] Ah...
[9] Show me your face.
[6] Are you the eyes that protect yourself?
[15] What?
[2] Even if you close your eyes, you have talent.
[12] Just a little...! ```ascii
```

**tojime-dense-action, gated, seed 8** (4917 ms, 219 prompt / 204 reply tokens)

```text
[5] I'm not going to eat the mafia's cock.
[1] Are you going to see if I'm still alive with a gun to my neck? Are you?
[7] Are you too?
[4] Stop it! This is not the place!
[11] Ah, that...
[8] Is it a girl?
[3] Uh... Yes, but...
[10] Ah...
[9] Show me your face.
[6] Are you a spy with your eyes closed?
[15] What?
[2] With your eyes closed, you can still be a good buyer.
[12] Let's just do it...! (pause)! (pause)! (pause)! (pause)! (pause)! (pause)! (pause)! (pause)! (pause)! (pause)! (pause)! (pause)! (pause)! (pause)! (pause)! (end
```

**tojime-dense-action, gated, seed 9** (3766 ms, 219 prompt / 139 reply tokens)

```text
[5] I'm not going to lick the mafia's anus.
[1] Are you going to see if I'm still alive with a gun to the back of my head?!
[7] Are you, too?
[4] Stop it! This is not the place!
[11] Ah, that's it...
[8] Is it a girl?
[3] Oh, yeah... but...
[10] Ah...
[9] Show me your face!
[6] Are you blind?!
[15] What?
[2] Even if you close your eyes, you have talent!
[12] Just a little...!
```
