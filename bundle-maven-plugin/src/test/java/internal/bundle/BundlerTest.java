package internal.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BundlerTest {

    static final Instant TIMESTAMP = Instant.parse("2026-01-02T03:04:06Z");
    static final String UNIX = "#!/bin/sh\nBASEDIR=\"application path\"\n"
            + "if [ -z \"$JAVACMD\" ] ; then\n"
            + "  JAVACMD=\"$JAVA_HOME/bin/java\"\nfi\nexec \"$JAVACMD\" \"$@\"\n";
    static final String WINDOWS = "@echo off\r\n" + "if \"%JAVACMD%\"==\"\" set JAVACMD=java\r\n%JAVACMD% %*\r\n";

    @TempDir
    Path temp;

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void reportsProgressAndOnlyCompletedOutputs(boolean failSecond) throws IOException {
        Path app = application(temp, "zip", false);
        Path runtime = runtime(temp, "zip", "jdk", false);
        Path first = temp.resolve("first.zip");
        Path second = temp.resolve("second.zip");
        BundleRequest request = BundleRequest.builder()
                .application(app)
                .baseDirectory("app")
                .launcher("appassembler")
                .appName("run")
                .timestamp(TIMESTAMP)
                .runtime(BundleRequest.RuntimeBundle.builder()
                        .id("first")
                        .archive(runtime)
                        .output(first)
                        .build())
                .runtime(BundleRequest.RuntimeBundle.builder()
                        .id("second")
                        .archive(runtime)
                        .output(second)
                        .javaHome(failSecond ? "missing" : null)
                        .build())
                .build();
        List<String> messages = new ArrayList<>();
        java.util.function.Consumer<String> progress = message -> {
            if (message.startsWith("Bundled runtime")) {
                assertThat(Files.isRegularFile(message.contains("'first'") ? first : second))
                        .isTrue();
            }
            messages.add(message);
        };
        if (failSecond) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Bundler.bundle(request, progress))
                    .withMessageContaining("second");
        } else {
            Bundler.bundle(request, progress);
        }
        assertThat(messages).hasSize(failSecond ? 5 : 7);
        assertThat(messages.get(0)).startsWith("Reading application archive: ");
        assertThat(messages.get(1)).startsWith("[1/2] Bundling runtime 'first' from ");
        assertThat(messages.get(2)).startsWith("Writing bundle for runtime 'first': ");
        assertThat(messages.get(3)).startsWith("Bundled runtime 'first': ");
        assertThat(messages.get(4)).startsWith("[2/2] Bundling runtime 'second' from ");
        if (!failSecond) {
            assertThat(messages.get(5)).startsWith("Writing bundle for runtime 'second': ");
            assertThat(messages.get(6)).startsWith("Bundled runtime 'second': ");
        }
    }

    @ParameterizedTest
    @CsvSource({"zip,zip", "zip,tar.gz", "tar.gz,zip", "tar.gz,tar.gz"})
    void bundlesAppassemblerAndPreservesRuntimeMetadata(String inputFormat, String outputFormat) throws IOException {
        Path app = application(temp, inputFormat, false);
        Path runtime = runtime(temp, inputFormat, "jdk-21", true);
        Path output = temp.resolve("standalone." + outputFormat);
        bundle(app, runtime, output, "app", "appassembler", "run", null, TIMESTAMP);
        try (Archive result = new Archive(temp)) {
            result.read(output);
            assertThat(text(result, "app/bin/run"))
                    .contains("JAVA_HOME=\"$BASEDIR/jdk-21\"\nJAVACMD=\"$JAVA_HOME/bin/java\"\nif [ -z \"$JAVACMD\" ]");
            assertThat(text(result, "app/bin/run.bat"))
                    .isEqualTo("@echo off\r\nset JAVACMD=\"%BASEDIR%\\jdk-21\\bin\\java.exe\"\r\n%JAVACMD% %*\r\n");
            assertThat(result.entries.get("app/bin/run").mode & 0111).isEqualTo(0111);
            assertThat(result.entries.get("app/jdk-21/bin/java").mode).isEqualTo(0755);
            assertThat(result.entries.get("app/jdk-21/restricted").mode).isZero();
            assertThat(result.entries.get("app/jdk-21/lib/current").type).isEqualTo(EntryType.SYMLINK);
            assertThat(result.entries.get("app/jdk-21/lib/current").link).isEqualTo("server");
            assertThat(text(result, "app/jdk-21/lib/copy")).isEqualTo("library");
            assertThat(result.entries.get("app/jdk-21/lib/copy").type)
                    .isEqualTo(
                            "tar.gz".equals(inputFormat) && "tar.gz".equals(outputFormat)
                                    ? EntryType.HARDLINK
                                    : EntryType.FILE);
            assertThat(text(result, "app/data.txt")).isEqualTo("untouched");
        }
    }

    @ParameterizedTest
    @CsvSource({"zip,zip", "tar.gz,tar.gz"})
    void bundlesNetbeansMacHomeAndPreservesLineEndings(String inputFormat, String outputFormat) throws IOException {
        Path app = application(temp, inputFormat, true);
        Path runtime = runtime(temp, inputFormat, "jdk-21.jre/Contents/Home", false);
        Path output = temp.resolve("mac." + outputFormat);
        bundle(app, runtime, output, "app", "netbeans", "brand", null, TIMESTAMP);
        try (Archive result = new Archive(temp)) {
            result.read(output);
            assertThat(text(result, "app/etc/brand.conf"))
                    .isEqualTo("other=value\r\njdkhome=\"jdk-21.jre/Contents/Home\"\r\n");
        }
    }

    @Test
    void preflightsAllRuntimePathsBeforeCreatingOutputs() throws IOException {
        Path app = application(temp, "zip", false);
        Path runtime = runtime(temp, "zip", "jdk", false);
        Path output = temp.resolve("not-created/result.zip");
        BundleRequest request = BundleRequest.builder()
                .application(app)
                .baseDirectory("app")
                .launcher("appassembler")
                .appName("run")
                .timestamp(TIMESTAMP)
                .runtime(BundleRequest.RuntimeBundle.builder()
                        .id("first")
                        .archive(runtime)
                        .output(output)
                        .build())
                .runtime(BundleRequest.RuntimeBundle.builder()
                        .id("second")
                        .archive(temp.resolve("missing.zip"))
                        .output(temp.resolve("second.zip"))
                        .build())
                .build();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Bundler.bundle(request))
                .withMessageContaining("second")
                .withMessageContaining("missing.zip");
        assertThat(Files.exists(output.getParent())).isFalse();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"empty", "duplicate", "traversal", "no-files"})
    void rejectsMalformedLauncherProviderPathsBeforeWriting(String appName) throws IOException {
        Path app = application(temp, "zip", false);
        Path runtime = runtime(temp, "zip", "jdk", false);
        Path output = temp.resolve("not-created/result.zip");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bundle(app, runtime, output, "app", "test-launcher", appName, null, TIMESTAMP));
        assertThat(Files.exists(output.getParent())).isFalse();
    }

    @Test
    void supportsRootLevelApplicationAndRuntime() throws IOException {
        Path app = temp.resolve("root-app.zip");
        try (Archive archive = new Archive(temp)) {
            file(archive, "bin/run", UNIX, 0755);
            file(archive, "bin/run.bat", WINDOWS, 0644);
            archive.write(app, "zip", TIMESTAMP);
        }
        Path runtime = temp.resolve("root-jdk.zip");
        try (Archive archive = new Archive(temp)) {
            file(archive, "bin/java", "binary", 0755);
            file(archive, "release", "version", 0644);
            archive.add(new Archive.Entry("bin/java-link", EntryType.SYMLINK, 0777, null, "java"));
            archive.write(runtime, "zip", TIMESTAMP);
        }
        Path output = temp.resolve("root-result.zip");
        bundle(app, runtime, output, ".", "appassembler", "run", ".", TIMESTAMP);
        try (Archive result = new Archive(temp)) {
            result.read(output);
            assertThat(text(result, "bin/run")).contains("JAVA_HOME=\"$BASEDIR\"\n");
            assertThat(text(result, "bin/run.bat")).contains("%BASEDIR%\\bin\\java.exe");
            assertThat(result.entries.get("bin/java-link").type).isEqualTo(EntryType.SYMLINK);
        }
        // The same layout also works when Java home is detected rather than explicitly selected.
        bundle(app, runtime, output, "", "appassembler", "run", null, TIMESTAMP);
    }

    @Test
    void isolatesLauncherChangesAcrossMultipleRuntimes() throws IOException {
        Path app = application(temp, "zip", false);
        Path first = runtime(temp, "zip", "first-jdk", false);
        Path second = temp.resolve("second-jdk.zip");
        Files.move(first, second);
        first = runtime(temp, "zip", "other-jdk", false);
        Path firstOutput = temp.resolve("first.zip");
        Path secondOutput = temp.resolve("second.zip");
        Bundler.bundle(BundleRequest.builder()
                .application(app)
                .baseDirectory("app")
                .launcher("appassembler")
                .appName("run")
                .timestamp(TIMESTAMP)
                .runtime(BundleRequest.RuntimeBundle.builder()
                        .id("first")
                        .archive(first)
                        .output(firstOutput)
                        .build())
                .runtime(BundleRequest.RuntimeBundle.builder()
                        .id("second")
                        .archive(second)
                        .output(secondOutput)
                        .build())
                .build());
        try (Archive result = new Archive(temp)) {
            result.read(firstOutput);
            assertThat(text(result, "app/bin/run"))
                    .contains("$BASEDIR/other-jdk")
                    .doesNotContain("first-jdk");
        }
        try (Archive result = new Archive(temp)) {
            result.read(secondOutput);
            assertThat(text(result, "app/bin/run"))
                    .contains("$BASEDIR/first-jdk")
                    .doesNotContain("other-jdk");
            assertThat(result.entries).doesNotContainKey("app/other-jdk/bin/java");
        }
    }

    @Test
    void writesHardlinkTargetsBeforeLinksIncludingChains() throws IOException {
        Path output = temp.resolve("links.tar.gz");
        try (Archive archive = new Archive(temp)) {
            file(archive, "z/target", "payload", 0644);
            archive.add(new Archive.Entry("a/link", EntryType.HARDLINK, 0644, null, "b/link"));
            archive.add(new Archive.Entry("b/link", EntryType.HARDLINK, 0644, null, "z/target"));
            archive.write(output, "tar.gz", TIMESTAMP);
        }
        try (org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream gzip =
                        org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream.builder()
                                .setPath(output)
                                .get();
                org.apache.commons.compress.archivers.tar.TarArchiveInputStream tar =
                        new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gzip)) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isLink()) {
                    assertThat(seen).contains(entry.getLinkName());
                }
                seen.add(entry.getName());
            }
        }
    }

    @Test
    void includesRuntimeAndLauncherEntryInFailureMessage() throws IOException {
        Path app = application(temp, "zip", true);
        Path runtime = runtime(temp, "zip", "jdk", false);
        Path output = temp.resolve("failed.zip");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bundle(app, runtime, output, "app", "netbeans", "missing", null, TIMESTAMP))
                .withMessageContaining("test-runtime")
                .withMessageContaining(runtime.toRealPath().toString())
                .withMessageContaining(output.getParent()
                        .toRealPath()
                        .resolve(output.getFileName())
                        .toString())
                .withMessageContaining("netbeans")
                .withMessageContaining("app/etc/missing.conf");
    }

    @Test
    void bundlesWithAnAdditionalLauncherProvider() throws IOException {
        Path app = application(temp, "zip", false);
        Path runtime = runtime(temp, "zip", "jdk", false);
        Path output = temp.resolve("custom.zip");
        bundle(app, runtime, output, "app", "test-launcher", null, null, TIMESTAMP);
        try (Archive result = new Archive(temp)) {
            result.read(output);
            assertThat(text(result, "app/data.txt")).isEqualTo("jdk");
            assertThat(result.entries.get("app/data.txt").mode).isEqualTo(0644);
            assertThat(text(result, "app/bin/run")).isEqualTo(UNIX);
        }
    }

    @Test
    void discoversWindowsOnlyRuntimeInZip() throws IOException {
        Path app = application(temp, "zip", false);
        Path runtime = temp.resolve("windows.zip");
        try (Archive archive = new Archive(temp)) {
            file(archive, "jdk/bin/java.exe", "binary", 0755);
            file(archive, "jdk/release", "JAVA_VERSION=\"21\"", 0644);
            archive.write(runtime, "zip", TIMESTAMP);
        }
        bundle(app, runtime, temp.resolve("result.zip"), "app", "appassembler", "run", null, TIMESTAMP);
        assertThat(Files.isRegularFile(temp.resolve("result.zip"))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"zip", "tar.gz"})
    void outputIsByteReproducible(String format) throws IOException {
        Path app = application(temp, format, false);
        Path runtime = runtime(temp, format, "jdk", true);
        Path first = temp.resolve("first." + format);
        Path second = temp.resolve("second." + format);
        bundle(app, runtime, first, "app", "appassembler", "run", null, TIMESTAMP);
        bundle(app, runtime, second, "app", "appassembler", "run", null, TIMESTAMP);
        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
    }

    @Test
    void failsForUnknownTemplateWithoutReplacingExistingOutputOrLeakingTemporaryFiles() throws IOException {
        Path app = temp.resolve("invalid.zip");
        try (Archive archive = new Archive(temp)) {
            file(archive, "app/bin/run", "unexpected template", 0755);
            file(archive, "app/bin/run.bat", WINDOWS, 0644);
            archive.write(app, "zip", TIMESTAMP);
        }
        Path runtime = runtime(temp, "zip", "jdk", false);
        Path output = temp.resolve("existing.zip");
        Files.write(output, new byte[] {1, 2, 3});
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bundle(app, runtime, output, "app", "appassembler", "run", null, TIMESTAMP))
                .withMessageContaining("Unrecognized");
        assertThat(Files.readAllBytes(output)).containsExactly(1, 2, 3);
        try (java.util.stream.Stream<Path> files = Files.list(temp)) {
            assertThat(files.map(path -> path.getFileName().toString())).noneMatch(name -> name.startsWith(".bundle-"));
        }
    }

    @Test
    void rejectsMissingLauncherAndInputOverwrite() throws IOException {
        Path app = application(temp, "zip", false);
        Path runtime = runtime(temp, "zip", "jdk", false);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bundle(app, runtime, app, "app", "appassembler", "run", null, TIMESTAMP))
                .withMessageContaining("distinct");
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        bundle(app, runtime, temp.resolve("result.zip"), "app", "netbeans", "missing", null, TIMESTAMP))
                .withMessageContaining("Missing regular launcher");
    }

    @Test
    void rejectsAmbiguousRuntimeUnlessHomeIsExplicit() throws IOException {
        Path app = application(temp, "zip", false);
        Path runtime = temp.resolve("two.zip");
        try (Archive archive = new Archive(temp)) {
            for (String root : Arrays.asList("jdk1", "jdk2")) {
                file(archive, root + "/bin/java", "binary", 0755);
                file(archive, root + "/release", "version", 0644);
            }
            archive.write(runtime, "zip", TIMESTAMP);
        }
        Path output = temp.resolve("result.zip");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bundle(app, runtime, output, "app", "appassembler", "run", null, TIMESTAMP))
                .withMessageContaining("Expected one Java home");
        bundle(app, runtime, output, "app", "appassembler", "run", "jdk2", TIMESTAMP);
        try (Archive result = new Archive(temp)) {
            result.read(output);
            assertThat(text(result, "app/bin/run")).contains("$BASEDIR/jdk2");
        }
    }

    @Test
    void rejectsArchivePathTraversalAndDuplicateEntries() throws IOException {
        Path bad = temp.resolve("bad.zip");
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(bad)) {
            zip.putArchiveEntry(new ZipArchiveEntry("../outside"));
            zip.closeArchiveEntry();
        }
        try (Archive archive = new Archive(temp)) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> archive.read(bad))
                    .withMessageContaining("Unsafe");
        }
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(bad)) {
            for (int i = 0; i < 2; i++) {
                zip.putArchiveEntry(new ZipArchiveEntry("duplicate"));
                zip.closeArchiveEntry();
            }
        }
        try (Archive archive = new Archive(temp)) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> archive.read(bad))
                    .withMessageContaining("Conflicting");
        }
        assertThat(Files.exists(temp.resolve("outside"))).isFalse();
    }

    @Test
    void rejectsEscapingLinksAndSpecialTarEntries() throws IOException {
        Path bad = temp.resolve("bad.tar.gz");
        for (byte type : new byte[] {TarConstants.LF_SYMLINK, TarConstants.LF_FIFO}) {
            try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(Files.newOutputStream(bad));
                    TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
                TarArchiveEntry item = new TarArchiveEntry("jdk/bin/link", type);
                item.setLinkName("../../../outside");
                tar.putArchiveEntry(item);
                tar.closeArchiveEntry();
            }
            try (Archive archive = new Archive(temp)) {
                assertThatIllegalArgumentException().isThrownBy(() -> archive.read(bad));
            }
        }
    }

    @Test
    void rejectsRuntimeCollisionWithApplicationContent() throws IOException {
        Path app = temp.resolve("collision.zip");
        try (Archive archive = new Archive(temp)) {
            file(archive, "app/bin/run", UNIX, 0755);
            file(archive, "app/bin/run.bat", WINDOWS, 0644);
            file(archive, "app/jdk/release", "existing runtime", 0644);
            archive.write(app, "zip", TIMESTAMP);
        }
        Path runtime = runtime(temp, "zip", "jdk", false);
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        bundle(app, runtime, temp.resolve("result.zip"), "app", "appassembler", "run", null, TIMESTAMP))
                .withMessageContaining("Conflicting");
    }

    private static Path application(Path directory, String format, boolean netbeans) throws IOException {
        Path output = directory.resolve("application." + format);
        try (Archive archive = new Archive(directory)) {
            if (netbeans) {
                file(archive, "app/etc/brand.conf", "other=value\r\n#jdkhome=\"/path/to/jdk\"\r\n", 0644);
            } else {
                file(archive, "app/bin/run", UNIX, 0644);
                file(archive, "app/bin/run.bat", WINDOWS, 0644);
            }
            file(archive, "app/data.txt", "untouched", 0644);
            archive.write(output, format, TIMESTAMP);
        }
        return output;
    }

    private static Path runtime(Path directory, String format, String home, boolean links) throws IOException {
        Path output = directory.resolve("runtime." + format);
        try (Archive archive = new Archive(directory)) {
            file(archive, home + "/bin/java", "binary", 0755);
            file(archive, home + "/release", "JAVA_VERSION=\"21\"", 0644);
            file(archive, home + "/restricted", "restricted", 0);
            file(
                    archive,
                    home + "/legal/" + String.join("", java.util.Collections.nCopies(8, "long-component-"))
                            + "/LICENSE",
                    "license",
                    0644);
            if (links) {
                file(archive, home + "/lib/server/library", "library", 0644);
                archive.add(new Archive.Entry(home + "/lib/current", EntryType.SYMLINK, 0777, null, "server"));
                archive.add(new Archive.Entry(
                        home + "/lib/copy", EntryType.HARDLINK, 0644, null, home + "/lib/server/library"));
            }
            archive.write(output, format, TIMESTAMP);
        }
        return output;
    }

    private static void file(Archive archive, String name, String content, int mode) throws IOException {
        archive.add(new Archive.Entry(
                name,
                EntryType.FILE,
                mode,
                archive.store(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))),
                null));
    }

    private static String text(Archive archive, String name) throws IOException {
        return new String(
                Files.readAllBytes(archive.regular(archive.entries.get(name)).content), StandardCharsets.UTF_8);
    }

    private static void bundle(
            Path application,
            Path runtime,
            Path output,
            String base,
            String launcher,
            String appName,
            String javaHome,
            Instant timestamp)
            throws IOException {
        Bundler.bundle(BundleRequest.builder()
                .application(application)
                .baseDirectory(base)
                .launcher(launcher)
                .appName(appName)
                .timestamp(timestamp)
                .runtime(BundleRequest.RuntimeBundle.builder()
                        .id("test-runtime")
                        .archive(runtime)
                        .output(output)
                        .javaHome(javaHome)
                        .build())
                .build());
    }
}
