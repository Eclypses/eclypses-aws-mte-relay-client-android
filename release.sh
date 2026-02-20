#!/bin/bash

set -euo pipefail

# Release workflow note:
# - This repository uses squash merges to master.
# - Releases are created from develop.
# - Preflight checks are intentionally simple: branch, clean tree, and "not behind origin/develop".
# - Complex divergence handling was removed to match this workflow.

# Usage: ./release.sh 4.2.7

# --- CONFIGURATION ---
REPO_URL="https://github.com/Eclypses/eclypses-aws-mte-relay-client-android"
SETTINGS_PATH="relay/src/main/java/com/mte/relay/RelaySettings.java"
CHANGELOG_PATH="CHANGELOG.md"
README_PATH="README.md"
BUILD_GRADLE_PATH="relay/build.gradle"
# ---------------------

TARGET_BRANCH="develop"

# 0. Git preflight checks
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$CURRENT_BRANCH" != "$TARGET_BRANCH" ]; then
  echo "Error: release must be run from '$TARGET_BRANCH' (current: '$CURRENT_BRANCH')."
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "Error: working tree is not clean. Commit/stash changes before running release.sh."
  exit 1
fi

git fetch origin "$TARGET_BRANCH" --prune

if ! git merge-base --is-ancestor "origin/$TARGET_BRANCH" HEAD; then
  echo "Error: local '$TARGET_BRANCH' is behind origin/$TARGET_BRANCH. Run: git pull --ff-only origin $TARGET_BRANCH"
  exit 1
fi

# 1. Validation
if [ -z "${1:-}" ]; then
  echo "Error: No version supplied."
  echo "Usage: ./release.sh <new_version>"
  echo "Example: ./release.sh 2.0.0"
  exit 1
fi

# STRIP 'v' if the user accidentally typed it (e.g. v2.0.0 -> 2.0.0)
CLEAN_VERSION="${1#v}"
TAG_VERSION="v$CLEAN_VERSION"
DATE=$(date +%Y-%m-%d)

if git rev-parse -q --verify "refs/tags/$TAG_VERSION" >/dev/null; then
  echo "Error: tag '$TAG_VERSION' already exists locally."
  exit 1
fi

if git ls-remote --tags origin | grep -q "refs/tags/$TAG_VERSION$"; then
  echo "Error: tag '$TAG_VERSION' already exists on origin."
  exit 1
fi

echo "🚀 Preparing release: $TAG_VERSION on $DATE"

# 2. Update RelaySettings.java
# Looks for: static String relayVersion = "..."
sed -i '' "s/static String relayVersion = \".*\";/static String relayVersion = \"$CLEAN_VERSION\";/" "$SETTINGS_PATH"

# 3. Update build.gradle version parameters
# Looks for: versionName "..."
sed -i '' "s/versionName \".*\"/versionName \"$CLEAN_VERSION\"/" "$BUILD_GRADLE_PATH"
# Looks for: version = '...' (inside publishing block)
sed -i '' "s/version = '.*'/version = '$CLEAN_VERSION'/" "$BUILD_GRADLE_PATH"

# 4. Update README.md version parameter
# Looks for: implementation 'com.eclypses:eclypses-aws-mte-relay-client-android-release:...'
sed -i '' "s/implementation 'com.eclypses:eclypses-aws-mte-relay-client-android-release:.*'/implementation 'com.eclypses:eclypses-aws-mte-relay-client-android-release:$CLEAN_VERSION'/" "$README_PATH"
# Looks for: implementation("com.eclypses:eclypses-aws-mte-relay-client-android-release:...")
sed -i '' "s/implementation(\"com.eclypses:eclypses-aws-mte-relay-client-android-release:.*\")/implementation(\"com.eclypses:eclypses-aws-mte-relay-client-android-release:$CLEAN_VERSION\")/" "$README_PATH"
# Looks for: eclypses-aws-mte-relay-client-android-release-x.x.x-release.aar
sed -i '' "s/eclypses-aws-mte-relay-client-android-release-.*-release.aar/eclypses-aws-mte-relay-client-android-release-$CLEAN_VERSION-release.aar/" "$README_PATH"

# 5. Update CHANGELOG.md Headers
# Uses tags like [2.0.0] for headers. 
# NOTE: Requires a '## [Unreleased]' section in your CHANGELOG.md to work.
SEARCH="## \[Unreleased\]"
REPLACE="## [Unreleased]\\
\\
### Added\\
-\\
\\
### Changed\\
-\\
\\
### Fixed\\
-\\
\\
\\
## [$CLEAN_VERSION] - $DATE"

sed -i '' "s/$SEARCH/$REPLACE/" "$CHANGELOG_PATH"

# 6. Update CHANGELOG.md Reference Links
# Link format: [2.0.0]: .../releases/tag/v2.0.0
NEW_LINK="[$CLEAN_VERSION]: $REPO_URL/releases/tag/$TAG_VERSION"

echo "" >> "$CHANGELOG_PATH"
echo "$NEW_LINK" >> "$CHANGELOG_PATH"

# 7. Git Operations
echo "📦 Committing changes..."
git add "$SETTINGS_PATH" "$BUILD_GRADLE_PATH" "$CHANGELOG_PATH" "$README_PATH"
git commit -m "chore: bump version to $CLEAN_VERSION"

echo "🏷️  Tagging version $TAG_VERSION..."
git tag -a "$TAG_VERSION" -m "Release version $CLEAN_VERSION"

echo "✅ Done! Validate the changes, then run:"
echo "   git push origin develop $TAG_VERSION"
