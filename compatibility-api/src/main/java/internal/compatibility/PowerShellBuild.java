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
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.*;

@lombok.Builder
public final class PowerShellBuild implements Build {

    @StaticFactoryMethod
    public static @NonNull PowerShellBuild getDefault() throws IOException {
        if (!WhereWrapper.isAvailable("mvn")) throw new IOException("mvn not found in PATH");
        if (!WhereWrapper.isAvailable("git")) throw new IOException("git not found in PATH");
        return PowerShellBuild.builder().build();
    }

    private final @Nullable Path mvn;

    private <X> X run(String command, Collector<String, ?, X> collector) throws IOException {
        Path script = Files.createTempFile("script", ".ps1");
        Files.write(script, getScript(command), UTF_8, TRUNCATE_EXISTING);
        try (BufferedReader reader = ProcessReader.newReader(UTF_8, PowerShellWrapper.exec(script))) {
            return reader.lines().collect(collector);
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
    public void clean(@NonNull Path project) throws IOException {
        run(format(ROOT, "mvn -q -f %s clean", project), consume());
    }

    @Override
    public void restore(@NonNull Path project) throws IOException {
        run(format(ROOT, "git -C %s restore .", project), consume());
    }

    @Override
    public int verify(@NonNull Path project) throws IOException {
        return run(
                format(ROOT, "mvn -q -f %s clean verify -U -DskipTests -D'enforcer.skip' ; echo $LASTEXITCODE", project),
                toLast()
        ).map(Integer::valueOf).orElseThrow(() -> new IOException("Failed to verify target"));
    }

    @Override
    public void setProperty(@NonNull Path project, @NonNull String propertyName, String propertyValue) throws IOException {
        run(format(ROOT, "mvn -q -f %s versions:set-property -Dproperty='%s' -DnewVersion='%s'", project, propertyName, propertyValue), consume());
    }

    @Override
    public String getProperty(@NonNull Path project, @NonNull String propertyName) throws IOException {
        return run(
                format(ROOT, "mvn -q -f %s help:evaluate -Dexpression='%s' -DforceStdout", project, propertyName),
                toFirst()
        ).orElseThrow(() -> new IOException("Failed to get property " + propertyName));
    }

    @Override
    public @NonNull Version getVersion(@NonNull Path project) throws IOException {
        return run(
                format(ROOT, "mvn -q -f %s help:evaluate -Dexpression='project.version' -DforceStdout", project),
                toFirst()
        ).map(Version::parse).orElseThrow(() -> new IOException("Failed to get version"));
    }

    @Override
    public void checkoutTag(@NonNull Path project, @NonNull Tag tag) throws IOException {
        run(format(ROOT, "git -C %s checkout -q %s", project, tag), consume());
    }

    @Override
    public @NonNull List<Tag> getTags(@NonNull Path project) throws IOException {
        return run(
                format(ROOT, "git -C %s tag --sort=-creatordate", project),
                mapping(Tag::parse, toList())
        );
    }

    @Override
    public void clone(@NonNull URI from, @NonNull Path to) throws IOException {
        run(format(ROOT, "git clone -q %s %s", from, to), consume());
        fixReadOnlyFiles(to);
    }

    @Override
    public void close() {
    }

    private static <X> Collector<X, ?, Optional<X>> toFirst() {
        return reducing((first, ignore) -> first);
    }

    private static <X> Collector<X, ?, Optional<X>> toLast() {
        return reducing((ignore, second) -> second);
    }

    private static <X> Collector<X, ?, Void> consume() {
        return reducing(null, ignore -> null, (ignoreFirst, ignoreSecond) -> null);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void fixReadOnlyFiles(Path to) throws IOException {
        try (Stream<Path> files = Files.list(to.resolve(".git").resolve("objects").resolve("pack"))) {
            files.forEach(file -> file.toFile().setWritable(true));
        }
    }
}
