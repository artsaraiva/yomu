# Phase 8 — Rendering & Typesetting Quality

**Milestone:** #9

## Goal

Keep every translated bubble legible and complete: no silent clipping, unusably small text, or unreadable overflow.

## Current delivery

- White bubble cover with black, centered wrapped text.
- User font-size scale from 0.5× to 2.0× in Settings and the quick overlay popup.

## Remaining work

- Define a minimum legible device font size.
- Make fitting preserve all text instead of clipping when a translation is too long.
- Improve wrapping/padding and test long-English-in-small-bubble cases.
- Add rendering checks to the evaluation harness where practical.

## Status

In progress. Font preference is delivered; robust no-clipping policy is not.
