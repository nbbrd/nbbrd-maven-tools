# Actionlint Maven Plugin

Lint GitHub Actions workflow files with [actionlint](https://github.com/rhysd/actionlint)
as part of your Maven build.

The plugin downloads the actionlint executable for the build machine's platform,
verifies its SHA-256 checksum, caches it, and reports actionlint problems in the
Maven log. No global installation is needed.

## Quick start

Add the plugin to `<build><plugins>` of the project containing `.github/workflows`:

```xml
<plugin>
    <groupId>com.github.nbbrd.nbbrd-maven-tools</groupId>
    <artifactId>actionlint-maven-plugin</artifactId>
    <version>1.5.1-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>lint</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Run:

```shell
mvn validate
```

The goal can also run standalone, without `pom.xml`, from the repository root:

```shell
mvn com.github.nbbrd.nbbrd-maven-tools:actionlint-maven-plugin:1.5.1-SNAPSHOT:lint
```

In this mode, the executable is extracted to `cacheDirectory` instead of
`target`.

Problems are logged as `file:line:column: message [kind]` and fail the build:

```text
[ERROR] .github/workflows/ci.yml:6:3: job "build" needs job "missing-job" which does not exist in this workflow [job-needs]
[ERROR] Failed to execute goal ...:lint (default) on project demo: actionlint found 1 problem(s)
```

The goal binds to `validate` by default. By default it lints the `*.yml` and
`*.yaml` files in `.github/workflows` under the project directory. In a
multi-module build, it runs only in the top-level project and silently skips the
other modules, so the plugin can be declared once in the parent POM. Set
`runOnlyAtRoot` to `false` to lint each module's own `.github/workflows`.

## Configuration reference

| Parameter | Property | Default | Purpose |
| --- | --- | --- | --- |
| `version` | `actionlint.version` | `1.7.12` | actionlint version to download. |
| `executable` | `actionlint.executable` | Unset | Local actionlint executable; disables downloading. |
| `baseUrl` | `actionlint.baseUrl` | `https://github.com/rhysd/actionlint/releases/download` | Release location; `v<version>/<archive>` is appended. |
| `cacheDirectory` | `actionlint.cacheDirectory` | `${settings.localRepository}/.cache/actionlint` | Cache of downloaded archives, verified on each use. |
| `files` | | `.github/workflows/*.{yml,yaml}` | Explicit files to lint, replacing discovery. |
| `configFile` | `actionlint.configFile` | Unset | actionlint configuration file (`-config-file`). |
| `ignores` | | Unset | Regular expressions of messages to ignore (`-ignore`). |
| `shellcheck` | `actionlint.shellcheck` | `shellcheck` on `PATH` | shellcheck executable used for `run:` scripts. |
| `pyflakes` | `actionlint.pyflakes` | `pyflakes` on `PATH` | pyflakes executable used for Python scripts. |
| `failOnError` | `actionlint.failOnError` | `true` | Fail the build when problems are found; otherwise log warnings. |
| `githubAnnotations` | `actionlint.githubAnnotations` | `true` on GitHub Actions | Print workflow commands that annotate problems on pull requests. |
| `runOnlyAtRoot` | `actionlint.runOnlyAtRoot` | `true` | Lint only in the top-level project of a multi-module build. |
| `skip` | `actionlint.skip` | `false` | Skip linting. |

Example:

```xml
<configuration>
    <ignores>
        <ignore>SC2086</ignore>
    </ignores>
    <failOnError>false</failOnError>
</configuration>
```

## Download, verification and caching

- The archive matching the build machine (Windows, Linux, macOS or FreeBSD; amd64,
  arm64, 386 or armv6) is downloaded from `baseUrl`.
- The expected SHA-256 checksum is resolved in this order:
  1. checksums **pinned inside the plugin** (versions 1.7.8 to 1.7.12): a
     tampered or corrupted archive fails the build even if the server's
     checksums match;
  2. the checksum recorded next to the cached archive by a previous download;
  3. the asset digest published by the GitHub release API, when `baseUrl` is a
     GitHub releases URL (`GITHUB_TOKEN` is used if set, to avoid rate limits);
  4. the release's checksum file.

  Options 3 and 4 log a warning, and the resolved checksum is recorded for later
  builds.
- The **archive** is cached per version in `cacheDirectory`. The executable is
  extracted to `${project.build.directory}/actionlint` and reused as long as it
  is unchanged; otherwise the archive is verified again (a mismatching archive
  is downloaded again) and the executable is re-extracted.
- Downloads are retried twice on network errors and HTTP 429/5xx responses.
- Offline builds (`mvn -o`) reuse the cached archive. They fail if the archive
  is missing, does not match its checksum, or has no known checksum.

## GitHub Actions annotations

When running on GitHub Actions (`GITHUB_ACTIONS=true`), each problem is also
printed as a [workflow command](https://docs.github.com/actions/reference/workflow-commands-for-github-actions)
(`::error` or, with `failOnError=false`, `::warning`), so that it is shown inline
on pull requests. Set `githubAnnotations` to `false` to disable it.

## Restricted networks

Downloads use the first active proxy of Maven's `settings.xml` whose protocol
matches `baseUrl` (or an `http` proxy), honoring `nonProxyHosts`. Proxy
authentication is not supported. Without a Maven proxy, the JVM's standard
networking properties apply (`https.proxyHost`, `https.proxyPort`, ...). Behind
TLS-inspecting proxies, the JVM may also need to trust the corporate
certificate, for example with `-Djavax.net.ssl.trustStoreType=Windows-ROOT` in
`MAVEN_OPTS` on Windows.

Alternatively, point `baseUrl` to a mirror with the same layout, or set
`executable` to a pre-installed actionlint.
