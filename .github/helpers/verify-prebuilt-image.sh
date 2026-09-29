#!/usr/bin/env bash
# Checks that a prebuilt (embargoed) image matches what this public release resolved.
# Inputs (env):
#   VERSION                       release version being published
#   IMAGE_VERSION                 org.opencontainers.image.version label
#   IMAGE_CORE_SHA, CORE_SHA      label vs. head of the public core branch   -> must be equal
#   IMAGE_PI_SHA, PI_SHA, PI_REPO label vs. head of the public plugin-interface branch
#   IMAGE_PG_SHA, PG_SHA, PG_REPO label vs. head of the public postgresql-plugin branch
#   TESTS_RUN_ID                  label; must be set (the image was built after its tests passed)
# The plugin commits in the image must be the resolved heads or ancestors of them: plugin
# branches may keep moving during an embargo, and the release tags what was actually built.
set -euo pipefail

fail=0
err() { echo "::error::$*"; fail=1; }

[ "$IMAGE_VERSION" = "$VERSION" ] || err "image is labelled $IMAGE_VERSION, releasing $VERSION"
[ -n "$TESTS_RUN_ID" ] || err "image has no tests-run-id label: it was not built after a passing test run"
[ "$IMAGE_CORE_SHA" = "$CORE_SHA" ] || \
  err "public core branch head is $CORE_SHA but the image was built from $IMAGE_CORE_SHA (push the security branch first, as a fast-forward)"

# is_ancestor <repo> <image-sha> <resolved-head>
is_ancestor() {
  local repo="$1" built="$2" head="$3" dir
  [ "$built" = "$head" ] && return 0
  dir="$(mktemp -d)"
  git -C "$dir" init -q
  # Commit history only (no file contents) of the resolved head.
  git -C "$dir" fetch -q --filter=blob:none "https://github.com/$repo.git" "$head"
  git -C "$dir" cat-file -e "$built^{commit}" 2>/dev/null && git -C "$dir" merge-base --is-ancestor "$built" "$head"
}

is_ancestor "$PI_REPO" "$IMAGE_PI_SHA" "$PI_SHA" || \
  err "$PI_REPO: image commit $IMAGE_PI_SHA is not on the resolved branch (head $PI_SHA)"
is_ancestor "$PG_REPO" "$IMAGE_PG_SHA" "$PG_SHA" || \
  err "$PG_REPO: image commit $IMAGE_PG_SHA is not on the resolved branch (head $PG_SHA)"

if [ "$fail" -ne 0 ]; then exit 1; fi
{
  echo "### Prebuilt image verified"
  echo "| | built from | public head |"
  echo "|---|---|---|"
  echo "| core | \`$IMAGE_CORE_SHA\` | \`$CORE_SHA\` |"
  echo "| plugin-interface | \`$IMAGE_PI_SHA\` | \`$PI_SHA\` |"
  echo "| postgresql-plugin | \`$IMAGE_PG_SHA\` | \`$PG_SHA\` |"
  echo ""
  echo "Tests: run $TESTS_RUN_ID"
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
echo "Prebuilt image matches the public branches."
