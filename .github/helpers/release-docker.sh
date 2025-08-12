#!/bin/bash

set -e

# Check for required arguments
if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <source-image:tag> <target-image:tag>"
  exit 1
fi

SOURCE_IMAGE="$1"
TARGET_IMAGE="$2"

# Platforms to support
PLATFORMS=("linux/amd64" "linux/arm64")
TEMP_IMAGES=()

# Pull, retag, and push platform-specific images
for PLATFORM in "${PLATFORMS[@]}"; do
  ARCH=$(echo $PLATFORM | cut -d'/' -f2)
  TEMP_TAG="${TARGET_IMAGE}-${ARCH}"
  TEMP_IMAGES+=("$TEMP_TAG")

  echo "Pulling $SOURCE_IMAGE for $PLATFORM..."
  docker pull --platform $PLATFORM "$SOURCE_IMAGE"

  echo "Tagging as $TEMP_TAG..."
  docker tag "$SOURCE_IMAGE" "$TEMP_TAG"

  echo "Pushing $TEMP_TAG..."
  docker push "$TEMP_TAG"
done

# Create and push manifest for multi-arch image
echo "Creating and pushing multi-arch manifest for $TARGET_IMAGE..."
docker manifest create "$TARGET_IMAGE" "${TEMP_IMAGES[@]}"
docker manifest push "$TARGET_IMAGE"

echo "✅ Multi-arch image pushed as $TARGET_IMAGE"
