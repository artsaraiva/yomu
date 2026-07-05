# Yomu evaluation datasets

Curated, real-world manga cases used to measure two things that must not regress:

1. **Bubble detection recall** — every readable text box must be detected. A single
   missed bubble breaks the reading experience, so the target is effectively 100%
   recall on the accepted case set.
2. **Translation quality** — Japanese OCR text must become coherent English.
   Track artifacts (e.g. `SEP`), incoherent output, and untranslated/source-language
   output.

These datasets are the source of truth for judging detector and translation-engine
changes. Do not tune thresholds or swap engines without checking against them.

## Layout

```
eval/
├── README.md
├── bubble-detection/
│   ├── SCHEMA.md            # case format spec
│   └── cases/               # one directory per case
└── translation-quality/
    ├── SCHEMA.md            # case format spec
    └── cases/               # one directory per case
```

## How to add a case

1. Reproduce the failure during real usage (screenshot the page).
2. Create a new case directory under the relevant `cases/` folder using a short
   descriptive id, e.g. `cases/small-bottom-right-bubble/`.
3. Add the required files described in that dataset's `SCHEMA.md`.
4. Prefer capturing hard cases: small bubbles, close/overlapping bubbles,
   narration boxes, edge-of-screen bubbles, noisy OCR, slang, and known ML Kit
   artifact cases.

## Scope

Phase 0 provides structure and schema only. Automated runners, metrics, and CI
integration are Phase 1 work (see `.slim/deepwork/roadmap/phase-1-translation-engines.md`).
