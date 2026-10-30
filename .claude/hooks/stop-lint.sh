#!/usr/bin/env bash
# Stop gate: at the end of each agent turn, lint the Kotlin code with ktlint.
# New violations (anything not in a module's ktlint-baseline.xml) are handed back to the agent to fix.
set -uo pipefail

input=$(cat)

# Already continuing because of this hook: let the turn end rather than loop.
if [[ $(jq -r '.stop_hook_active // false' <<<"$input") == "true" ]]; then
    exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}" || exit 0

# Nothing to lint unless Kotlin sources changed (staged, unstaged or untracked).
if ! git status --porcelain --untracked-files=all | grep -qE '\.kts?$'; then
    exit 0
fi

if output=$(./gradlew ktlintCheck --continue -q 2>&1); then
    exit 0
fi

# Gradle only names the failing reports; read them to get the actual violations.
reports=$(grep -oE '/[^[:space:]]+/build/reports/ktlint/[^[:space:]]+\.txt' <<<"$output" | sort -u)
violations=$(for report in $reports; do cat "$report"; done |
    sed -E 's/\x1b\[[0-9;]*m//g' | grep -E '\.kts?:[0-9]+:[0-9]+' | sed "s#$PWD/##" | sort -u | head -50)
[[ -z $violations ]] && violations=$(tail -30 <<<"$output")

reason="ktlint found new style violations. Run ./gradlew ktlintFormat to auto-fix what it can, then fix the rest by hand:
$violations"

jq -n --arg reason "$reason" '{decision: "block", reason: $reason}'
