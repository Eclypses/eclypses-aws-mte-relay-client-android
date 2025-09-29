All notable changes to this project will be documented in this file.

## [4.2.2] - 2025-09-05

### Added

### Changed
- Added functionality to return error information when a VolleyResponse contains no NetworkResponse
- Bumped Version

### Fixed

[4.2.2]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/4.2.2

## [4.2.1] - 2025-09-05

### Added
- Added public method to process an OkHttp Request/Response

### Changed
- Bumped Version

### Fixed
- Updated Host class to gracefully handle missing Volley Response

[4.2.1]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/4.2.1

## [4.2.0] - 2025-08-21

### Added
- Added public method to process an OkHttp Request/Response  

### Changed
- Bumped Version
- Updated README.md

### Fixed

[4.2.0]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/4.2.0
<br><br>

## [4.1.1] - 2025-05-21

### Added

### Changed
- Bumped Version
- Updated README.md

### Fixed
- Updated Host.java to return a 503 statusCode if RelayServerUrl is bad and we don't get a NetworkResponse from Volley.

[4.1.1]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/4.1.1
<br><br>

## [4.1.0] - 2025-05-16

### Added

### Changed
- Bumped Version
- Added serverUrl and pathnamePrefix to public logging methods
- Changed setFileLoggingEnabled method names to enableFileLogging
- Updated README.md

### Fixed

[4.1.0]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/4.1.0
<br><br>

## [4.0.0] - 2025-05-14

### Added
- Added http (Volley) NetworkResponse to RelayVolleyRequestListener
- Added int statusCode to RelayStreamResponseListener

### Changed
- Bumped Version
- Changed RelayDataTaskListener to RelayVolleyRequestListener

### Fixed
 - General Cleanup

[4.0.0]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/4.0.0
<br><br>

## [3.7.1] - 2025-05-12

### Added
- Added call in relay to set LogToFile to false explicitly to turn off FILE Appender

### Changed
- Bumped Version

### Fixed
 - General Cleanup

[3.7.1]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/3.7.1
<br><br>

## [3.7.0] - 2025-05-12

### Added
- Added Logging to LogCat and File. LogToFile is selecteable from Relay.java

### Changed
- Set default pairPoolSize to 5.

### Fixed
 - General Cleanup

[3.7.0]: https://github.com/Eclypses/eclypses-aws-mte-relay-client-android/releases/tag/3.7.0