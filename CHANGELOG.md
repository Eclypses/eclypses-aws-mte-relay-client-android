All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- Layered JVM unit test suite for contract/model behavior, header bridge boundaries, facade conversion behavior, and throughput/edge scenarios.
- Deterministic test fixtures and hand-written fake codec infrastructure for controlled encode/decode behavior, call history, and lifecycle resets.
- JaCoCo coverage report and verification tasks with configurable minimum line coverage (`-PminLineCoverage`, default `0.40`).

### Changed
- Added a runtime-safe codec seam (`RelayCodec`) and delegated existing `NetworkHeaderHelper` paths through it without changing public runtime behavior.
- Updated Azure Pipeline to run lint and unit tests on all branches, run coverage generation/verification only on `develop`, and skip coverage on `master`.
- Added Android SDK environment resolution and `local.properties` generation for macOS pipeline agents.

### Fixed
- Corrected JaCoCo verification DSL configuration for reliable Gradle coverage gate execution.


## [4.2.6] - 2025-12-31

### Added
-

### Changed
- Edited azure-pipelines.yml to delete dev_docs directory on push to GitHub

### Fixed
-


## [4.2.5] - 2025-12-31

### Added
- Dev_Docs directory with helpful markdown files 

### Changed
- Updated README to provide corrected and implementation details
- Updated pipeline.yaml file to remove dev_docs directory and deploy to GitHub on merge to master

### Fixed
- Removed unnecessary MTE_Version.txt file


## [4.2.4] - 2025-12-30

### Added
- Added release.sh deployment script file

### Changed
-

### Fixed
-


## [4.2.3] - 2025-09-30

### Added

### Changed
- Corrected the way an error message is returned in a Volley Response
- Bumped Version

### Fixed


## [4.2.2] - 2025-09-05

### Added

### Changed
- Added functionality to return error information when a VolleyResponse contains no NetworkResponse
- Bumped Version

### Fixed


## [4.2.1] - 2025-09-05

### Added
- Added public method to process an OkHttp Request/Response

### Changed
- Bumped Version

### Fixed
- Updated Host class to gracefully handle missing Volley Response


## [4.2.0] - 2025-08-21

### Added
- Added public method to process an OkHttp Request/Response  

### Changed
- Bumped Version
- Updated README.md

### Fixed


## [4.1.1] - 2025-05-21

### Added

### Changed
- Bumped Version
- Updated README.md

### Fixed
- Updated Host.java to return a 503 statusCode if RelayServerUrl is bad and we don't get a NetworkResponse from Volley.


## [4.1.0] - 2025-05-16

### Added

### Changed
- Bumped Version
- Added serverUrl and pathnamePrefix to public logging methods
- Changed setFileLoggingEnabled method names to enableFileLogging
- Updated README.md

### Fixed


## [4.0.0] - 2025-05-14

### Added
- Added http (Volley) NetworkResponse to RelayVolleyRequestListener
- Added int statusCode to RelayStreamResponseListener

### Changed
- Bumped Version
- Changed RelayDataTaskListener to RelayVolleyRequestListener

### Fixed
 - General Cleanup


## [3.7.1] - 2025-05-12

### Added
- Added call in relay to set LogToFile to false explicitly to turn off FILE Appender

### Changed
- Bumped Version

### Fixed
 - General Cleanup


## [3.7.0] - 2025-05-12

### Added
- Added Logging to LogCat and File. LogToFile is selectable from Relay.java

### Changed
- Set default pairPoolSize to 5.

### Fixed
 - General Cleanup


[3.7.0]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/v3.7.0
[3.7.1]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/v3.7.1
[4.0.0]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/v4.0.0
[4.1.0]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/v4.1.0
[4.1.1]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/v4.1.1
[4.2.0]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/v4.2.0
[4.2.1]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/v4.2.1
[4.2.2]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/v4.2.2
[4.2.3]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/v4.2.3
[4.2.4]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/v4.2.4
[4.2.5]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/v4.2.5

[4.2.6]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/v4.2.6
