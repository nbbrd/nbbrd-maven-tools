# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2025-07-02

### Added

- Add parsing of error messages in Maven verify command
- Add option to log errors [#34](https://github.com/nbbrd/nbbrd-maven-tools/issues/34)

### Changed

- Migrate OSSRH to Central Portal

## [1.0.3] - 2025-05-23

### Fixed

- Fix retrieval of artifact version when using wildcards

## [1.0.2] - 2025-05-19

### Fixed

- Fix concurrent access to local project

## [1.0.1] - 2025-05-13

### Fixed

- Fix limits default value in mojos
- Fix sourceBinding default value in mojos
- Fix empty label in MarkdownFormat
- Fix missing BOM module

## [1.0.0] - 2025-05-12

Initial release.

[Unreleased]: https://github.com/nbbrd/nbbrd-maven-tools/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/nbbrd/nbbrd-maven-tools/compare/v1.0.3...v1.1.0
[1.0.3]: https://github.com/nbbrd/nbbrd-maven-tools/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/nbbrd/nbbrd-maven-tools/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/nbbrd/nbbrd-maven-tools/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/nbbrd/nbbrd-maven-tools/compare/develop...v1.0.0
