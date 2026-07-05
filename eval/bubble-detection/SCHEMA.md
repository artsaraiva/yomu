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
  "image_width": 1080,
  "image_height": 2340,
  "boxes": [
    { "x": 120, "y": 340, "w": 260, "h": 180, "label": "speech" },
    { "x": 720, "y": 1980, "w": 150, "h": 120, "label": "narration" }
  ]
}
```

- `x`, `y`: top-left corner in pixels.
- `w`, `h`: width/height in pixels.
- `label`: one of `speech`, `narration`, `sfx`.

## Optional files

### `notes.txt`
Free-form context: failure category (missed / suppressed / low-confidence),
device, and why the case matters.

## Metrics this case supports

- Recall (missed boxes are regressions; target ~100% on accepted cases).
- False positives.
- Per-page missed-bubble count.
