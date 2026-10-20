# Coding Standards

Review checks every diff against each rule below.

## Code

1. **Type everything.** No `Any`, no unchecked casts, no `!!` unless unavoidable.
2. **Comments carry the why.** KDoc states a function's contract. An inline comment states only a reason the code can't show.
3. **Follow existing patterns.** A new file reads like its neighbours.
4. **Files are focused.** One responsibility per file. Split a file once it grows past 300 lines.
5. **No dead code.** No TODOs, no commented-out code, no unused imports.

## Tests

1. **Test the behavior, not the implementation.** A test's name says what should happen.
2. **Every public function has a test.**
