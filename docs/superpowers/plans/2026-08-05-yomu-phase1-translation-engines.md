# Yomu Phase 1 — Translation Engines

> **Spec:** `.slim/deepwork/roadmap/phase-1-translation-engines.md`
> **Date:** 2026-08-05
> **Status:** Approved (brainstorm decisions below)

## Brainstorm Decisions

1. **LLM strategy:** Repair Qwen3 1.7B decode timeout first (KV-cache, threads, deadline). Replace only if unrecoverable.
2. **OPUS-MT vs LLM:** Parallel lanes.
3. **Engine selector UI:** Both — Settings holds the selector, Models shows assets for the selected engine (grouped by capability, already partially done).
4. **Async render:** OCR-first, async-replace with English.
5. **Eval:** Build minimal eval dataset first (5-10 page pairs) so engine comparison is evidence-based.

## Work Graph

```
A. Eval dataset (independent, background) ──┐
B. Translation cache (independent) ──────────┤
C. Qwen LLM repair (independent, risky) ────┤── E. Engine selector UI
D. OPUS-MT ONNX (independent) ──────────────┤── F. Async render (OCR-first)
G. Warm-model cache (depends on C) ─────────┘
```

## Lanes

### Lane A — Minimal eval dataset
- Collect 5-10 manga page pairs (JA original + EN reference) from public sources.
- Extend `eval/bubble-detection` + `eval/translation-quality` scaffolding.
- Output: runnable comparison script scoring engines on the dataset.
- Owner: @librarian (research sources) → @fixer (harness).

### Lane B — Translation cache
- Persist normalized OCR text → translation keyed by `engineId:modelId`.
- Room table or file cache. Normalize: trim, collapse whitespace, NFKC.
- Hit = skip model entirely.
- Owner: @fixer.

### Lane C — Qwen LLM repair
- Diagnose decode timeout root cause (native abort, blank return).
- Investigate: KV-cache reuse across bubbles, thread count, context reuse, decode deadline, quantization.
- Fix or explicitly mark experimental.
- Owner: @oracle (diagnosis) → @fixer (implementation).

### Lane D — OPUS-MT ONNX
- Integrate `Xenova/opus-mt-ja-en` (encoder/decoder ONNX + tokenizer.json + config.json).
- Implement seq2seq decode loop in `OnnxRuntime`.
- Wire as `TranslationBridge` impl.
- Owner: @librarian (research) → @fixer (implementation).

### Lane E — Engine selector UI
- Settings: dropdown/radio for engine (ML Kit / OPUS-MT / LLM).
- Models: group by capability, show only assets for selected engine.
- ML Kit labeled as temporary baseline.
- Owner: @designer (UI) → @fixer (wiring). Depends on B + engine abstraction.

### Lane F — Async render (OCR-first)
- Overlay renders OCR Japanese immediately.
- Replaces with English when translation completes (per-bubble).
- Owner: @fixer. Depends on translation pipeline.

### Lane G — Warm-model cache
- Keep loaded LLM instance across runs; avoid cold load every manual tap.
- Persistent/warm context, KV-cache reuse.
- Owner: @fixer. Depends on C.

## Execution Order

1. **Now (parallel background):** A (research), B (cache), C (diagnose), D (research).
2. **After research returns:** dispatch @fixer for B, C-impl, D-impl.
3. **After B + engine abstraction stable:** E (UI design + wiring).
4. **After translation pipeline stable:** F (async render).
5. **After C-impl:** G (warm cache).
6. **Gate:** engine comparison on eval dataset before picking default.

## Acceptance (from spec)

- [ ] User can select ML Kit, OPUS-MT, or LLM engine.
- [ ] ML Kit labeled as temporary baseline.
- [ ] LLM either translates at acceptable latency or marked experimental.
- [ ] Cache prevents repeated calls for identical OCR text under same engine/model.
- [ ] Manual overlay still works with selected engine.
- [ ] No engine blocks forever during download or translation.
- [ ] Engine changes validated against eval dataset before becoming default.

## Verification

- `./gradlew test` green.
- `./gradlew lint` clean.
- `./gradlew assembleDebug` builds.
- Eval harness runs and scores engines.
- Manual overlay end-to-end with each engine.