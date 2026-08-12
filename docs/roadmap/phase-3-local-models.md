# Phase 3 — Local Model Selection & Management

**Status:** Planned
**Owner:** Yomu app
**Milestone:** #13

## Goal
Provide explicit, safe lifecycle management for local translation models.

## Scope
Model metadata, download/import and storage validation, readiness, selection, compatibility, cleanup, and user-visible failure recovery. Use Phase 2 evidence to choose what to support; avoid speculative model catalogs.

## Exit criteria
A supported model can be installed, selected, loaded, used, replaced, and recovered from failure without corrupting user data.

## Dependencies and issue ownership
Depends on Phase 2’s documented decision. App integration issues belong to Yomu; model production remains separate unless accepted by `yomu-training`.
