# AGENTS.md — Java Service Util

## Overview

`nbbrd-maven-tools` is a multi-module Maven project that provides tooling to automate compatibility checks between Java projects and their dependent extensions/plugins.

The main workflow is to compare one or more **source** repositories (libraries/applications that expose APIs) against one or more **target** repositories (downstream plugins/extensions), across selected tags/versions, and report whether each combination still builds.

The build currently publishes three artifacts:
- `compatibility-api`: core domain model (`Job`, `Source`, `Target`, `Report`) and compatibility engine (`Compatibility`)
- `compatibility-maven-plugin`: Maven goals (`check-downstream`, `check-upstream`, `split-job`, `merge-reports`) built on top of `compatibility-api`
- `nbbrd-maven-bom`: BOM that pins versions of the API and Maven plugin for consumers

## Architecture

```text
nbbrd-maven-tools (parent POM)
|- compatibility-api          (jar)
|- compatibility-maven-plugin (maven-plugin)
`- nbbrd-maven-bom            (pom)
```

The architecture is layered:

1. **Domain + orchestration layer (`compatibility-api`)**
   - `Compatibility.check(Job)` is the core entry point.
   - It initializes source/target contexts (clone remote repos or copy local repos), resolves candidate versions from Git tags using `Filter`, and evaluates all source-version/target-version combinations.
   - For each combination, it updates the target dependency version (via `binding`), runs Maven verification, and records a `ReportItem` with an `ExitStatus`.

2. **SPI layer (`compatibility-api` / `nbbrd.compatibility.spi`)**
   - `Builder`/`Build` abstract external tooling execution (Git + Maven commands).
   - `Format` abstracts serialization/parsing of jobs/reports.
   - `Versioning` abstracts version validation and ordering.
   - SPI implementations are discovered through Service Provider annotations (`@ServiceDefinition`, `@ServiceProvider`) and loaded through generated loaders.

3. **Default implementations (`internal.compatibility.spi`)**
   - `CommandLineBuilder` / `CommandLineBuild`: execute `git` and `mvn` commands.
   - `JsonFormat`: read/write `Job` and `Report` files.
   - `MarkdownFormat`: render compatibility matrix reports.
   - `SemanticVersioning`: semantic version comparator used by default (`semver`).

4. **Maven plugin layer (`compatibility-maven-plugin`)**
   - Mojo classes translate Maven parameters into `Job` instances and delegate to `Compatibility`.
   - `check-downstream` and `check-upstream` run compatibility checks.
   - `split-job` creates one job file per target for parallelization.
   - `merge-reports` aggregates report files/directories into one report.

## Build & Test

```shell
mvn clean install                 # full build + tests + enforcer checks
mvn clean install -Pyolo          # skip all checks (fast local iteration)
mvn test -pl <module-name> -Pyolo # fast test a single module
mvn test -pl <module-name> -am    # full test a single module
```

- **Java 8 target** with JPMS `module-info.java` compiled separately on JDK 9+ (see `java8-with-jpms` profile in root POM)
- **JUnit 5** with parallel execution enabled (`junit.jupiter.execution.parallel.enabled=true`); **AssertJ** for assertions
- `heylogs-api` publishes a **test-jar** (`tests/` package) reused by extension modules for shared test fixtures

## Key Conventions

- **Lombok**: use lombok annotations when possible. Config in `lombok.config`: `addNullAnnotations=jspecify`, `builder.className=Builder`
- **Nullability**: `@org.jspecify.annotations.Nullable` for nullable; `@lombok.NonNull` for non-null parameters. Return types use `@Nullable` or the `OrNull` suffix (e.g., `getThingOrNull`)
- **Design annotations** use annotations from `java-design-util` such as `@VisibleForTesting`, `@StaticFactoryMethod`, `@DirectImpl`, `@MightBeGenerated`, `@MightBePromoted`
- **Internal packages**: `internal.<project>.*` are implementation details; public API lives in the root and `spi` packages
- **Static analysis**: `forbiddenapis` (no `jdk-unsafe`, `jdk-deprecated`, `jdk-internal`, `jdk-non-portable`, `jdk-reflection`), `modernizer`
- **Reproducible builds**: `project.build.outputTimestamp` is set in the root POM
- **Formatting/style**: 
  - Use IntelliJ IDEA default code style for Java
  - Follow existing formatting and match naming conventions exactly
  - Follow the principles of "Effective Java"
  - Follow the principles of "Clean Code"
- **Java/JVM**: 
  - Target version defined in root POM properties; some modules may require higher versions
  - Use modern Java feature compatible with defined version

## Agent behavior

- Do respect existing architecture, coding style, and conventions
- Do prefer minimal, reviewable changes
- Do preserve backward compatibility
- Do not introduce new dependencies without justification
- Do not rewrite large sections for cleanliness
- Do not reformat code
- Do not propose additional features or changes beyond the scope of the task
