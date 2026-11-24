package internal.actionlint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.jspecify.annotations.Nullable;

/**
 * Downloads and caches the verified actionlint archive, then extracts its executable.
 *
 * <p>The expected SHA-256 is resolved from pinned checksums, then from a checksum recorded by a previous download,
 * then from the GitHub release API and finally from the release checksum file.
 */
@lombok.Builder
public final class Installer {

    private static final int TIMEOUT_MILLIS = 30_000;
    private static final long RETRY_DELAY_MILLIS = 1_000;
    private static final String SHA256_PREFIX = "sha256:";
    private static final Pattern GITHUB_RELEASES =
            Pattern.compile("https://github\\.com/([^/]+)/([^/]+)/releases/download/?");

    @NonNull private final String version;

    @NonNull private final String baseUrl;

    @NonNull private final Path cacheDirectory;

    @NonNull private final Path installDirectory;

    @NonNull private final Platform platform;

    private final boolean offline;

    private final @Nullable HttpProxy proxy;

    @lombok.Builder.Default
    private final int retries = 2;

    @NonNull private final Consumer<String> onInfo;

    @NonNull private final Consumer<String> onWarn;

    public @NonNull Path install() throws IOException {
        String archiveName = platform.getArchiveName(version);
        Path archive = cacheDirectory.resolve(version).resolve(archiveName);
        String expected = resolveChecksum(archiveName, archive.resolveSibling(archiveName + ".sha256"));
        Path executable = installDirectory
                .resolve(version)
                .resolve(platform.getOs() + "_" + platform.getArch())
                .resolve(platform.getExecutableName());
        Path marker = executable.resolveSibling(executable.getFileName() + ".sha256");
        if (isInstalled(executable, marker, expected)) {
            return executable;
        }
        if (!Files.isRegularFile(archive)) {
            download(archive, expected);
        } else if (!sha256(archive).equals(expected)) {
            onWarn.accept("Cached archive '" + archive + "' does not match its checksum; downloading it again");
            download(archive, expected);
        }
        extract(archive, executable);
        writeAtomically(marker, expected + " " + sha256(executable));
        return executable;
    }

    private static boolean isInstalled(Path executable, Path marker, String expected) throws IOException {
        if (!Files.isRegularFile(executable) || !Files.isRegularFile(marker)) {
            return false;
        }
        String[] parts = readString(marker).split(" ", -1);
        return parts.length == 2 && parts[0].equals(expected) && parts[1].equals(sha256(executable));
    }

    private String resolveChecksum(String archiveName, Path recorded) throws IOException {
        String pinned = loadPinnedChecksums().get(archiveName);
        if (pinned != null) {
            return pinned;
        }
        if (Files.isRegularFile(recorded)) {
            return readString(recorded);
        }
        if (offline) {
            throw newOfflineException(recorded.getParent());
        }
        String result = loadRemoteChecksum(archiveName);
        writeAtomically(recorded, result);
        return result;
    }

    private Map<String, String> loadPinnedChecksums() throws IOException {
        try (InputStream embedded = Installer.class.getResourceAsStream(getChecksumsFileName())) {
            return embedded != null ? parseChecksums(embedded) : Collections.emptyMap();
        }
    }

    private String loadRemoteChecksum(String archiveName) throws IOException {
        String apiUrl = getGitHubApiUrl(baseUrl, version);
        if (apiUrl != null) {
            try {
                String json = retry(retries, RETRY_DELAY_MILLIS, onWarn, () -> readUrl(apiUrl, getGitHubHeaders()));
                String digest = parseGitHubDigest(json, archiveName);
                if (digest != null) {
                    onWarn.accept("No pinned checksum for actionlint " + version + "; using GitHub asset digest");
                    return digest;
                }
            } catch (IOException ex) {
                onWarn.accept("Cannot read GitHub asset digest: " + ex.getMessage());
            }
        }
        String url = getReleaseUrl(getChecksumsFileName());
        onWarn.accept("No pinned checksum for actionlint " + version + "; trusting " + url);
        String result = retry(retries, RETRY_DELAY_MILLIS, onWarn, () -> {
                    try (InputStream remote = open(url, Collections.emptyMap())) {
                        return parseChecksums(remote);
                    }
                })
                .get(archiveName);
        if (result == null) {
            throw new IOException("No actionlint " + version + " release archive for platform " + platform.getOs() + "/"
                    + platform.getArch());
        }
        return result;
    }

