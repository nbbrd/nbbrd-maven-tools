package internal.actionlint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstallerTest {

    private static final Platform WINDOWS = Platform.of("Windows 11", "amd64");
    private static final Platform LINUX = Platform.of("Linux", "amd64");
    private static final byte[] CONTENT = "fake actionlint".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temp;

    final List<String> warnings = new ArrayList<>();

    @Test
    void downloadsVerifiesAndCachesZipArchive() throws Exception {
        Path server = publish("0.0.1", WINDOWS, zip("actionlint.exe"), null);
        Path executable = installer("0.0.1", WINDOWS, server, false).install();
        assertThat(executable)
                .hasFileName("actionlint.exe")
                .hasBinaryContent(CONTENT)
                .startsWith(temp.resolve("install"));
        assertThat(temp.resolve("cache/0.0.1/actionlint_0.0.1_windows_amd64.zip"))
                .isRegularFile();
        assertThat(warnings).singleElement().asString().contains("No pinned checksum");

        deleteRecursively(server);
        deleteRecursively(temp.resolve("install"));
        assertThat(installer("0.0.1", WINDOWS, server, true).install())
                .isEqualTo(executable)
                .hasBinaryContent(CONTENT);
    }

    @Test
    void downloadsAgainWhenCachedArchiveIsTampered() throws Exception {
        Path server = publish("0.0.1", WINDOWS, zip("actionlint.exe"), null);
        installer("0.0.1", WINDOWS, server, false).install();
        Path archive = temp.resolve("cache/0.0.1/actionlint_0.0.1_windows_amd64.zip");
        Files.write(archive, zip("evil.exe"));
        // the checksum file is gone: the recorded checksum must be used
        Files.delete(server.resolve("v0.0.1/actionlint_0.0.1_checksums.txt"));
        deleteRecursively(temp.resolve("install"));

        assertThat(installer("0.0.1", WINDOWS, server, false).install()).hasBinaryContent(CONTENT);
        assertThat(warnings).last().asString().contains("does not match its checksum");
    }

    @Test
    void rejectsTamperedCachedArchiveOffline() throws Exception {
        Path server = publish("0.0.1", WINDOWS, zip("actionlint.exe"), null);
        installer("0.0.1", WINDOWS, server, false).install();
        Files.write(temp.resolve("cache/0.0.1/actionlint_0.0.1_windows_amd64.zip"), zip("evil.exe"));
        deleteRecursively(temp.resolve("install"));

        assertThatIOException()
                .isThrownBy(() -> installer("0.0.1", WINDOWS, server, true).install())
                .withMessageContaining("offline");
    }

    @Test
    void reusesInstalledExecutableWithoutArchive() throws Exception {
        Path server = publish("0.0.1", WINDOWS, zip("actionlint.exe"), null);
        Path executable = installer("0.0.1", WINDOWS, server, false).install();
        Files.delete(temp.resolve("cache/0.0.1/actionlint_0.0.1_windows_amd64.zip"));

        assertThat(installer("0.0.1", WINDOWS, server, true).install()).isEqualTo(executable);
    }

    @Test
    void extractsAgainWhenInstalledExecutableIsTampered() throws Exception {
        Path server = publish("0.0.1", WINDOWS, zip("actionlint.exe"), null);
        Path executable = installer("0.0.1", WINDOWS, server, false).install();
        Files.write(executable, "evil".getBytes(StandardCharsets.UTF_8));

        assertThat(installer("0.0.1", WINDOWS, server, true).install()).hasBinaryContent(CONTENT);
    }

    @Test
    void retriesTransientFailuresOnly() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        assertThat(Installer.retry(2, 0, warnings::add, () -> {
                    if (calls.incrementAndGet() < 3) {
                        throw new Installer.HttpStatusException("url", 503);
                    }
                    return "ok";
                }))
                .isEqualTo("ok");
        assertThat(warnings).hasSize(2);

        calls.set(0);
        assertThatIOException()
                .isThrownBy(() -> Installer.retry(2, 0, warnings::add, () -> {
                    calls.incrementAndGet();
                    throw new Installer.HttpStatusException("url", 404);
                }));
        assertThat(calls).hasValue(1);

        calls.set(0);
        assertThatIOException()
                .isThrownBy(() -> Installer.retry(2, 0, warnings::add, () -> {
                    calls.incrementAndGet();
                    throw new IOException("connection reset");
                }));
        assertThat(calls).hasValue(3);
    }

    @Test
    void extractsTarGzExecutable() throws Exception {
        Path server = publish("0.0.1", LINUX, tarGz("actionlint"), null);
        assertThat(installer("0.0.1", LINUX, server, false).install())
                .hasFileName("actionlint")
                .hasBinaryContent(CONTENT)
                .isExecutable();
    }

    @Test
    void rejectsChecksumMismatchWithoutCaching() throws Exception {
        Path server = publish("0.0.1", WINDOWS, zip("actionlint.exe"), "00");
        assertThatIOException()
                .isThrownBy(() -> installer("0.0.1", WINDOWS, server, false).install())
                .withMessageContaining("Checksum mismatch");
        assertThat(temp.resolve("cache/0.0.1/actionlint_0.0.1_windows_amd64.zip"))
                .doesNotExist();
    }

    @Test
    void usesPinnedChecksumsInsteadOfRemoteOnes() throws Exception {
        Path server = publish("1.7.12", WINDOWS, zip("actionlint.exe"), null);
        assertThatIOException()
                .isThrownBy(() -> installer("1.7.12", WINDOWS, server, false).install())
                .withMessageContaining("Checksum mismatch");
        assertThat(warnings).isEmpty();
    }

    @Test
    void failsOfflineWithoutCache() {
        assertThatIOException()
                .isThrownBy(() -> installer("0.0.1", WINDOWS, temp, true).install())
                .withMessageContaining("offline");
    }

    @Test
    void failsWhenArchiveHasNoExecutable() throws Exception {
        Path server = publish("0.0.1", WINDOWS, zip("README.md"), null);
        assertThatIOException()
                .isThrownBy(() -> installer("0.0.1", WINDOWS, server, false).install())
                .withMessageContaining("not found");
    }

    @Test
    void parsesChecksumFiles() throws IOException {
        String content = "ABC  actionlint_1_linux_amd64.tar.gz\n\ndef  actionlint_1_windows_amd64.zip\n";
        assertThat(Installer.parseChecksums(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))))
                .containsEntry("actionlint_1_linux_amd64.tar.gz", "abc")
                .containsEntry("actionlint_1_windows_amd64.zip", "def")
                .hasSize(2);
    }

    @Test
    void derivesGitHubApiUrlFromReleaseUrl() {
        assertThat(Installer.getGitHubApiUrl("https://github.com/rhysd/actionlint/releases/download", "1.7.12"))
                .isEqualTo("https://api.github.com/repos/rhysd/actionlint/releases/tags/v1.7.12");
        assertThat(Installer.getGitHubApiUrl("https://mirror.example.com/actionlint", "1.7.12"))
                .isNull();
    }

    @Test
    void parsesGitHubAssetDigest() throws IOException {
        String json = "{\"assets\":["
                + "{\"name\":\"a.zip\",\"digest\":\"sha256:ABC\"},"
                + "{\"name\":\"b.zip\",\"digest\":null}]}";
        assertThat(Installer.parseGitHubDigest(json, "a.zip")).isEqualTo("abc");
        assertThat(Installer.parseGitHubDigest(json, "b.zip")).isNull();
        assertThat(Installer.parseGitHubDigest(json, "c.zip")).isNull();
        assertThat(Installer.parseGitHubDigest("{}", "a.zip")).isNull();
        assertThatIOException().isThrownBy(() -> Installer.parseGitHubDigest("[]", "a.zip"));
    }

    private Installer installer(String version, Platform platform, Path server, boolean offline) {
        return Installer.builder()
                .version(version)
                .baseUrl(server.toUri().toString())
                .cacheDirectory(temp.resolve("cache"))
                .installDirectory(temp.resolve("install"))
                .platform(platform)
                .offline(offline)
                .onInfo(message -> {})
                .onWarn(warnings::add)
                .build();
    }

    private Path publish(String version, Platform platform, byte[] archive, String checksum) throws Exception {
        Path server = temp.resolve("server");
        Path release = Files.createDirectories(server.resolve("v" + version));
        String archiveName = platform.getArchiveName(version);
        Files.write(release.resolve(archiveName), archive);
        String hash = checksum != null ? checksum : sha256(archive);
        Files.write(
                release.resolve("actionlint_" + version + "_checksums.txt"),
                (hash + "  " + archiveName + "\n").getBytes(StandardCharsets.UTF_8));
        return server;
    }

    private static byte[] zip(String name) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(result)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(CONTENT);
            zip.closeEntry();
        }
        return result.toByteArray();
    }

    private static byte[] tarGz(String name) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (OutputStream gzip = new GZIPOutputStream(result);
                TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
            TarArchiveEntry entry = new TarArchiveEntry(name);
            entry.setSize(CONTENT.length);
            tar.putArchiveEntry(entry);
            tar.write(CONTENT);
            tar.closeArchiveEntry();
        }
        return result.toByteArray();
    }

    private static String sha256(byte[] content) throws NoSuchAlgorithmException {
        StringBuilder result = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(content)) {
            result.append(String.format(Locale.ROOT, "%02x", b));
        }
        return result.toString();
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(path);
            }
        }
    }
}
