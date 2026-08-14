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
6. **no session links.** This repo is public. Never put a `claude.ai/code/session_...` URL in a commit trailer, PR body, or issue — it is an account-scoped identifier that is a dead link for every reader, and git history is permanent. Generic tool attribution (`🤖 Generated with Claude Code`) is fine. Claude Code emits the session trailer by default; suppress it with `attribution: {"commit": "", "pr": ""}` in `settings.json`.

### Phase, Issue, and Branch Workflow

`docs/roadmap/README.md` maps each phase to its GitHub milestone. Read it before planning any non-trivial work. Do not guess the phase or milestone.

| Situation | Required action |
|---|---|
| Any phase-scoped feature, bugfix, refactor, or research task | Create a real GitHub issue first → assign its roadmap milestone → create a focused branch → PR closes the issue |
| Bug found during device testing or review | Create issue in the owning phase milestone before implementation |
| New follow-up discovered during a PR | Create a separate issue in the owning milestone; do not expand the current PR unless user explicitly approves it |
| Infrastructure work (CI, Dependabot, review, security) | Create issue in the Infrastructure milestone before implementation |
| Trivial typo or one-line documentation correction | Direct branch/PR is allowed; no issue required |

**Required sequence for phase work:**
1. Read `docs/roadmap/README.md` and the owning phase doc.
2. Create an issue with the behavior, acceptance criteria, and a link to the phase doc.
3. Assign the issue to the matching GitHub milestone before creating the branch.
4. Create one focused branch for that issue: `feat/<issue-number>-<short-description>` or `fix/<issue-number>-<short-description>`.
5. Keep the PR focused. Its body must contain `Closes #<issue-number>` and mention the phase/milestone.
6. Self-review the PR diff, run required verification, then request review.

**Rules:**
- Issues track real unfinished work; do not create retrospective placeholder issues merely to populate completed milestones.
- Close completed milestones from commit/PR evidence when no real follow-up work remains.
- One issue normally maps to one branch and one PR. Split unrelated work into separate issues/PRs.
- Use label `future` only for work intentionally deferred beyond the current delivery window.
- For this repository, use `github-personal_*` tools and the `artsaraiva/yomu` milestones; never use the work-account GitHub tools.

### GitHub Workflow

1. **Remote:** `git@github-personal:artsaraiva/yomu.git` (SSH alias for personal account)
2. **Git identity:** `Arthur Saraiva <arthur.m.saraiva@hotmail.com>` (set per-repo, not global)
3. **Flow:** branch → commit → push → PR → review → merge to `main`
4. **PR creation:** Use `github-personal_*` MCP tools (authenticated as `artsaraiva`). Do NOT use `github_*` tools (work account) for this repo.
5. **PR review:** Self-review the diff before requesting human review. Use `github-personal_pull_request_read` with `get_diff` and `get_files`.

### Device Testing (Android MCP)

When a physical device or emulator is connected, use the `android-mcp_*` tools to validate changes on-device:

1. **Check device:** `android-mcp_execute_adb_shell_command` with `getprop ro.product.model`
2. **Launch app:** `am start -n com.yomu.app/.MainActivity`
3. **Inspect UI:** `android-mcp_get_uilayout` for clickable elements, or `uiautomator dump /dev/tty` for full hierarchy
4. **Take screenshots:** `android-mcp_get_screenshot` (note: some models can't read images — use UI layout dump as fallback)
5. **Interact:** `input tap <x> <y>`, `input swipe <x1> <y1> <x2> <y2>`, `input keyevent KEYCODE_BACK`
6. **Logcat:** `logcat -s OverlayService:* FloatingButtonOverlay:*` for targeted debugging
7. **Service state:** `dumpsys activity services com.yomu.app` to check if foreground service is running

**When to test on-device:**
- After overlay/service changes (can't be unit-tested)
- After UI changes (Compose layout, new screens)
- After pipeline changes that affect rendering
- When the user reports a visual bug

**When NOT to test on-device:**
- Pure logic changes (typesetter math, cache, parsing) — unit tests suffice
- Build/config changes — `assembleDebug` is enough

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

## Agent skills

### Issue tracker

GitHub (`artsaraiva/yomu`) via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context — `CONTEXT.md` + `docs/adr/` at repo root. See `docs/agents/domain.md`.

