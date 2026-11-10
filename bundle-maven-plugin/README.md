# Bundle Maven Plugin

Ship your application with its own Java runtime, so users do not need to install
Java separately. This plugin combines an existing application archive with a
local JDK/JRE archive and updates its Appassembler or NetBeans launcher.

You can generate Windows, Linux, and macOS bundles on the same build machine,
including on Windows without symlink privileges. Runtime permissions and
symbolic links are kept as archive metadata, not recreated on the build machine.

The plugin **only bundles**: it does not download Java, run `jlink`, test the
bundled runtime, or attach the outputs as Maven artifacts.

## Quick start

Before you begin, you need:

- An application archive with Appassembler or NetBeans launchers.
- A JDK/JRE archive for each platform you want to ship. Download these separately,
  for example with `jdks-maven-plugin`.

This example creates one Linux bundle using Appassembler. It assumes
`target/myapp.zip` contains `myapp/bin/myapp` and `myapp/bin/myapp.bat`,
and that the runtime archive is available at `target/jdks/linux.tar.gz`.

Set a fixed timestamp in your project's POM for reproducible outputs:

```xml
<properties>
    <project.build.outputTimestamp>2026-01-01T00:00:00Z</project.build.outputTimestamp>
</properties>
```

Add the plugin to `<build><plugins>`:

```xml
<plugin>
    <groupId>com.github.nbbrd.nbbrd-maven-tools</groupId>
    <artifactId>bundle-maven-plugin</artifactId>
    <version>1.4.3-SNAPSHOT</version>
    <executions>
        <execution>
            <id>bundle-cli</id>
            <phase>package</phase>
            <goals>
                <goal>bundle-jdk</goal>
            </goals>
            <configuration>
                <inputFile>${project.build.directory}/myapp.zip</inputFile>
                <applicationRoot>myapp</applicationRoot>
                <launcherId>appassembler</launcherId>
                <appName>myapp</appName>
                <runtimes>
                    <runtime>
                        <id>linux-x86_64</id>
                        <archivePath>${project.build.directory}/jdks/linux.tar.gz</archivePath>
                        <outputFile>${project.build.directory}/myapp-linux-x86_64.tar.gz</outputFile>
                    </runtime>
                </runtimes>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Run:

```shell
mvn package
```

The result is `target/myapp-linux-x86_64.tar.gz`. Maven logs application loading,
each runtime's progress (`[1/1]`), archive writing, and successful completion.

**Build order matters:** the input archives must exist before `bundle-jdk`
runs. If another plugin creates or downloads them during `package`, declare that
plugin before this one. `package` is also the goal's default phase.

## Understand the archive layout

`applicationRoot` is a directory **inside the application archive**, not a
directory on your build machine. The plugin places the runtime there without
stripping or renaming its original root.

For example:

```text
Application: myapp.zip       Runtime: linux.tar.gz       Result: myapp-linux-x86_64.tar.gz
myapp/                      jdk-21/                     myapp/
  bin/                        bin/java                    bin/
    myapp                     release                       myapp       (patched)
    myapp.bat                                               myapp.bat   (patched)
  lib/                                                    lib/
                                                          jdk-21/
                                                            bin/java
                                                            release
