# Model-specific translation lives behind a page slot

**Status:** accepted, 2026-09-09.

Translation selection returns a **translation slot** instead of implementing the same interface as its selected adapter. The slot accepts one geometry-free page, and each adapter owns its prompt form, batch-versus-per-line strategy, id-keyed parsing, token budget, timeout, readiness, and native lifecycle; the pipeline only projects detected page data, rejects non-translations, applies per-bubble source fallback, and assembles the result. This keeps adding a model to one side of the seam and lets the pipeline resolve the selected slot for every capture.

[ADR-0002](0002-cross-panel-translation-context.md) still governs page context, id addressing, per-bubble fallback, and omission of empty OCR; only the home of those decisions moves below the translation seam. Its statement that the line-keyed cache is bypassed on the LLM path was superseded by [#125](https://github.com/artsaraiva/yomu/issues/125), which removes that cache from every path.
