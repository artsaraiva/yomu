# Phase 2 — Observability & Live Logs

**Milestone:** #3

## Goal

Make pipeline behavior understandable on-device without relying solely on `adb logcat`.

## Scope

- In-app, live pipeline events for capture, model readiness, detection, OCR, translation, fallback, cache, and render.
- Copy/share diagnostics for bug reports.
- Clear failure reasons and timing data for each stage.

## Status

Planned. Existing Logcat messages are useful, but no in-app log UI or event store exists.

## Definition of done

- User can view the last translation attempt's stage timeline.
- User can copy/share diagnostic output.
- Logs distinguish real translation, cache hit, fallback, and failure.
