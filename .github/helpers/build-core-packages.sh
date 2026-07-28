#!/usr/bin/env bash

set -Eeuo pipefail

required_variables=(
  CORE_VERSION
  PLUGIN_INTERFACE_VERSION
  POSTGRESQL_VERSION
  S3_BUCKET
)

for variable in "${required_variables[@]}"; do
  if [[ -z "${!variable:-}" ]]; then
    echo "Missing required environment variable: ${variable}" >&2
    exit 1
  fi
done

version_pattern='^[0-9]+\.[0-9]+\.[0-9]+$'
for version in "$CORE_VERSION" "$PLUGIN_INTERFACE_VERSION" "$POSTGRESQL_VERSION"; do
  if [[ ! "$version" =~ $version_pattern ]]; then
    echo "Expected a stable X.Y.Z version, received: ${version}" >&2
    exit 1
  fi
done

S3_JAR_PREFIX="${S3_JAR_PREFIX:-core-jars}"
S3_JRE_PREFIX="${S3_JRE_PREFIX:-core-runtime/jre}"
S3_PACKAGE_PREFIX="${S3_PACKAGE_PREFIX:-packages/v1}"
S3_JAR_PREFIX="${S3_JAR_PREFIX#/}"
S3_JAR_PREFIX="${S3_JAR_PREFIX%/}"
S3_JRE_PREFIX="${S3_JRE_PREFIX#/}"
S3_JRE_PREFIX="${S3_JRE_PREFIX%/}"
S3_PACKAGE_PREFIX="${S3_PACKAGE_PREFIX#/}"
S3_PACKAGE_PREFIX="${S3_PACKAGE_PREFIX%/}"

ROOT_DIR="${ROOT_DIR:-$PWD}"
CORE_DIR="${ROOT_DIR}/supertokens-core"
PLUGIN_INTERFACE_DIR="${ROOT_DIR}/supertokens-plugin-interface"
POSTGRESQL_DIR="${ROOT_DIR}/supertokens-postgresql-plugin"

for path in \
  "${CORE_DIR}/config.yaml" \
  "${CORE_DIR}/install" \
  "${CORE_DIR}/install.bat" \
  "${CORE_DIR}/LICENSE.md" \
  "${POSTGRESQL_DIR}/config.yaml"; do
  if [[ ! -f "$path" ]]; then
    echo "Missing package source file: ${path}" >&2
    exit 1
  fi
done

