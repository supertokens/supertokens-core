#!/usr/bin/env bash

set -Eeuo pipefail

required_variables=(CORE_VERSION CORE_DIR PLUGIN_INTERFACE_DIR POSTGRESQL_DIR GITHUB_OUTPUT)
for variable in "${required_variables[@]}"; do
  if [[ -z "${!variable:-}" ]]; then
    echo "Missing required environment variable: ${variable}" >&2
    exit 1
  fi
done

plugin_interface_minor="$(
  git -C "$CORE_DIR" show "v${CORE_VERSION}:pluginInterfaceSupported.json" \
    | jq -r '.versions[0]'
)"
if [[ -z "$plugin_interface_minor" || "$plugin_interface_minor" == "null" ]]; then
  echo "Cannot determine plugin-interface version for core v${CORE_VERSION}" >&2
  exit 1
fi

plugin_interface_version="${INPUT_PLUGIN_INTERFACE_VERSION:-}"
if [[ -z "$plugin_interface_version" ]]; then
  plugin_interface_version="$(
    git -C "$PLUGIN_INTERFACE_DIR" tag \
      | grep -E "^v${plugin_interface_minor}\.[0-9]+$" \
      | sed 's/^v//' \
      | sort -V \
      | tail -1
  )"
fi
if [[ ! "$plugin_interface_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] ||
  [[ "${plugin_interface_version%.*}" != "$plugin_interface_minor" ]]; then
  echo "Plugin-interface version must be a stable ${plugin_interface_minor}.x release" >&2
  exit 1
fi
git -C "$PLUGIN_INTERFACE_DIR" rev-parse --verify "v${plugin_interface_version}^{commit}" >/dev/null

postgresql_version="${INPUT_POSTGRESQL_VERSION:-}"
if [[ -z "$postgresql_version" ]]; then
  while IFS= read -r postgresql_tag; do
    supported_minor="$(
      git -C "$POSTGRESQL_DIR" show "${postgresql_tag}:pluginInterfaceSupported.json" 2>/dev/null \
        | jq -r '.versions[0]' 2>/dev/null || true
    )"
    if [[ "$supported_minor" == "$plugin_interface_minor" ]]; then
      postgresql_version="${postgresql_tag#v}"
      break
    fi
  done < <(git -C "$POSTGRESQL_DIR" tag --sort=-version:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$')
fi
if [[ ! "$postgresql_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "PostgreSQL plugin version must be a stable X.Y.Z release" >&2
  exit 1
fi

postgresql_plugin_interface_minor="$(
  git -C "$POSTGRESQL_DIR" show "v${postgresql_version}:pluginInterfaceSupported.json" \
    | jq -r '.versions[0]'
)"
if [[ "$postgresql_plugin_interface_minor" != "$plugin_interface_minor" ]]; then
  echo "PostgreSQL plugin ${postgresql_version} supports plugin-interface ${postgresql_plugin_interface_minor}, expected ${plugin_interface_minor}" >&2
  exit 1
fi

echo "Plugin-interface minor: ${plugin_interface_minor}"
echo "Plugin-interface version: ${plugin_interface_version}"
echo "PostgreSQL plugin version: ${postgresql_version}"
echo "pi_version=${plugin_interface_version}" >> "$GITHUB_OUTPUT"
echo "pg_version=${postgresql_version}" >> "$GITHUB_OUTPUT"
