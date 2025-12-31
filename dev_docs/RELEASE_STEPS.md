# Release Steps

This wiki provides guidelines to bump library version and push **Eclypses-AWS-MTE-Relay-Client-Android** to ADO and GitHub.

## 1. Automated Release Script (`release.sh`)

This project includes a helper script (`release.sh`) to automate version bumping. It updates `RelaySettings.java`, `build.gradle`, `README.md`, and rotates the `CHANGELOG.md` headers.

### Usage
1.  **Update Changelog**: Ensure all your code changes are documented in `CHANGELOG.md` under the `## [Unreleased]` section.
2.  **Run Script**:
    ```bash
    ./release.sh <new_version>
    ```
    *   *Example:* `./release.sh 4.2.5` (The `v` prefix is handled automatically).
3.  **Verify**: Check that the version numbers in the project files have been updated correctly.
4.  **Push**: The script commits the changes and tags the release. You simply need to push.

---

## 2. Release Workflow

After you have run the release script and verified the changes:

1.  **Push Changes**:
    The script has already committed the changes and created the tag. Run:
    ```bash
    git push && git push --tags
    ```

2.  Navigate to the **ADO Repository** and verify the branch and tag:
    *   [https://cos-ado.eclypses.com/DefaultCollection/Eclypses/_git/eclypses-aws-mte-relay-client-android](https://cos-ado.eclypses.com/DefaultCollection/Eclypses/_git/eclypses-aws-mte-relay-client-android)

3.  Merge as necessary. **Merge to the master branch will trigger a CI pipeline to push the Tag to GitHub.**

4.  Navigate to the **GitHub Repository** and verify the public branch and tag:
    *   [https://github.com/Eclypses/eclypses-aws-mte-relay-client-android](https://github.com/Eclypses/eclypses-aws-mte-relay-client-android)

5.  Create a new release from the latest tag on GitHub.

---

## 3. Manual Publish to Maven Central

*Note: The following instructions were adapted from the internal wiki for manually publishing to Maven Central using the NMCP plugin.*

### Preparation
1.  **Active Build Variant**: Ensure the Active Build Variant is set to `release` (View menu > Tool Windows > Build Variants).
2.  **Verify Version Consistency**: The version number must be updated in `module build.gradle` in two locations:
    *   In the `DefaultConfiguration` element.
    *   In the `Publishing` element.
    *   *Note: The `release.sh` script should handle this, but verifying is recommended.*
3.  **Sync Gradle**: Perform a Gradle Sync after updates.

### Build & Package
1.  **Build Project**: Run the build to create the `.aar` in `relay/build/outputs/aar`.
2.  **Generate Maven Artifacts**:
    *   Open **Gradle** menu > **publishing**.
    *   Run `publishReleasePublicationToNmcpReleaseRepository`.
    *   *Note*: This task builds all required files (POM, AAR, Signatures) for Maven Central.
3.  **Compress Artifacts**:
    *   Navigate to `relay/build/nmcp`.
    *   Locate the `com` directory (inside `m2Release`).
    *   Right-click `com` and select **Compress "com"**.
    *   Rename the resulting `com.zip` to `vx.x.x.zip` (matching your version).

### Upload to Sonatype (Maven Central)
1.  **Navigate**: Go to [https://central.sonatype.com/publishing](https://central.sonatype.com/publishing).
2.  **Publish Component**:
    *   Click **Publish Component** (upper right).
    *   **Name**: `Eclypses AWS MteRelay Android Client Library <Version>` (Copy the name format from previous releases).
    *   **Description**: Add a description (e.g., contents of the CHANGELOG).
    *   **Upload**: Add the `vx.x.x.zip` file you created.
    *   Click **Publish Component**.
3.  **Validation**:
    *   Wait for the status to change to **Validating**.
    *   Refresh until it shows **Validated**.
4.  **Publish**:
    *   Once validated, click **Publish** to make it publicly available.
    *   *Warning: Once published, it cannot be removed.*

### Post-Publish Verification
1.  **Open Demo App**:
    *   Navigate to the [Demo Mobile Relay Android](https://cos-ado.eclypses.com/DefaultCollection/Eclypses/_git/demo-mobile-relay-android) repository.
    *   Open the project in **Android Studio**.
2.  **Update Dependency**:
    *   Open the module-level `build.gradle` file.
    *   Update the library dependency to pull the latest Maven version you just uploaded.
3.  **Confirm Operation**:
    *   Sync Gradle and run the app.
    *   Verify that the application functions as expected with the new library.