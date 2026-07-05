# Translation quality case schema

Each case is a directory: `cases/<case-id>/`.

## Required files

### `source.txt`
Raw Japanese OCR text, one bubble per line (or a full page block). Capture the
actual OCR output, including noise, so cases reflect the real pipeline input.

### `reference.txt`
Acceptable English translation(s), one per line, aligned to `source.txt` lines.
Multiple acceptable references for the same line may be listed on adjacent lines
if the case documents which lines map together in `metadata.json`.

## Optional files

### `page.png`
Source manga page for visual context.

### `metadata.json`
```json
{
  "difficulty": "medium",
  "tags": ["slang", "sfx", "sep_artifact", "untranslated_output"]
}
```

- `difficulty`: `easy`, `medium`, or `hard`.
- `tags`: failure categories or content characteristics. Use `sep_artifact` for
  cases where an engine emitted stray `SEP` tokens, and `untranslated_output`
  where the engine returned source-language text.

### `notes.txt`
Why this case is included; which engine(s) failed and how.

## Metrics this case supports

- Untranslated-output rate.
- Artifact rate (e.g. `SEP`).
- Human-readability judgement.
- Optional exact/near-match against references.

Used to compare ML Kit, OPUS-MT, and the LLM engine on identical inputs before
choosing a Phase 1 default.
