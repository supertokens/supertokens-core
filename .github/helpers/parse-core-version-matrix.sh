#!/usr/bin/env bash

set -Eeuo pipefail

if [[ -z "${GITHUB_OUTPUT:-}" ]]; then
  echo "GITHUB_OUTPUT is required" >&2
  exit 1
fi

versions="$(
  printf '%s' "${CORE_VERSIONS:-}" \
    | tr ',\n' '\n\n' \
    | sed 's/[[:space:]]//g' \
    | jq -R 'select(length > 0)' \
    | jq -sc .
)"

if [[ "$versions" == "[]" ]]; then
  echo "No versions parsed from input" >&2
  exit 1
fi
if [[ "$(jq '[.[] | test("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")] | all' <<< "$versions")" != "true" ]]; then
  echo "Every version must use X.Y.Z format" >&2
  exit 1
fi
if (( $(jq length <<< "$versions") > 256 )); then
  echo "A batch cannot contain more than 256 versions" >&2
  exit 1
fi

echo "Versions to process: ${versions}"
echo "matrix={\"version\":${versions}}" >> "$GITHUB_OUTPUT"