```

Use `<applicationRoot>.</applicationRoot>` (the default) if `bin` or `etc` is
directly at the application archive root.

### Which directory is `javaHome`?

`javaHome` means the directory **inside the runtime archive that contains
Java's `bin` directory**. Usually you can omit it: the plugin finds this
directory automatically.

For example, if the archive contains `jdk-21/bin/java`, the Java home is
`jdk-21`, not `jdk-21/bin` or `jdk-21/bin/java`.

| Path to Java inside the runtime archive | `javaHome` |
| --- | --- |
| `jdk-21/bin/java` | `jdk-21` |
| `jdk-21/bin/java.exe` | `jdk-21` |
| `jdk-21.jre/Contents/Home/bin/java` | `jdk-21.jre/Contents/Home` |
| `bin/java` | `.` (the archive root) |

For automatic detection, the plugin looks for both the Java executable
(`bin/java` or `bin/java.exe`) and a **file literally named `release`** in the
same Java home directory. This is a metadata file supplied with the JDK/JRE,
containing information such as its Java version. It is not a directory,
an application release, or a value you need to configure.

If the archive contains several Java installations, choose one explicitly
inside its `<runtime>`, for example:

```xml
<javaHome>jdk-21.jre/Contents/Home</javaHome>
```

The path is relative to the runtime archive, **not** the application archive
or the build machine's `JAVA_HOME`. Setting it selects an existing directory;
it does not rename or move the runtime.

## Bundle several platforms

Replace the quick-start runtime list with as many entries as you need:

```xml
<runtimes>
    <runtime>
        <id>windows-x86_64</id>
        <archivePath>${project.build.directory}/jdks/windows.zip</archivePath>
        <outputFile>${project.build.directory}/myapp-windows-x86_64.zip</outputFile>
    </runtime>
    <runtime>
        <id>linux-x86_64</id>
        <archivePath>${project.build.directory}/jdks/linux.tar.gz</archivePath>
        <outputFile>${project.build.directory}/myapp-linux-x86_64.tar.gz</outputFile>
    </runtime>
    <runtime>
        <id>macos-aarch64</id>
        <archivePath>${project.build.directory}/jdks/macos</archivePath>
        <outputFile>${project.build.directory}/myapp-macos-aarch64.tgz</outputFile>
    </runtime>
</runtimes>
```

`archivePath` accepts either an archive file or a directory containing exactly
one `.zip`, `.tar.gz`, or `.tgz` file. Other files, such as checksums, are ignored
when selecting an archive from a directory.

**Every configured runtime is bundled**, regardless of the build machine's OS.
The `id` is a unique diagnostic label, not a platform filter. Choose the correct
runtime archives for your users' operating systems and architectures.

The output filename is used exactly as configured; no id or extension is
appended. Its extension selects the format:

| `outputFile` extension | Output format |
| --- | --- |
| `.zip` | ZIP |
| `.tar.gz` or `.tgz` | GZIP-compressed TAR |

Input and output formats can differ. There is no separate `format` setting.

## Choose a launcher

`appName` is a filename stem, not a display name. It defaults to
`${project.artifactId}`.

| `launcherId` | Files expected under `applicationRoot` |
| --- | --- |
| `appassembler` | `bin/${appName}` and `bin/${appName}.bat` |
| `netbeans` | `etc/${appName}.conf` |

### Appassembler

Support targets the standard scripts generated by Appassembler 2.1.0.
Both Unix and Windows scripts must be present. The plugin patches them to force
the bundled Java, even if the caller sets `JAVA_HOME` or `JAVACMD`. Paths
containing spaces are quoted.

### NetBeans

Keep the runtime list and use these application settings:

```xml
<inputFile>${project.build.directory}/myapp.zip</inputFile>
<applicationRoot>myapp</applicationRoot>
<launcherId>netbeans</launcherId>
<appName>myapp</appName>
```

The plugin replaces the standard commented setting
`#jdkhome="/path/to/jdk"` with the bundled home. Explicit NetBeans command-line
overrides remain governed by the NetBeans launcher.

For both providers, customized templates may require a custom launcher provider.
Launcher files must be UTF-8. Line endings and unrelated content are preserved.
Missing, ambiguous, or customized templates fail the build rather than producing
an archive that silently uses system Java.

## Configuration reference

### Goal parameters

| Parameter | Default | Purpose |
| --- | --- | --- |
| `inputFile` | Required | Original application ZIP or TAR.GZ archive. |
| `applicationRoot` | `.` | Application directory inside the archive. |
| `launcherId` | Required | `appassembler`, `netbeans`, or a custom provider id. |
| `appName` | `${project.artifactId}` | Script or configuration filename stem. |
| `runtimes` | Required | One or more runtime configurations. |
| `outputTimestamp` | `${project.build.outputTimestamp}`; required | ISO-8601 instant or Unix epoch seconds. |
| `skip` | `false` | Skip bundling. |

