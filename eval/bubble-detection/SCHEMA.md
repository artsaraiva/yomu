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
- `label`: one of `speech`, `narration`, `sfx`. **Not annotation** — OpenMantra has no class field
  and `generate-cases.py:label_for()` guesses from substrings. Nothing gates on it, the harness
  reports no per-class breakdown, and it exists only to keep the schema stable. Do not read
  meaning into it.

## Generated files (gitignored, written by `run-benchmark.sh`)

### `actual.json` / `actual_s.json`
On-device detector output. `actual.json` is the incumbent yolo26n; `actual_s.json` is the yolo26s
candidate (#57), written only when its weights asset was staged. Each box adds `conf` (post-NMS
confidence), and the object carries `detect_ms` (per-page wall-clock) and `nms_thresholded` /
`nms_kept` (pre- vs post-NMS box counts). `score-detector-comparison.py` reads both files to rank
the detectors under the #33 rule; `run-eval.py` scores `actual.json` alone and ignores the extra
keys.

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
