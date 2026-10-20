# Coding Standards

Review checks every diff against each rule below. CI enforces the mechanical ones, so review skips them: no TODO or FIXME comments, no unused imports, and in main sources no `!!` and no file past 300 lines (`./gradlew detekt`, `scripts/check-code-standards.sh`).

## Code

1. **Type everything.** No `Any`, no unchecked casts.
2. **Comments carry the why.** KDoc states a function's contract. An inline comment states only a reason the code can't show.
3. **Follow existing patterns.** A new file reads like its neighbours.
4. **Files are focused.** One responsibility per file.
5. **No dead code.** No commented-out code.

## Tests

1. **Test the behavior, not the implementation.** A test's name says what should happen.
2. **Every public function has a test.**
