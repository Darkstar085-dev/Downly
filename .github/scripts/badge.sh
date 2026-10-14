#!/usr/bin/env bash
set -euo pipefail
releases_url="https://api.github.com/repos/${GITHUB_REPOSITORY}/releases?per_page=100"
auth_header=()
if [[ -n "${GITHUB_TOKEN:-}" ]]; then auth_header=(-H "Authorization: Bearer $GITHUB_TOKEN"); fi
curl --fail --silent --show-error \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "${auth_header[@]}" "$releases_url" -o /tmp/downly-releases.json
total=$(jq '[.[].assets[]? | select(.name | endswith(".apk")) | .download_count] | add // 0' /tmp/downly-releases.json)
mkdir -p .github/badges
jq -n --arg total "$total" '{
  schemaVersion: 1,
  label: "APK Downloads",
  message: $total,
  color: "00A982"
}' > .github/badges/downloads.json
