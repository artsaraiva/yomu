## Yomu Project — Agent Instructions

This file governs all AI agent behavior in this repository.

### Superpowers: Always On

1. **Check skills BEFORE any action.** For every task, check available_skills first. Even 1% relevance means load the skill.
2. **brainstorming before build.** Any feature, bugfix, or change must go through brainstorming (design → approve → plan → implement) unless it's a trivial one-line fix.
3. **writing-plans before code.** After design approval, always invoke writing-plans to create an implementation plan. Then execute via subagent-driven-development or executing-plans.
4. **verification-before-completion.** Never claim a task is done without running lint, tests, and build. Evidence before assertions.

### Git Conventions

1. **worktrees.** All feature work happens in a separate git worktree, not the main checkout.
2. **branch per feature.** Branch name: `feat/<short-description>`. One branch, one purpose.
3. **commits.** Use Conventional Commits. Subject ≤50 chars. Body only when "why" isn't obvious. Example:
   ```
   feat: add bubble detection ONNX wrapper
   
   Uses YOLOv11 Nano via ONNX Runtime Mobile. Outputs bounding boxes with confidence scores.
   ```
4. **no force push.** Never rewrite pushed history.
5. **commit after every passing test.** Each green test = one commit.

### Code Standards

1. **Type everything.** No `Any`, no unchecked casts, no `!!` unless unavoidable.
2. **No comments.** Code explains itself. Only document the "why" when the code can't.
3. **Follow existing patterns.** Look at surrounding files before writing new code.
4. **Files are focused.** One responsibility per file. If a file grows past 300 lines, split it.
5. **No dead code.** No TODOs, no commented-out code, no unused imports.

### Testing

1. **TDD.** Red → Green → Refactor. Always write the failing test first.
2. **Test the behavior, not the implementation.** Use meaningful test names that describe what should happen.
3. **Every public function has a test.**
4. **Run the test before implementation to confirm it fails.** Run it after to confirm it passes.

### Yomu Project Context

- **Stack:** Kotlin, Jetpack Compose, Hilt, Room, ONNX Runtime, llama.cpp
- **Architecture:** Main app + foreground overlay service
- **Phase 1 scope:** Japanese→English, single-page, Android, local-only, system-wide overlay
- **Spec:** `docs/superpowers/specs/2026-01-11-yomu-phase1-design.md`
- **Plan:** `docs/superpowers/plans/2026-01-11-yomu-phase1.md`

### When stuck

1. Read the relevant spec or plan doc first.
2. Graph the codebase: `graphify query "<question>"` if graphify-out/ exists.
3. If a skill exists for the problem, load it.
4. Ask the user for clarification before guessing.
