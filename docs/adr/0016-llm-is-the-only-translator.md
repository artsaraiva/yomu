# The LLM is the only translator; ML Kit and OPUS-MT are removed

**Status:** accepted, 2026-09-15. Decides [#222](https://github.com/artsaraiva/yomu/issues/222). Supersedes in part [ADR-0008](0008-translation-model-selection.md): its "OPUS-MT and ML Kit stay as optional floor engines" clause.

Yomu removes the ML Kit and OPUS-MT floor engines. A curated LLM deliverable is the only thing that fills the translation slot. Setup downloads the default curated LLM deliverable, Yomu is not ready until the selected deliverable is downloaded, and the translation model is chosen only in Settings. The engine type, the engine role and the stored engine preference go away. What was the engine selection now holds only the LLM deliverable choice and the generation profile. The `TranslationSlot` seam from [ADR-0012](0012-page-translation-slot.md) stays.

## Why

ADR-0008 kept the floors for devices that cannot run an LLM. Neither floor delivered that. OPUS-MT never loaded on a phone: DJL ships no `arm64-v8a` tokenizer library ([#14](https://github.com/artsaraiva/yomu/issues/14)). ML Kit worked, but it asks one bubble at a time, so it could not use the page-level context the product is built on. It also made Play Services a dependency and was the setup default. Every fresh install started on the engine ADR-0008 said should never be the default. Keeping the floors meant an engine switcher, a role enum, two download paths and a Play Services dependency, all for engines nobody should pick. [ADR-0015](0015-quality-belongs-to-the-model.md) also removed the eval, which was the last code that compared against them.

## Considered Options

- **Keep ML Kit as the floor, delete only OPUS-MT.** Rejected. ML Kit is the engine that pulls the setup default and the quick-settings switcher away from the LLM. Keeping it keeps the whole engine abstraction.
- **Fix #14 and keep OPUS-MT as a self-contained floor.** Rejected. It is still a one-bubble-at-a-time engine outside the page-level architecture, and it needs native tokenizer work no current device needs.
- **Remove both; the LLM is the only translator. (Chosen.)**

## Consequences

**No migration.** Nobody uses the app yet. A stored `translation_engine` preference is ignored, and floor-engine files already on a device are not cleaned up. Their rows leave the model registry, so `refreshModelList` drops them.

**ONNX Runtime stays.** Detection and OCR still use it. The DJL tokenizer dependency, which only OPUS-MT used, is removed.

**A device that cannot fit any curated deliverable has no translator.** The fit budget still gates which deliverables are offered, and the default is sized to fit every supported device (ADR-0010).

**Older ADRs and benchmark records stay as history.** ADR-0008 keeps its text, with a status line pointing here.