version_at_least() {
  [[ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -1)" == "$1" ]]
}

if version_at_least "$CORE_VERSION" "11.0.0"; then
  JRE_MAJOR="21"
  JRE_VERSION="21.0.7"
else
  JRE_MAJOR="15"
  JRE_VERSION="15.0.1"
fi

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

CORE_JAR_KEY="${S3_JAR_PREFIX}/supertokens-core/v${CORE_VERSION}/core-${CORE_VERSION}.jar"
CLI_JAR_KEY="${S3_JAR_PREFIX}/supertokens-core/v${CORE_VERSION}/cli.jar"
DOWNLOADER_JAR_KEY="${S3_JAR_PREFIX}/supertokens-core/v${CORE_VERSION}/downloader.jar"
EE_JAR_KEY="${S3_JAR_PREFIX}/supertokens-core/v${CORE_VERSION}/ee.jar"
PLUGIN_INTERFACE_JAR_KEY="${S3_JAR_PREFIX}/supertokens-plugin-interface/v${PLUGIN_INTERFACE_VERSION}/plugin-interface-${PLUGIN_INTERFACE_VERSION}.jar"
POSTGRESQL_JAR_KEY="${S3_JAR_PREFIX}/supertokens-postgresql-plugin/v${POSTGRESQL_VERSION}/postgresql-plugin-${POSTGRESQL_VERSION}.jar"
release_manifest_key="${S3_PACKAGE_PREFIX}/releases/core-${CORE_VERSION}.json"

download_object() {
  local key="$1"
  local destination="$2"

  mkdir -p "$(dirname "$destination")"
  aws s3 cp "s3://${S3_BUCKET}/${key}" "$destination" --only-show-errors
}

object_state() {
  local key="$1"
  local error_file="${WORK_DIR}/head-object-error"

  if aws s3api head-object --bucket "$S3_BUCKET" --key "$key" >/dev/null 2>"$error_file"; then
    printf 'exists'
  elif grep -Eq '\((404|NoSuchKey|NotFound)\)' "$error_file"; then
    printf 'missing'
  else
    cat "$error_file" >&2
    return 1
  fi
}

if [[ "$(object_state "$release_manifest_key")" == "exists" ]]; then
  existing_release_manifest="${WORK_DIR}/existing-core-${CORE_VERSION}.json"
  download_object "$release_manifest_key" "$existing_release_manifest"
  existing_tuple="$(jq -r '[.core, .plugin, .pluginInterface] | join("/")' "$existing_release_manifest")"
  requested_tuple="${CORE_VERSION}/${POSTGRESQL_VERSION}/${PLUGIN_INTERFACE_VERSION}"
  if [[ "$existing_tuple" != "$requested_tuple" ]]; then
    echo "Release ${CORE_VERSION} is already published with tuple ${existing_tuple}" >&2
    exit 1
  fi
fi

package_keys='{}'
platforms=(mac windows linux linux-arm)

for platform in "${platforms[@]}"; do
  if [[ "$platform" == "linux-arm" && "$JRE_MAJOR" == "12" ]]; then
    continue
  fi

  staging="${WORK_DIR}/staging-${platform}"
  package_root="${staging}/supertokens"
  mkdir -p \
    "${package_root}/core" \
    "${package_root}/cli" \
    "${package_root}/downloader" \
    "${package_root}/plugin" \
    "${package_root}/plugin-interface" \
    "${package_root}/jre"

  download_object "$CORE_JAR_KEY" "${package_root}/core/core-${CORE_VERSION}.jar"
  download_object "$CLI_JAR_KEY" "${package_root}/cli/cli.jar"
  download_object "$DOWNLOADER_JAR_KEY" "${package_root}/downloader/downloader.jar"
  download_object "$PLUGIN_INTERFACE_JAR_KEY" "${package_root}/plugin-interface/plugin-interface-${PLUGIN_INTERFACE_VERSION}.jar"
  download_object "$POSTGRESQL_JAR_KEY" "${package_root}/plugin/postgresql-plugin-${POSTGRESQL_VERSION}.jar"

  if [[ "$(object_state "$EE_JAR_KEY")" == "exists" ]]; then
    if [[ ! -f "${CORE_DIR}/ee/LICENSE.md" ]]; then
      echo "The EE JAR exists but ${CORE_DIR}/ee/LICENSE.md is missing" >&2
      exit 1
    fi
    download_object "$EE_JAR_KEY" "${package_root}/ee/ee.jar"
    cp "${CORE_DIR}/ee/LICENSE.md" "${package_root}/ee/LICENSE.md"
  fi

  jre_archive_key="${S3_JRE_PREFIX}/${JRE_VERSION}/${platform}.tar.gz"
  jre_checksum_key="${jre_archive_key}.sha256"
  jre_archive="${WORK_DIR}/${JRE_VERSION}-${platform}.tar.gz"
  jre_checksum="${jre_archive}.sha256"
  download_object "$jre_archive_key" "$jre_archive"
  download_object "$jre_checksum_key" "$jre_checksum"
  expected_jre_checksum="$(cut -d ' ' -f 1 "$jre_checksum")"
  actual_jre_checksum="$(sha256sum "$jre_archive" | cut -d ' ' -f 1)"
  if [[ ! "$expected_jre_checksum" =~ ^[a-fA-F0-9]{64}$ || "$expected_jre_checksum" != "$actual_jre_checksum" ]]; then
    echo "JRE checksum mismatch for ${jre_archive_key}" >&2
    exit 1
  fi
  tar -xzf "$jre_archive" -C "${package_root}/jre"

  if [[ "$platform" == "linux" || "$platform" == "linux-arm" ]]; then
    java_description="$(file -Lb "${package_root}/jre/bin/java")"
    if [[ "$platform" == "linux" && "$java_description" != *"x86-64"* ]]; then
      echo "Expected an x86-64 Linux JRE, found: ${java_description}" >&2
      exit 1
    fi
    if [[ "$platform" == "linux-arm" && "$java_description" != *"aarch64"* ]]; then
      echo "Expected an AArch64 Linux JRE, found: ${java_description}" >&2
      exit 1
    fi
  fi

  install_file="install"
  if [[ "$platform" == "windows" ]]; then
    install_file="install.bat"
  fi
  cp "${CORE_DIR}/${install_file}" "${package_root}/${install_file}"
  chmod 755 "${package_root}/${install_file}"
  cp "${CORE_DIR}/LICENSE.md" "${package_root}/LICENSE.md"
  {
    cat "${CORE_DIR}/config.yaml"
    printf '\n\n'
    cat "${POSTGRESQL_DIR}/config.yaml"
  } > "${package_root}/config.yaml"
  cp "${package_root}/config.yaml" "${package_root}/config.yaml.original"
  cat > "${package_root}/version.yaml" <<EOF
core_version: ${CORE_VERSION}
plugin_interface_version: ${PLUGIN_INTERFACE_VERSION}
plugin_version: ${POSTGRESQL_VERSION}
plugin_name: postgresql
EOF

  for legacy_file in INSTALLATION_INSTRUCTIONS.txt SuperTokensLicense.pdf OpenSourceLicenses.pdf; do
    if [[ -f "${CORE_DIR}/${legacy_file}" ]]; then
      cp "${CORE_DIR}/${legacy_file}" "${package_root}/${legacy_file}"
    fi
  done

  find "$staging" -exec touch -h -t 198001010000 {} +
  zip_path="${WORK_DIR}/supertokens-${platform}.zip"
  (
    cd "$staging"
    find . -print | LC_ALL=C sort | zip -X -y -q "$zip_path" -@
  )

  package_key="${S3_PACKAGE_PREFIX}/core-${CORE_VERSION}/${platform}.zip"
  checksum="$(sha256sum "$zip_path" | cut -d ' ' -f 1)"
  checksum_path="${WORK_DIR}/${platform}.sha256"
  manifest_path="${WORK_DIR}/${platform}.manifest.json"
  printf '%s  %s.zip\n' "$checksum" "$platform" > "$checksum_path"
  jq -n \
    --arg packageKey "$package_key" \
    --arg sha256 "$checksum" \
    --arg core "$CORE_VERSION" \
    --arg plugin "$POSTGRESQL_VERSION" \
    --arg pluginInterface "$PLUGIN_INTERFACE_VERSION" \
    --arg os "$platform" \
    --arg jreVersion "$JRE_VERSION" \
    --arg coreJarKey "$CORE_JAR_KEY" \
    --arg cliJarKey "$CLI_JAR_KEY" \
    --arg downloaderJarKey "$DOWNLOADER_JAR_KEY" \
    --arg pluginInterfaceJarKey "$PLUGIN_INTERFACE_JAR_KEY" \
    --arg postgresqlJarKey "$POSTGRESQL_JAR_KEY" \
    --arg jreArchiveKey "$jre_archive_key" \
    --argjson size "$(stat -c %s "$zip_path")" \
    '{
      packageKey: $packageKey,
      sha256: $sha256,
      size: $size,
      tuple: {
        core: $core,
        plugin: $plugin,
        pluginInterface: $pluginInterface,
        os: $os,
        jreVersion: $jreVersion
      },
      inputs: {
        coreJar: $coreJarKey,
        cliJar: $cliJarKey,
        downloaderJar: $downloaderJarKey,
        pluginInterfaceJar: $pluginInterfaceJarKey,
        postgresqlJar: $postgresqlJarKey,
        jreArchive: $jreArchiveKey
      }
    }' > "$manifest_path"

  aws s3 cp "$manifest_path" "s3://${S3_BUCKET}/${package_key%.zip}.manifest.json" \
    --content-type application/json \
    --only-show-errors
  aws s3 cp "$checksum_path" "s3://${S3_BUCKET}/${package_key%.zip}.sha256" \
    --content-type text/plain \
    --only-show-errors
  aws s3 cp "$zip_path" "s3://${S3_BUCKET}/${package_key}" \
    --content-type application/zip \
    --content-disposition "attachment; filename=supertokens-${CORE_VERSION}-${platform}.zip" \
    --only-show-errors

  package_keys="$(jq -c --arg platform "$platform" --arg packageKey "$package_key" '. + {($platform): $packageKey}' <<< "$package_keys")"
