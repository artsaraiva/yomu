# Bubble detection case schema

Each case is a directory: `cases/<case-id>/`.

## Required files

### `page.png`
Source manga page screenshot, exactly as captured on device (full display pixel
space, matching what the detector receives).

### `expected.json`
Ground-truth text-box bounding boxes in captured-image pixel coordinates.

```json
{
  "kind": "story",
  "image_width": 1080,
  "image_height": 2340,
  "boxes": [
    { "x": 120, "y": 340, "w": 260, "h": 180, "label": "speech" },
    { "x": 720, "y": 1980, "w": 150, "h": 120, "label": "narration" }
  ]
}
```

- `kind`: `story` or `cover`. Only `story` cases are in the detection gate; `cover` cases hold
  title typography and author credits and are scored on a separate reported line. Absent means
  `story`.
- `x`, `y`: top-left corner in pixels.
- `w`, `h`: width/height in pixels.
- `label`: one of `speech`, `narration`, `sfx`. **Not annotation, and forbidden as a scoring
  input** — OpenMantra has no class field and `generate-cases.py:label_for()` guesses from
  substrings. It is listed under `forbidden_inputs` in
  [`../eval-contract.json`](../eval-contract.json), so a metric that read it would fail the contract
  check rather than merely be discouraged (#44). It exists only to keep the schema stable.

## Generated output

There are no generated files in this directory. On-device detector output lives in the run
directory's `records.jsonl` as `detection` records, one per arm/case — see
[`../SCHEMA.md`](../SCHEMA.md). The incumbent yolo26n is arm `bubble`; the yolo26s candidate (#57) is
arm `bubble_s`, present only when its weights asset was staged.

A detection record carries the boxes (each with `conf`, the post-NMS confidence), `nms_thresholded` /
`nms_kept`, and the monotonic `duration_ms`. `run-eval.py --run-dir` scores each detection arm;
`score-detector-comparison.py --run-dir` reads both to rank them under the #33 rule.

The old shared `actual.json` / `actual_s.json` files are gone. They were how #58 shipped: a skipped
engine kept a prior run's outputs and was scored as if it had just run.

## Optional files

### `notes.txt`
Free-form context: failure category (missed / suppressed / low-confidence),
device, and why the case matters.

## Metrics this case supports

Scored by containment, per [ADR-0003](../../docs/adr/0003-detection-hit-criterion.md):

- Containment recall (gate): ≥95% of a ground-truth box's area covered by one detection, padded by
  4% of page width per side, one-to-one. Box-weighted over `story` cases only; per-case recalls are
  never averaged.
- Localisation recall (reported, never gated): ground-truth centre inside a detection.
- Merging detections: one detection covering two or more ground-truth centres — a hit for neither.
- False positives.
