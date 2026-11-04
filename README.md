# NBBRD Maven tools

[![Download](https://img.shields.io/github/release/nbbrd/nbbrd-maven-tools.svg)](https://github.com/nbbrd/java-service-util/releases/latest)
[![Changes](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fnbbrd%2Fnbbrd-maven-tools%2Fbadges%2Funreleased-changes.json)](https://github.com/nbbrd/java-service-util/blob/develop/CHANGELOG.md)

## Overview

NBBRD Maven tools is a collection of Maven plugins and utilities to support the development of Java projects.

| Module | Description |
|--------|-------------|
| [compatibility-maven-plugin](compatibility-maven-plugin/README.md) | Maven plugin that checks the compatibility between projects, ensuring that libraries/applications and their external extensions/plugins remain compatible across versions. |
| [exe-icon-maven-plugin](exe-icon-maven-plugin/README.md) | Replaces Windows executable icons on any supported build host, without a system-wide Node.js installation. |

## Developing

This project is written in Java and uses [Apache Maven](https://maven.apache.org/) as a build tool.  
Building the complete reactor requires [JDK 21 or later](https://whichjdk.com/).
Existing artifacts retain their Java 8 target; `exe-icon-maven-plugin` requires Java 21.
Java dependencies are hosted on [Maven Central](https://search.maven.org/).
The icon plugin build also downloads pinned Node/npm distributions and npm dependencies into local build directories; no global installation is needed.

The code can be build using any IDE or by just type-in the following commands in a terminal:

```shell
git clone https://github.com/nbbrd/nbbrd-maven-tools.git
cd nbbrd-maven-tools
mvn clean install
```

## Contributing

Any contribution is welcome and should be done through pull requests and/or issues.

## Licensing

The code of this project is licensed under the [European Union Public Licence (EUPL)](https://joinup.ec.europa.eu/page/eupl-text-11-12).