done

release_manifest="${WORK_DIR}/core-${CORE_VERSION}.json"
jq -n \
  --arg core "$CORE_VERSION" \
  --arg plugin "$POSTGRESQL_VERSION" \
  --arg pluginInterface "$PLUGIN_INTERFACE_VERSION" \
  --arg jreVersion "$JRE_VERSION" \
  --argjson packages "$package_keys" \
  '{
    core: $core,
    plugin: $plugin,
    pluginInterface: $pluginInterface,
    jreVersion: $jreVersion,
    packages: $packages
  }' > "$release_manifest"

if [[ "$(object_state "$release_manifest_key")" == "exists" ]]; then
  remote_release_manifest="${WORK_DIR}/remote-core-${CORE_VERSION}.json"
  download_object "$release_manifest_key" "$remote_release_manifest"
  existing_release_tuple="$(jq -r '[.core, .plugin, .pluginInterface] | join("/")' "$remote_release_manifest")"
  requested_release_tuple="${CORE_VERSION}/${POSTGRESQL_VERSION}/${PLUGIN_INTERFACE_VERSION}"
  if [[ "$existing_release_tuple" != "$requested_release_tuple" ]]; then
    echo "Refusing to replace ${release_manifest_key} with a different release tuple" >&2
    exit 1
  fi
fi

aws s3 cp "$release_manifest" \
  "s3://${S3_BUCKET}/${release_manifest_key}" \
  --content-type application/json \
  --only-show-errors
