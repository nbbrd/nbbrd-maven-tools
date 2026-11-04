# Executable icon Maven plugin

A self-contained, cross-platform Maven plugin for replacing icons in Windows
executables from an ICO file, on Windows, Linux or macOS.

- [For plugin users](#for-plugin-users): requirements, configuration and NetBeans integration.
- [For plugin developers](#for-plugin-developers): building, implementation and maintenance.

## For plugin users

### Requirements

Requires Maven 3.9.11+ and JDK 21+.

### Usage

```xml
<plugin>
    <groupId>com.github.nbbrd.nbbrd-maven-tools</groupId>
    <artifactId>exe-icon-maven-plugin</artifactId>
    <version>1.3.2-SNAPSHOT</version>
    <executions>
        <execution>
            <phase>prepare-package</phase>
            <goals>
                <goal>replace-icon</goal>
            </goals>
            <configuration>
                <inputFile>${project.build.directory}/launchers/app64.exe</inputFile>
                <iconFile>${project.basedir}/src/main/branding/app.ico</iconFile>
                <outputFile>${project.build.directory}/distribution/bin/myapp64.exe</outputFile>
                <iconGroupId>100</iconGroupId>
            </configuration>
        </execution>
    </executions>
</plugin>
```

The lifecycle phase above is illustrative: the input launcher must already exist,
and subsequent packaging must consume the output rather than copy another launcher
over it. The goal has no default phase.

### Parameters and behavior

| Parameter | Property | Meaning |
|-----------|----------|---------|
| `inputFile` | `exeIcon.inputFile` | Required unsigned PE32/PE32+ executable; never modified |
| `iconFile` | `exeIcon.iconFile` | Required ICO; supports multiple resolutions and PNG-backed images |
| `outputFile` | `exeIcon.outputFile` | Required distinct destination; an existing output is replaced on success |
| `iconGroupId` | `exeIcon.iconGroupId` | Optional numeric resource ID (0-65535); inferred only if there is exactly one group ID |
| `skip` | `exeIcon.skip` | Skip execution; defaults to false |

All existing languages of the selected group are updated. Other groups, including
their shared images, are retained. Missing/ambiguous groups, malformed input and
signed executables fail the build. Image conversion and signing are not provided.
Output is generated and checked before it is written through a temporary sibling
file. Atomic replacement is used where the filesystem supports it.

### NetBeans Platform

The NetBeans launcher source declares icon group 100. Verify the actual launcher
from your platform release rather than assuming every release has that layout.
Create the launcher copies, replace their icons, sign if needed, then create the
ZIP/installer. Use one execution per distributed launcher variant.

Never edit a launcher in the shared NetBeans harness or Maven local repository.
The input and output must be distinct, so configure packaging to consume the
branded copy. The running Swing application's window/taskbar branding is separate
from the executable's Explorer icon.

This plugin is tested against pinned NetBeans PE32 and PE32+ launchers; it does not
promise arbitrary packed executables, installers or self-extracting archives are
safe to rewrite. Validate the final application on Windows.

## For plugin developers

This section concerns building and maintaining the plugin itself, not using it
in an application's Maven build.

### Building without a global JavaScript toolchain

From the repository root:

```shell
mvn -pl exe-icon-maven-plugin -am clean verify
```

The normal build regenerates the bundle; generated JavaScript is not committed.
`frontend-maven-plugin` provisions exact Node/npm versions. Maven stages the
JavaScript sources, runs `npm ci --ignore-scripts --no-audit --no-fund`, then uses
esbuild to produce a self-contained browser-targeted IIFE resource in the JAR.
esbuild's platform package works without its npm installation script.

| Build output | Location in this module |
|--------------|-------------------------|
| Node/npm installation | `target\node` |
| JS working files and dependencies | `target\js-build` |
| npm cache/logs and prefix | `target\npm-cache`, `target\npm-prefix` |
| npm child-process temporary files | `target\js-build` |
| JUnit temporary files | Under `target` |
| Bundle and bundled dependency licenses | `target\generated-resources\internal\exeicon` |
| Consumer integration-test project and repository | `target\it`, `target\it-repository` |

The build does not install global packages, edit the host's PATH, or write npm
configuration into the user's home. npm user/global configuration is explicitly
redirected to project-controlled files. `mvn clean` removes the directories above.
Maven's normal dependency/tool archive cache is separate and is not removed by
clean. To also contain it, supply an absolute workspace path:

```shell
mvn -Dmaven.repo.local=C:\build-cache\maven -pl exe-icon-maven-plugin -am clean verify
```

Use the corresponding absolute path on Unix hosts. This is controlled local
provisioning, not an OS sandbox: Maven/JVM/OS activity is not fully isolated.
No administrator privileges or machine configuration changes are required.

A cold build needs access to Maven repositories, `nodejs.org` and the npm registry.
`dependency:go-offline` alone does not provision Node/npm or npm dependencies.

### Corporate networks and certificates

Corporate proxies may require approved download mirrors or certificates.
The provisioned Node/npm processes use the operating system's trusted certificate
store in addition to Node's bundled certificates (`NODE_USE_SYSTEM_CA=1`), with TLS
verification still enabled. This supports corporate CA certificates already
trusted by the host without requiring a shell environment variable. If the CA is
not trusted by the host, obtain an approved certificate configuration from your
administrator. Maven's Java TLS configuration is separate.
The frontend plugin's `nodeDownloadRoot` and `npmDownloadRoot` parameters support
approved mirrors; do not disable certificate checks or bypass corporate filtering.

### Updating the bundle

1. Change exact dependency versions in `src\main\js\package.json`, or tool versions
   in this module's POM (Node/npm) or the root POM's `pluginManagement` (Maven plugins).
2. Stage sources and provision the local tools:
   `mvn -pl exe-icon-maven-plugin initialize frontend:install-node-and-npm`.
3. Generate the lockfile with local npm:
   `mvn -pl exe-icon-maven-plugin frontend:npm "-Dfrontend.npm.arguments=install --package-lock-only --ignore-scripts --no-audit --no-fund"`.
4. Copy `target\js-build\package-lock.json` back to `src\main\js\package-lock.json`,
   then run the normal build and review dependency/license changes.

Commit the inputs and lockfile, not `target`. Dependabot tracks the npm manifest.
The lockfile also freezes resedit's transitive `pe-library` version.

### Implementation choices

The published plugin embeds GraalJS Community and needs neither Node.js, Wine,
Windows SDK tools nor a GraalVM JDK. Node/npm provisioning is only needed when
building the plugin itself. Stock-JDK execution is covered by this project's CI,
although upstream GraalJS does not officially support that mode.

The plugin deliberately excludes the optimizing `truffle-runtime` dependency and
uses Truffle's interpreter instead. This avoids coupling the embedded JavaScript
engine to the host GraalVM JDK's compiler version, without changing JVM-wide system
properties or disabling version checks. JavaScript JIT compilation is not needed
for this build-time operation.

The bundle includes the browser-compatible `buffer` package because the pinned
pe-library's non-Buffer string-writing fallback corrupts PE section names.
Regression tests compare non-resource section bytes and non-icon resource bytes.
Bundled runtime dependencies' license texts and versions are shipped in
`internal/exeicon/THIRD-PARTY-NOTICES.txt`; GraalJS retains its normal Maven
dependency metadata and licenses.

### Verification

Tests cover NetBeans PE32/PE32+ launchers, growing PNG-based icons,
multi-resolution bitmap ICOs, language variants, shared icon resources, signed
input rejection, invalid inputs, deterministic output and input preservation.
A separate Maven consumer invokes the packaged plugin without provisioning Node.
Structural resource checks do not replace starting a complete branded NetBeans
application on Windows with its platform modules, configuration and JDK.
