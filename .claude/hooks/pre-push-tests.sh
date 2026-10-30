#!/usr/bin/env bash
# PreToolUse(Bash) gate: before the agent runs `git push`, run the shared unit tests.
# Exit 2 blocks the tool call and feeds stderr back to the agent so it fixes the failure first.
set -uo pipefail

command=$(jq -r '.tool_input.command // empty')

# Match `git push` anywhere in the command, including `git -C dir push` and chains like `git commit && git push`.
if ! grep -qE '(^|[;&|[:space:]])git([[:space:]]+-[^[:space:]]+([[:space:]]+[^-[:space:]][^[:space:]]*)?)*[[:space:]]+push([[:space:]]|$)' <<<"$command"; then
    exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}" || exit 2

if output=$(./gradlew testAndroidHostTest --continue --console=plain 2>&1); then
    exit 0
fi

{
    echo "Push blocked: unit tests failed (./gradlew testAndroidHostTest). Fix them before pushing."
    echo
    grep -E "> .* FAILED$|Error|Exception|expected|There were failing tests" <<<"$output" | grep -v "^> Task" | head -60
} >&2
exit 2