    private void download(Path archive, String expected) throws IOException {
        if (offline) {
            throw newOfflineException(archive.getParent());
        }
        Files.createDirectories(archive.getParent());
        Path temp =
                Files.createTempFile(archive.getParent(), archive.getFileName().toString(), ".tmp");
        try {
            String url = getReleaseUrl(archive.getFileName().toString());
            onInfo.accept("Downloading " + url);
            String actual = retry(retries, RETRY_DELAY_MILLIS, onWarn, () -> {
                MessageDigest digest = newSha256();
                try (InputStream input = new DigestInputStream(open(url, Collections.emptyMap()), digest);
                        OutputStream output = Files.newOutputStream(temp)) {
                    copy(input, output);
                }
                return toHex(digest.digest());
            });
            if (!actual.equals(expected)) {
                throw new IOException("Checksum mismatch for " + archive.getFileName() + ": expected " + expected
                        + " but was " + actual);
            }
            Files.move(temp, archive, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void extract(Path archive, Path executable) throws IOException {
        Files.createDirectories(executable.getParent());
        Path temp = Files.createTempFile(
                executable.getParent(), executable.getFileName().toString(), ".tmp");
        try {
            if (!extractTo(archive, temp)) {
                throw new IOException(
                        "Executable '" + executable.getFileName() + "' not found in " + archive.getFileName());
            }
            if (!temp.toFile().setExecutable(true)) {
                throw new IOException("Cannot make '" + temp + "' executable");
            }
            Files.move(temp, executable, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private boolean extractTo(Path archive, Path target) throws IOException {
        String name = platform.getExecutableName();
        try (InputStream input = Files.newInputStream(archive)) {
            if (platform.isWindows()) {
                ZipInputStream zip = new ZipInputStream(input);
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.isDirectory() && getFileName(entry.getName()).equals(name)) {
                        Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                        return true;
                    }
                }
            } else {
                TarArchiveInputStream tar = new TarArchiveInputStream(new GZIPInputStream(input));
                TarArchiveEntry entry;
                while ((entry = tar.getNextEntry()) != null) {
                    if (entry.isFile() && getFileName(entry.getName()).equals(name)) {
                        Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private IOException newOfflineException(Path directory) {
        return new IOException(
                "actionlint " + version + " is not cached in '" + directory + "' and Maven is in offline mode");
    }

    private String getChecksumsFileName() {
        return "actionlint_" + version + "_checksums.txt";
    }

    private String getReleaseUrl(String fileName) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/v" + version + "/" + fileName;
    }

    private String readUrl(String url, Map<String, String> headers) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (InputStream input = open(url, headers)) {
            copy(input, result);
        }
        return new String(result.toByteArray(), StandardCharsets.UTF_8);
    }

    private InputStream open(String url, Map<String, String> headers) throws IOException {
        URI uri = URI.create(url);
        URL target = uri.toURL();
        boolean http = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        URLConnection connection =
                proxy != null && http ? target.openConnection(proxy.toProxy(uri)) : target.openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        headers.forEach(connection::setRequestProperty);
        if (connection instanceof HttpURLConnection) {
            int code = ((HttpURLConnection) connection).getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new HttpStatusException(url, code);
            }
        }
        return connection.getInputStream();
    }

    @FunctionalInterface
    interface IOSupplier<T> {
        T get() throws IOException;
    }

    static <T> T retry(int retries, long delayMillis, Consumer<String> onWarn, IOSupplier<T> action)
            throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                return action.get();
            } catch (IOException ex) {
                if (attempt >= retries || !isRetryable(ex)) {
                    throw ex;
                }
                onWarn.accept(ex.getMessage() + "; retrying (" + (attempt + 1) + "/" + retries + ")");
                try {
                    Thread.sleep(delayMillis * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    InterruptedIOException result = new InterruptedIOException("Download interrupted");
                    result.addSuppressed(ex);
                    throw result;
                }
            }
        }
    }

    static boolean isRetryable(IOException ex) {
        if (ex instanceof HttpStatusException) {
            int code = ((HttpStatusException) ex).getCode();
            return code >= 500 || code == 429;
        }
        if (ex instanceof SocketTimeoutException) {
            return true;
        }
        return !(ex instanceof FileNotFoundException
                || ex instanceof NoSuchFileException
                || ex instanceof InterruptedIOException);
    }

    static final class HttpStatusException extends IOException {

        @Getter
        private final int code;

        HttpStatusException(String url, int code) {
            super("Cannot download " + url + ": HTTP " + code);
            this.code = code;
        }
    }

    static @Nullable String getGitHubApiUrl(String baseUrl, String version) {
        Matcher matcher = GITHUB_RELEASES.matcher(baseUrl);
        return matcher.matches()
                ? "https://api.github.com/repos/" + matcher.group(1) + "/" + matcher.group(2) + "/releases/tags/v"
                        + version
                : null;
    }

    static @Nullable String parseGitHubDigest(String json, String assetName) throws IOException {
        try {
            JsonArray assets = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("assets");
            if (assets == null) {
                return null;
            }
            for (JsonElement element : assets) {
                JsonObject asset = element.getAsJsonObject();
                JsonElement name = asset.get("name");
                if (name != null && name.getAsString().equals(assetName)) {
                    JsonElement digest = asset.get("digest");
                    if (digest == null
                            || digest.isJsonNull()
                            || !digest.getAsString().startsWith(SHA256_PREFIX)) {
                        return null;
                    }
                    return digest.getAsString()
                            .substring(SHA256_PREFIX.length())
                            .toLowerCase(Locale.ROOT);
                }
            }
            return null;
        } catch (JsonParseException | IllegalStateException | ClassCastException | UnsupportedOperationException ex) {
            throw new IOException("Cannot parse GitHub release", ex);
        }
    }

    static Map<String, String> parseChecksums(InputStream stream) throws IOException {
        Map<String, String> result = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.trim().split("\\s+", 2);
            if (parts.length == 2) {
                result.put(parts[1].trim(), parts[0].toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static Map<String, String> getGitHubHeaders() {
        Map<String, String> result = new HashMap<>();
        result.put("Accept", "application/vnd.github+json");
        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isEmpty()) {
            result.put("Authorization", "Bearer " + token);
        }
        return result;
    }

    private static String readString(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
    }

    /** Writes through a temporary file so that concurrent builds never read a partial file. */
    private static void writeAtomically(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, content.getBytes(StandardCharsets.UTF_8));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String getFileName(String entryName) {
        return entryName.substring(entryName.lastIndexOf('/') + 1);
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", b));
        }
        return result.toString();
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
