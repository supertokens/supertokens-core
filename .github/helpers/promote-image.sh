#!/usr/bin/env bash
# Promotes one image, pinned by digest, to <registry/repo> under the release tag set.
# Usage: promote-image.sh <source@sha256:...> <registry/repo> <version> <is_canary> <is_latest_for_major> <is_latest_release> [force_retag]
#
# Tags:   canary -> <version>-canary only
#         else   -> <version>, <major>.<minor>, plus <major> / latest when flagged
# The version tag is immutable: same digest -> no-op; different digest -> error unless force_retag.
# Floating tags always move. Every tag is read back and must resolve to the source digest.
# Needs crane on PATH and registry logins already in place.
set -euo pipefail

if [ "$#" -lt 6 ] || [ "$#" -gt 7 ]; then
  echo "Usage: $0 <source@sha256:...> <registry/repo> <version> <is_canary> <is_latest_for_major> <is_latest_release> [force_retag]" >&2
  exit 2
fi

src="$1" dst="$2" version="$3" is_canary="$4" latest_for_major="$5" latest="$6" force="${7:-false}"

if [[ "$src" != *@sha256:* ]]; then
  echo "::error::Source must be pinned by digest (<repo>@sha256:...), got '$src'"
  exit 1
fi
want="${src##*@}"

if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error::Version must be X.Y.Z, got '$version'"
  exit 1
fi
major="${version%%.*}"
minor="$(echo "$version" | cut -d. -f1,2)"

floating=()
if [ "$is_canary" = "true" ]; then
  pinned="$version-canary"
else
  pinned="$version"
  floating+=("$minor")
  [ "$latest_for_major" = "true" ] && floating+=("$major")
  [ "$latest" = "true" ] && floating+=("latest")
fi

copy_and_verify() {
  local tag="$1" got
  crane copy "$src" "$dst:$tag"
  got="$(crane digest "$dst:$tag")"
  if [ "$got" != "$want" ]; then
    echo "::error::$dst:$tag resolves to $got after copy, expected $want"
    exit 1
  fi
  echo "✓ $dst:$tag -> $want"
}

# Version tag. Only a definite "not found" counts as absent; any other error (auth, network)
# must not be mistaken for absence, or we could overwrite a published version.
if existing="$(crane digest "$dst:$pinned" 2>&1)"; then
  if [ "$existing" = "$want" ]; then
    echo "= $dst:$pinned already at $want"
  elif [ "$force" = "true" ]; then
    echo "::warning::force-retag: moving $dst:$pinned from $existing to $want"
    copy_and_verify "$pinned"
  else
    echo "::error::$dst:$pinned already exists at $existing, not $want. Published versions are immutable: use a new patch version (or force-retag for an emergency)."
    exit 1
  fi
elif grep -qiE 'MANIFEST_UNKNOWN|NAME_UNKNOWN|not found' <<<"$existing"; then
  copy_and_verify "$pinned"
else
  echo "::error::Could not check $dst:$pinned: $existing"
  exit 1
fi

for tag in "${floating[@]}"; do
  copy_and_verify "$tag"
done
