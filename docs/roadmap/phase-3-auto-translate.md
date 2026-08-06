# Phase 3 — Auto-Translate & Detection

**Milestone:** #4

## Goal

Offer safe, battery-conscious manga detection and optional hands-free translation after manual quality is proven.

## Current status

Blocked. The Settings toggle exists, but it does not drive runtime behavior:

- `FloatingButtonView` has no manga-detected pulse state.
- `UrlMangaDetector` is not wired into the service.
- No screen-change gate or anti-loop protection exists.

## Immediate prerequisite

Resolve [#6](https://github.com/artsaraiva/yomu/issues/6): add a visible `MANGA_DETECTED` state and a lightweight detector strategy.

## Future safety rules

- Never run concurrent translations.
- Do not retranslate a page because Yomu's own overlay changed pixels.
- Use page/bubble-geometry hashing and cooldowns.
- Do not auto-run expensive OCR/translation until engine latency is acceptable.
- Add Phase 2 event logging before enabling unattended translation.
