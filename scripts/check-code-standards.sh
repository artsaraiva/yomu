#!/usr/bin/env bash
# The mechanical rules from CODING_STANDARDS.md that detekt can't check without type resolution (#380):
# no main-source Kotlin file past 300 lines, and no `!!`. Breaches that predate the check are listed in
# config/code-standards-baseline.txt; fixing one means deleting its line.
set -euo pipefail
cd "$(dirname "$0")/.."

LIMIT=300
BASELINE=config/code-standards-baseline.txt

found="$(
  find app/src/main core/src/main ml/src/main pipeline/src/main -name '*.kt' | sort | while read -r file; do
    if [ "$(wc -l < "$file")" -gt "$LIMIT" ]; then echo "longer-than-$LIMIT $file"; fi
    if grep -qE '[]A-Za-z0-9_)>]!!' "$file"; then echo "not-null-assertion $file"; fi
  done
)"

new="$(comm -13 <(sort "$BASELINE") <(printf '%s\n' "$found" | sed '/^$/d' | sort))"
if [ -n "$new" ]; then
  printf 'Breaks CODING_STANDARDS.md:\n%s\n' "$new" >&2
  exit 1
fi
