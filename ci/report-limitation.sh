#!/usr/bin/env bash
set -euo pipefail

message=${1:?A limitation message is required}

echo "::warning title=Incomplete CI coverage::${message}"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
        echo "### Incomplete CI coverage"
        echo
        echo "${message}"
    } >> "${GITHUB_STEP_SUMMARY}"
fi
