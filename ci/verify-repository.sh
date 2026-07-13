#!/usr/bin/env bash
set -euo pipefail

fail() {
    echo "Repository policy violation: $1" >&2
    exit 1
}

if git ls-files --error-unmatch .env.local >/dev/null 2>&1; then
    fail ".env.local must never be tracked"
fi

if git ls-files | grep -Eq '\.(jks|keystore|p12|mobileprovision)$'; then
    fail "signing material must never be tracked"
fi

if git grep -IlE -- '-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----|BAMBUDDY_(BASE_URL|OPENAPI_URL|API_KEY)[[:space:]]*=[[:space:]]*[^[:space:]$<{]+' -- . >/dev/null; then
    fail "a tracked file appears to contain credentials or private-instance configuration"
fi

if grep -Eq '(^|[="[:space:]])(latest\.|[^"[:space:]]*\+|[^"[:space:]]*-SNAPSHOT)' gradle/libs.versions.toml; then
    fail "dynamic or snapshot dependency versions are forbidden"
fi

grep -Eq '^distributionSha256Sum=[0-9a-f]{64}$' gradle/wrapper/gradle-wrapper.properties ||
    fail "the Gradle distribution checksum must be pinned"

while IFS= read -r workflow; do
    while IFS= read -r line; do
        reference=${line#*uses:}
        reference=${reference%%#*}
        reference=$(printf '%s' "${reference}" | xargs)
        if [[ "${reference}" == ./* ]]; then
            continue
        fi
        if [[ ! "${reference}" =~ ^[^@[:space:]]+@[0-9a-f]{40}$ ]]; then
            fail "third-party action references must use immutable 40-character commit SHAs (${workflow})"
        fi
    done < <(grep -E '^[[:space:]]*-?[[:space:]]*uses:' "${workflow}")
done < <(find .github -type f \( -name '*.yml' -o -name '*.yaml' \) | sort)

echo "Repository supply-chain baseline passed."
