package internal.compatibility.spi;

import lombok.NonNull;
import nbbrd.compatibility.Ref;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Build;
import nbbrd.design.StaticFactoryMethod;
import nbbrd.io.sys.EndOfProcessException;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

@lombok.Builder
public final class CommandLineBuild implements Build {

    @StaticFactoryMethod
    public static @NonNull CommandLineBuild getDefault() throws IOException {
        return CommandLineBuild.builder().build();
    }

    private final @Nullable Path mvn;

    private final @Nullable Path git;

    private MvnCommandBuilder mvnOf(Path project) {
        return new MvnCommandBuilder().binary(mvn).quiet().file(project);
    }

    @Override
    public void clean(@NonNull Path project) throws IOException {
        mvnOf(project)
                .goal("clean")
                .build()
                .collect(consuming());
    }

    @Override
    public void restore(@NonNull Path project) throws IOException {
        new GitCommandBuilder()
                .binary(git)
                .quiet()
                .workingDir(project)
                .command("restore")
                .parameter(".")
                .build()
                .collect(consuming());
    }

    @Override
    public int verify(@NonNull Path project) throws IOException {
        try {
            mvnOf(project)
                    .goal("clean")
                    .goal("verify")
                    .updateSnapshots()
                    .define("skipTests")
                    .define("enforcer.skip")
                    .build()
                    .collect(toLast());
            return 0;
        } catch (EndOfProcessException ex) {
            return ex.getExitValue();
        }
    }

    @Override
    public void setProperty(@NonNull Path project, @NonNull String propertyName, @Nullable String propertyValue) throws IOException {
        mvnOf(project)
                .goal("versions:set-property")
                .define("property", propertyName)
                .define("newVersion", propertyValue)
                .build()
                .collect(consuming());
    }

    @Override
    public String getProperty(@NonNull Path project, @NonNull String propertyName) throws IOException {
        return mvnOf(project)
                .goal("help:evaluate")
                .define("expression", propertyName)
                .define("forceStdout")
                .build()
                .collect(toFirst())
                .orElseThrow(() -> new IOException("Failed to get property " + propertyName));
    }

    @Override
    public @NonNull Version getVersion(@NonNull Path project) throws IOException {
        return mvnOf(project)
                .goal("help:evaluate")
                .define("expression", "project.version")
                .define("forceStdout")
                .build()
                .collect(toFirst())
                .map(Version::parse)
                .orElseThrow(() -> new IOException("Failed to get version"));
    }

    @Override
    public void checkoutTag(@NonNull Path project, @NonNull Ref ref) throws IOException {
        new GitCommandBuilder()
                .binary(git)
                .quiet()
                .workingDir(project)
                .command("checkout")
                .parameter(ref.getName())
                .build()
                .collect(consuming());
    }

    @Override
    public @NonNull List<Ref> getTags(@NonNull Path project) throws IOException {
        return new GitCommandBuilder()
                .binary(git)
                .workingDir(project)
                .command("tag")
                .option("--sort", "creatordate")
                .option("--format", "%(creatordate:short)/%(refname:strip=2)")
                .build()
                .collect(mapping(Ref::parse, toList()));
    }

    @Override
    public void clone(@NonNull URI from, @NonNull Path to) throws IOException {
        new GitCommandBuilder()
                .binary(git)
                .quiet()
                .command("clone")
                .parameter(from.toString())
                .parameter(to.toString())
                .build()
                .collect(consuming());
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

    private static <X> Collector<X, ?, Void> consuming() {
        return reducing(null, ignore -> null, (ignoreFirst, ignoreSecond) -> null);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void fixReadOnlyFiles(Path to) throws IOException {
        try (Stream<Path> files = Files.list(to.resolve(".git").resolve("objects").resolve("pack"))) {
            files.forEach(file -> file.toFile().setWritable(true));
        }
    }
}
