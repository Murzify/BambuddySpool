#!/usr/bin/env bash
set -euo pipefail

fail() {
    echo "Repository policy violation: $1" >&2
    exit 1
}

if git ls-files --error-unmatch .env.local >/dev/null 2>&1; then
    fail ".env.local must never be tracked"
fi

if git ls-files | grep -Eqi '\.(jks|keystore|p12|p8|pem|key|der|mobileprovision|provisionprofile)$'; then
    fail "signing material must never be tracked"
fi

if git grep -IlE -- '-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----|BAMBUDDY_(BASE_URL|OPENAPI_URL|API_KEY)[[:space:]]*=[[:space:]]*[^[:space:]$<{]+|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|Bearer[[:space:]]+[A-Za-z0-9._~-]{20,}' -- . >/dev/null; then
    fail "a tracked file appears to contain credentials or private-instance configuration"
fi

contract_fixtures=shared/src/commonTest/resources/contracts
if [[ -d "${contract_fixtures}" ]]; then
    if grep -REqi '"(access_code|api_key|ip_address|serial_number|tag_uid|token|tray_uuid)"[[:space:]]*:' "${contract_fixtures}"; then
        fail "contract fixtures must not contain private credential, network, device, or tag fields"
    fi
    if grep -REi 'https?://|(^|[^0-9])([0-9]{1,3}\.){3}[0-9]{1,3}([^0-9]|$)' "${contract_fixtures}" |
        grep -Ev '"(provenance|reference_version)"' >/dev/null; then
        fail "contract fixtures must not contain hosts or network addresses"
    fi
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
