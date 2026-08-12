# Phase 6 — Automatic Detection

**Status:** Planned
**Owner:** Yomu app
**Milestone:** #16

## Goal
Reduce manual interaction with safe manga-region detection and automatic translation triggers.

## Scope
Detection pulse, region qualification, debouncing, user opt-in, resource limits, and fallback to manual capture. Automatic mode must not obscure the source or loop uncontrollably.

## Exit criteria
Detection behaves predictably on supported content, respects lifecycle and performance limits, and can be disabled without affecting manual translation.

## Dependencies and issue ownership
Depends on the functional path and Phase 5 diagnostics. Detection and app-control issues belong to Yomu.
