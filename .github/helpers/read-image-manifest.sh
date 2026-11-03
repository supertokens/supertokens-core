#!/usr/bin/env bash
# Reads the release manifest a core image carries in its labels (written by build-core-image).
# Usage: read-image-manifest.sh <registry/repo> <tag>
# Prints key=value lines (for $GITHUB_OUTPUT): image-ref (repo@digest), digest, core-version,
# core-sha, plugin-interface-version, plugin-interface-sha, postgresql-plugin-version,
# postgresql-sha, has-db-migration, tests-run-id.
# Fails if the tag is missing, a label is missing, or the platforms disagree.
# Needs crane and jq, and a registry login.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <registry/repo> <tag>" >&2
  exit 2
fi
repo="$1" tag="$2"

digest="$(crane digest "$repo:$tag")" || { echo "::error::$repo:$tag not found" >&2; exit 1; }
ref="$repo@$digest"

labels_for() { crane config "$ref" --platform "$1" | jq -S '.config.Labels // {}'; }
amd64="$(labels_for linux/amd64)"
arm64="$(labels_for linux/arm64)"
if [ "$amd64" != "$arm64" ]; then
  echo "::error::$ref: linux/amd64 and linux/arm64 carry different labels" >&2
  exit 1
fi

label() {
  local v
  v="$(jq -r --arg k "$1" '.[$k] // empty' <<<"$amd64")"
  if [ -z "$v" ] && [ "${2:-required}" = "required" ]; then
    echo "::error::$ref has no '$1' label; it was not built by build-core-image" >&2
    exit 1
  fi
  printf '%s' "$v"
}

# Assign first: a failing $(label ...) inside an echo would not stop the script.
core_version="$(label org.opencontainers.image.version)"
core_sha="$(label org.opencontainers.image.revision)"
pi_version="$(label com.supertokens.plugin-interface.version)"
pi_sha="$(label com.supertokens.plugin-interface.revision)"
pg_version="$(label com.supertokens.postgresql-plugin.version)"
pg_sha="$(label com.supertokens.postgresql-plugin.revision)"
has_db_migration="$(label com.supertokens.has-db-migration optional)"
tests_run_id="$(label com.supertokens.release.tests-run-id optional)"

cat <<OUT
image-ref=$ref
digest=$digest
core-version=$core_version
core-sha=$core_sha
plugin-interface-version=$pi_version
plugin-interface-sha=$pi_sha
postgresql-plugin-version=$pg_version
postgresql-sha=$pg_sha
has-db-migration=$has_db_migration
tests-run-id=$tests_run_id
OUT
