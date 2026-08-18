# The paper-mâché look is rendered procedurally, not from paper-texture image assets

**Status:** accepted, 2026-08-18. Frames the [paper-mâché visual system](../design/paper-mache-visual-system.md) (issue [#19](https://github.com/artsaraiva/yomu/issues/19)).

Yomu's paper-mâché aesthetic is expressed **procedurally** — warm colour, rounded shape, a thin ink border, and a soft offset drop shadow for layering — and **never** through photographic paper-texture image assets tiled or stretched behind surfaces. At most one small tiled grain PNG may be added later if flat surfaces read as too sterile; the identity does not depend on it.

## Why

A texture-asset approach is the obvious way to make an app "look like paper", so a future reader will reasonably wonder why it was avoided — hence this record.

- **On-device budget.** Yomu is an on-device Android app whose memory and APK size already carry the detection, OCR, and translation models. Full-bleed paper-texture bitmaps cost decoded-bitmap memory on every screen and APK weight, for a purely cosmetic layer. Procedural surfaces cost neither.
- **Two modes, cleanly.** The system ships **day paper** and **night paper**. Colour tokens re-theme for free; a photographic texture would need a separately authored (and separately shipped) dark asset, and tends to muddy contrast against the WCAG-AA bar the visual system commits to.
- **No asset pipeline.** Procedural paper is code and tokens — no texture sourcing, licensing, resolution/density sets, or nine-patch tuning.

## Considered options

- **Raster paper-texture assets (rejected).** Most authentic, but pays bitmap memory and APK size on a device already tight from ML models, needs a second dark-mode asset, and risks contrast failures under text.
- **Procedural / flat paper-suggestion (chosen).** Paper *language* through shape, border, layering, and warm colour. Cheap on memory and APK, re-themes across both modes for free, and keeps contrast under our control.

## Consequences

- The visual system's surface language (rounded corners, `edge` border, offset shadow, elevation levels) is the paper — there is no texture layer to fall back on, so those primitives must carry the feel.
- If flatness proves too sterile in practice, the sanctioned escape hatch is **one small tiled grain PNG**, not full-bleed photographic texture — bounded so the memory/theming argument still holds.
- This is a look-and-feel decision only. It does not touch the [typeset bubble](../../CONTEXT.md), whose appearance answers to legibility over live artwork, not to this aesthetic.
