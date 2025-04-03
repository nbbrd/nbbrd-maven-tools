package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Tag;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Build;
import nbbrd.design.StaticFactoryMethod;
import nbbrd.io.sys.ProcessReader;
import nbbrd.io.win.PowerShellWrapper;
import nbbrd.io.win.WhereWrapper;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toList;

@lombok.Builder
public final class PowerShellBuild implements Build {

    @StaticFactoryMethod
    public static @NonNull PowerShellBuild getDefault() throws IOException {
        if (!WhereWrapper.isAvailable("mvn")) throw new IOException("mvn not found in PATH");
        if (!WhereWrapper.isAvailable("git")) throw new IOException("git not found in PATH");
        return PowerShellBuild.builder().build();
    }

    private final @Nullable Path mvn;

    private List<String> exec(String command) throws IOException {
        Path script = Files.createTempFile("script", ".ps1");
        Files.write(script, getScript(command), UTF_8, TRUNCATE_EXISTING);
        try (BufferedReader reader = ProcessReader.newReader(UTF_8, PowerShellWrapper.exec(script))) {
            return reader.lines().collect(toList());
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        } finally {
            Files.delete(script);
        }
    }

    private List<String> getScript(String command) {
        List<String> result = new ArrayList<>();
        if (mvn != null) {
            result.add(format(ROOT, "sal -name mvn -value '%s'", mvn));
        }
        result.add(command);
        return result;
    }

    @Override
    public void cleanAndRestore(@NonNull Path project) throws IOException {
        exec(
                format(ROOT, "mvn -q -f %s clean", project)
        );
        exec(
                format(ROOT, "git -C %s restore .", project)
        );
    }

    @Override
    public int verify(@NonNull Path project) throws IOException {
        return exec(
                format(ROOT, "mvn -q -f %s clean verify -U -DskipTests -D'enforcer.skip' ; echo $LASTEXITCODE", project)
        ).stream()
                .reduce((ignore, second) -> second)
                .map(Integer::valueOf)
                .orElseThrow(() -> new IOException("Failed to verify target"));
    }

    @Override
    public void setProperty(@NonNull Path project, @NonNull String propertyName, String propertyValue) throws IOException {
        exec(
                format(ROOT, "mvn -q -f %s versions:set-property -Dproperty='%s' -DnewVersion='%s'", project, propertyName, propertyValue)
        );
    }

    @Override
    public String getProperty(@NonNull Path project, @NonNull String propertyName) throws IOException {
        return exec(
                format(ROOT, "mvn -q -f %s help:evaluate -Dexpression='%s' -DforceStdout", project, propertyName)
        ).stream()
                .findFirst()
                .orElseThrow(() -> new IOException("Failed to get property " + propertyName));
    }

    @Override
    public @NonNull Version getVersion(@NonNull Path project) throws IOException {
        return exec(
                format(ROOT, "mvn -q -f %s help:evaluate -Dexpression='project.version' -DforceStdout", project)
        ).stream()
                .findFirst()
                .map(Version::parse)
                .orElseThrow(() -> new IOException("Failed to get version"));
    }

    @Override
    public void checkoutTag(@NonNull Path project, @NonNull Tag tag) throws IOException {
        exec(
                format(ROOT, "git -C %s checkout -q %s", project, tag)
        );
    }

    @Override
    public @NonNull List<Tag> getTags(@NonNull Path project) throws IOException {
        return exec(
                format(ROOT, "git -C %s tag --sort=-creatordate", project)
        ).stream()
                .map(Tag::parse)
                .collect(toList());
    }

    @Override
    public void clone(@NonNull URI from, @NonNull Path to) throws IOException {
        exec(
                format(ROOT, "git clone -q %s %s", from, to)
        );
    }

    @Override
    public void close() {
    }
}
