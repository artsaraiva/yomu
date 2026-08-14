# Custom translation models are allowed, unsupported, and translation-only

Yomu ships a curated translation model that it selects, downloads, and supports, and additionally lets users load their own GGUF from local storage via the Android file picker. Custom models are labelled "Custom — unsupported" in Settings and that label follows through to translation results, so a screenshot of a bad translation is self-diagnosing. The monetized surface is cloud credits, not local model quality, so an open local slot costs no revenue — which is what makes this permissiveness affordable at all.

## Considered Options

- **Curated only (no custom models).** Rejected: nothing to protect. Local translation is free, so restricting it buys no revenue and only frustrates users with capable devices.
- **Fully open — any model, any slot, any source.** Rejected for the reasons in Consequences below.
- **Yomu's own fine-tuned model only.** Not available: no such artifact exists yet. The `yomu-training` repo aims to produce one, but a policy that depends on an unbuilt model is a bet, not a policy. When that model lands it becomes the curated default; it does not change this policy.

## Consequences

**Translation slot only.** Detection and OCR stay curated-only. The slots are not symmetric: translation runs through llama.cpp on GGUF, a self-describing format behind a stable interface, so a wrong model fails loudly or merely translates badly. Detection and OCR are raw ONNX with rigid tensor contracts, and OCR is a coupled triple (`encoder_model.onnx` + `decoder_model.onnx` + `vocab.txt`) where a mismatched vocab yields silent garbage rather than an error. Silent wrongness is the failure mode worth refusing.

**Local file picker only — no URL loading.** Downloading arbitrary remote binaries would make Yomu an unwitting download manager and inherit every network, retry, and integrity failure as a Yomu bug, in exchange for the user not opening a browser. The `checksum` field on `ModelEntity` is meaningless for a file Yomu did not publish.

**Picked files are copied into `filesDir`, not referenced by SAF URI.** The user pays double disk for a 0.5–2 GB file, which is the cheaper complaint than a model that silently vanishes when the source file moves. Copying also makes a custom model an ordinary `ModelEntity` row, so the sideload path stops being a special case everywhere downstream of the picker.

**Validation warns, it does not block.** The GGUF header is parsed (ARM's `GgufMetadataReader`, already vendored under `ml/llama.cpp/examples/llama.android`) to reject non-GGUF files and to predict weight footprint from parameter count and quantization. When the prediction does not fit the device, Yomu shows the math and lets the user proceed anyway. The failure this catches is the one ADR readers will not guess: OOM does not surface as an error. All pipeline components are Hilt `@Singleton`s held resident together, so the LLM loads on top of roughly 750 MB of detection and OCR already in memory, and an oversized model produces a silent app kill. Any hard threshold would be wrong for someone's device, and blocking is incoherent for a slot deliberately labelled unsupported.

**No sandboxing.** A GGUF is data, not executable code, and the user picks it off their own storage — the same trust boundary as any app opening a user-chosen file. The residual risk is a parser CVE in llama.cpp; the mitigation is keeping the submodule current, not a sandbox.

**Implementation note.** `PipelineModule` currently bakes the model path into a compile-time constant inside a `@Singleton` provider (`provideLlamaModelPath`). This decision requires that path to become runtime-selected.
