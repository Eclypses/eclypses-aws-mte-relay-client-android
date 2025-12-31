#!/bin/bash

# Usage: ./release.sh 4.2.4

# --- CONFIGURATION ---
REPO_URL="https://github.com/Eclypses/eclypses-aws-mte-relay-client-android"
# Corrected Paths for Android Project Structure
SETTINGS_PATH="relay/src/main/java/com/mte/relay/RelaySettings.java"
CHANGELOG_PATH="CHANGELOG.md"
README_PATH="README.md"
BUILD_GRADLE_PATH="relay/build.gradle"
# ---------------------

# 1. Validation
if [ -z "$1" ]; then
  echo "Error: No version supplied."
  echo "Usage: ./release.sh <new_version>"
  echo "Example: ./release.sh 2.0.0"
  exit 1
fi

# STRIP 'v' if the user accidentally typed it (e.g. v2.0.0 -> 2.0.0)
CLEAN_VERSION="${1#v}"
# Migrating to 'v' prefix for tags (e.g. v4.2.4)
TAG_VERSION="v$CLEAN_VERSION"
DATE=$(date +%Y-%m-%d)

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
# Commit message usually uses the clean version or the tag, preference varies.
git commit -m "chore: bump version to $CLEAN_VERSION"

echo "🏷️  Tagging version $TAG_VERSION..."
git tag -a "$TAG_VERSION" -m "Release $CLEAN_VERSION"

echo "✅ Done! Validate the changes, then run:"
echo "   git push && git push --tags"
