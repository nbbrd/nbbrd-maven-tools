package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Tag;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Build;
import nbbrd.design.StaticFactoryMethod;
import nbbrd.io.sys.EndOfProcessException;
import nbbrd.io.sys.ProcessReader;
import nbbrd.io.win.WhereWrapper;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.*;

@lombok.Builder
public final class CommandLineBuild implements Build {

    @StaticFactoryMethod
    public static @NonNull CommandLineBuild getDefault() throws IOException {
        if (!WhereWrapper.isAvailable("mvn.cmd")) throw new IOException("mvn not found in PATH");
        if (!WhereWrapper.isAvailable("git")) throw new IOException("git not found in PATH");
        return CommandLineBuild.builder().build();
    }

    private final @Nullable Path mvn;

    public String getMvn() {
        return mvn != null ? mvn.toString() : "mvn.cmd";
    }

    @Override
    public void clean(@NonNull Path project) throws IOException {
        run(consume(), getMvn(), "-q", "-f", project.toString(), "clean");
    }

    @Override
    public void restore(@NonNull Path project) throws IOException {
        run(consume(), "git", "-C", project.toString(), "restore", ".");
    }

    @Override
    public int verify(@NonNull Path project) throws IOException {
        try {
            run(toLast(), getMvn(), "-q", "-f", project.toString(), "clean", "verify", "-U", "-DskipTests", "-Denforcer.skip");
            return 0;
        } catch (EndOfProcessException ex) {
            return ex.getExitValue();
        }
    }

    @Override
    public void setProperty(@NonNull Path project, @NonNull String propertyName, String propertyValue) throws IOException {
        run(consume(), getMvn(), "-q", "-f", project.toString(), "versions:set-property", "-Dproperty=\"" + propertyName + "\"", "-DnewVersion=\"" + propertyValue + "\"");
    }

    @Override
    public String getProperty(@NonNull Path project, @NonNull String propertyName) throws IOException {
        return run(toFirst(), getMvn(), "-q", "-f", project.toString(), "help:evaluate", "-Dexpression=\"" + propertyName + "\"", "-DforceStdout")
                .orElseThrow(() -> new IOException("Failed to get property " + propertyName));
    }

    @Override
    public @NonNull Version getVersion(@NonNull Path project) throws IOException {
        return run(toFirst(), getMvn(), "-q", "-f", project.toString(), "help:evaluate", "-Dexpression=\"project.version\"", "-DforceStdout")
                .map(Version::parse)
                .orElseThrow(() -> new IOException("Failed to get version"));
    }

    @Override
    public void checkoutTag(@NonNull Path project, @NonNull Tag tag) throws IOException {
        run(consume(), "git", "-C", project.toString(), "checkout", "-q", tag.toString());
    }

    @Override
    public @NonNull List<Tag> getTags(@NonNull Path project) throws IOException {
        return run(mapping(Tag::parse, toList()), "git", "-C", project.toString(), "tag", "--sort=-creatordate");
    }

    @Override
    public void clone(@NonNull URI from, @NonNull Path to) throws IOException {
        run(consume(), "git", "clone", "-q", from.toString(), to.toString());
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

    private static <X> X run(Collector<String, ?, X> collector, String... command) throws IOException {
        try (BufferedReader reader = ProcessReader.newReader(UTF_8, command)) {
            return reader.lines().collect(collector);
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }
}