### Each `<runtime>`

| Parameter | Default | Purpose |
| --- | --- | --- |
| `id` | Required | Unique label identifying the runtime in logs and errors. |
| `archivePath` | Required | Runtime archive, or directory containing exactly one supported archive. |
| `outputFile` | Required | Destination archive, including its format-selecting extension. |
| `javaHome` | Auto-detected | Directory containing Java's `bin` directory, relative to the runtime archive; `.` selects the archive root. |

### Command-line overrides

Goal parameters other than `runtimes` support `bundle.<parameter>` properties:

```shell
mvn package -Dbundle.appName=myapp
mvn package -Dbundle.skip=true
```

Skipping avoids reading the archives, but Maven still requires `inputFile`,
`launcherId`, `runtimes`, and `outputTimestamp` to be configured.

Runtime settings belong in the POM. To override their values from the command
line, reference your own Maven properties in `archivePath` or `outputFile`.

## Permissions, reproducibility, and disk space

**Linux and macOS runtimes:** Unix permission bits and symbolic links are
preserved even when building on Windows. The user's extractor must also support
them. Prefer TAR.GZ if you cannot guarantee that their ZIP extractor preserves
Unix modes and symlinks. TAR hard links stay hard links in TAR.GZ outputs and
become regular files in ZIP outputs.

**Reproducible outputs:** use the same inputs and a fixed `outputTimestamp`.
Entries are ordered deterministically; timestamps, ownership, and compression
metadata are normalized. ZIP timestamps use UTC calendar fields regardless of
the build machine's timezone, must fall between 1980 and 2107, and have
two-second precision. TAR hard-link targets are written before their links.

**Temporary disk space:** allow room beside the outputs for the uncompressed
application, the runtime being processed, and the new output archive. Contents
are staged as private temporary files, never extracted using archive entry
paths. The application is staged once, and runtimes are processed sequentially.
Inputs are never modified.

The plugin does not verify that a bundled runtime runs on its target platform.
For release validation, extract the finished bundle on that platform and run
its launcher.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Application archive not found | Ensure the packaging plugin runs before `bundle-jdk`. |
| No runtime archive, or several candidates | Set `archivePath` to a specific archive, or leave exactly one supported archive in the selected directory. |
| Launcher file missing | Check `applicationRoot` and `appName` against the paths inside the application archive. Appassembler requires both scripts. |
| Launcher template cannot be patched | Use the supported standard template or a custom launcher provider. |
| Multiple Java homes found | Set the runtime's `javaHome` to an archive-relative path. |
| Unsupported output format | End `outputFile` with `.zip`, `.tar.gz`, or `.tgz`. |
| Missing or invalid timestamp | Set `project.build.outputTimestamp` or `outputTimestamp`; check the ZIP date range. |
| Output collision | Give each runtime its own output path, distinct from every input archive. |

Errors include runtime and archive context; entry-level failures identify the
affected entry. Unsafe paths or links, duplicate files, and unsupported archive
entry types fail explicitly.

A failed bundle leaves its existing output unchanged and removes temporary
files. If a later runtime fails, earlier completed bundles remain available.

## Custom launcher providers

For launchers beyond the built-in templates, implement
`nbbrd.bundle.spi.Launcher` and add the provider as a plugin dependency.
Register it with `@ServiceProvider` and the service annotation processor, or
standard `META-INF/services` registration. Select it with `launcherId`.
Unknown ids and duplicate providers for the selected id fail explicitly.

The SPI is a `@ServiceDefinition` with multiple providers, discovered through
the generated `LauncherLoader`. A provider supplies:

- A unique launcher id.
- A non-empty list from `getFiles(appName)` of `LauncherFile` descriptors, each
  with a unique, non-empty application-relative path, a provider-specific role,
  and an executable-permission flag.
- `patch(file, content, javaHome)`, which returns patched UTF-8 text. Java home
  is relative to the application root; an empty path means that root.

Roles distinguish Unix scripts, Windows scripts, and configuration files
without relying on filename extensions.
