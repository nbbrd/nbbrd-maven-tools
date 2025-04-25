package internal.compatibility.spi;

import internal.compatibility.TempPath;
import lombok.NonNull;
import nbbrd.compatibility.Artifact;
import nbbrd.compatibility.Ref;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Build;
import nbbrd.design.VisibleForTesting;
import nbbrd.io.sys.EndOfProcessException;
import nbbrd.io.text.TextParser;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.*;

@lombok.Builder
public final class CommandLineBuild implements Build {

    @lombok.Builder.Default
    private final @NonNull Consumer<? super String> onEvent = nbbrd.compatibility.spi.Builder.IGNORE_EVENT;

    private final @Nullable Path mvn;

    private final @Nullable Path git;

    private MvnCommandBuilder mvnOf(Path project) {
        return new MvnCommandBuilder().binary(mvn).quiet().batchMode().file(project);
    }

    @Override
    public void clean(@NonNull Path project) throws IOException {
        onEvent.accept("cleaning " + project);
        mvnOf(project)
                .goal("clean")
                .build()
                .report(onEvent)
                .collect(consuming());
    }

    @Override
    public void restore(@NonNull Path project) throws IOException {
        onEvent.accept("restoring " + project);
        new GitCommandBuilder()
                .binary(git)
                .quiet()
                .workingDir(project)
                .command("restore")
                .parameter(".")
                .build()
                .report(onEvent)
                .collect(consuming());
    }

    @Override
    public int verify(@NonNull Path project) throws IOException {
        onEvent.accept("verifying " + project);
        try {
            mvnOf(project)
                    .goal("clean")
                    .goal("verify")
                    .updateSnapshots()
                    .define("skipTests")
                    .define("enforcer.skip")
                    .build()
                    .report(onEvent)
                    .collect(toLast());
            return 0;
        } catch (EndOfProcessException ex) {
            return ex.getExitValue();
        }
    }

    @Override
    public void setProperty(@NonNull Path project, @NonNull String propertyName, @Nullable String propertyValue) throws IOException {
        onEvent.accept("setting property " + propertyName + "=" + propertyValue + " to " + project);
        mvnOf(project)
                .goal("versions:set-property")
                .define("property", propertyName)
                .define("newVersion", propertyValue)
                .build()
                .report(onEvent)
                .collect(consuming());
    }

    @Override
    public String getProperty(@NonNull Path project, @NonNull String propertyName) throws IOException {
        onEvent.accept("getting property " + propertyName + " from " + project);
        return mvnOf(project)
                .goal("help:evaluate")
                .define("expression", propertyName)
                .define("forceStdout")
                .build()
                .report(onEvent)
                .collect(toFirst())
                .orElseThrow(() -> new IOException("Failed to get property " + propertyName));
    }

    @Override
    public @NonNull Version getVersion(@NonNull Path project) throws IOException {
        onEvent.accept("getting version from " + project);
        return mvnOf(project)
                .goal("help:evaluate")
                .define("expression", "project.version")
                .define("forceStdout")
                .build()
                .report(onEvent)
                .collect(toFirst())
                .map(Version::parse)
                .orElseThrow(() -> new IOException("Failed to get version"));
    }

    @Override
    public @Nullable Version getArtifactVersion(@NonNull Path project, @NonNull Artifact artifact) throws IOException {
        onEvent.accept("getting version of " + artifact + " from " + project);
        try (TempPath list = TempPath.of(Files.createTempFile("list", ".txt"))) {

            MvnCommandBuilder command = mvnOf(project)
                    .goal("dependency:list")
                    .define("includeScope", "compile")
                    .define("mdep.outputScope", "false")
                    .define("excludeTransitive")
                    .define("outputFile", list.toString())
                    .define("appendOutput");
            if (!artifact.getGroupId().isEmpty()) command.define("includeGroupIds", artifact.getGroupId());
            if (!artifact.getArtifactId().isEmpty()) command.define("includeArtifactIds", artifact.getArtifactId());
            if (!artifact.getClassifier().isEmpty()) command.define("includeClassifiers", artifact.getClassifier());
            if (!artifact.getType().isEmpty()) command.define("includeTypes", artifact.getType());
            command
                    .build()
                    .report(onEvent)
                    .collect(consuming());

            return TextParser.onParsingLines(CommandLineBuild::parseDependencyList)
                    .parsePath(list.getPath(), UTF_8)
                    .stream()
                    .map(Artifact::getVersion)
                    .map(Version::parse)
                    .distinct()
                    .collect(toSingle())
                    .orElse(null);
        }
    }

    @Override
    public void setArtifactVersion(@NonNull Path project, @NonNull Artifact artifact, @NonNull Version version) throws IOException {
        onEvent.accept("setting artifact " + artifact + "=" + version + " to " + project);
        mvnOf(project)
                .goal("versions:use-dep-version")
                .define("depVersion", version.toString())
                .define("includes", artifact.toString())
                .define("processProperties")
                .define("generateBackupPoms", "false")
                .define("forceVersion")
                .build()
                .report(onEvent)
                .collect(consuming());
    }

    @Override
    public void checkoutTag(@NonNull Path project, @NonNull Ref ref) throws IOException {
        onEvent.accept("checking out tag " + ref + " to " + project);
        new GitCommandBuilder()
                .binary(git)
                .quiet()
                .workingDir(project)
                .command("checkout")
                .parameter(ref.getName())
                .build()
                .report(onEvent)
                .collect(consuming());
    }

    @Override
    public @NonNull List<Ref> getTags(@NonNull Path project) throws IOException {
        onEvent.accept("getting tags from " + project);
        return new GitCommandBuilder()
                .binary(git)
                .workingDir(project)
                .command("tag")
                .option("--sort", "creatordate")
                .option("--format", "%(creatordate:short)/%(refname:strip=2)")
                .build()
                .report(onEvent)
                .collect(mapping(Ref::parse, toList()));
    }

    @Override
    public void clone(@NonNull URI from, @NonNull Path to) throws IOException {
        onEvent.accept("cloning " + from + " to " + to);
        new GitCommandBuilder()
                .binary(git)
                .quiet()
                .command("clone")
                .parameter(from.toString())
                .parameter(to.toString())
                .build()
                .report(onEvent)
                .collect(consuming());
        fixReadOnlyFiles(to);
    }

    @Override
    public void close() {
        onEvent.accept("closing build");
    }

    private static <X> Collector<X, ?, Optional<X>> toFirst() {
        return reducing((first, ignore) -> first);
    }

    private static <X> Collector<X, ?, Optional<X>> toLast() {
        return reducing((ignore, second) -> second);
    }

    private static <X> Collector<X, ?, Optional<X>> toSingle() {
        return collectingAndThen(toList(), list -> list.size() == 1 ? Optional.of(list.get(0)) : Optional.empty());
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

    @VisibleForTesting
    static List<Artifact> parseDependencyList(Stream<String> lines) {
        return lines
                .map(CommandLineBuild::parseDependency)
                .filter(Objects::nonNull)
                .collect(toList());
    }

    private static Artifact parseDependency(String line) {
        if (!line.startsWith("   ") || line.equals("   none")) {
            return null;
        }
        line = line.substring(3);
        int index = line.indexOf(' ');
        line = index != -1 ? line.substring(0, index) : line;
        String[] items = line.split(":", -1);
        if (items.length != 4) {
            return null;
        }
        return Artifact.builder()
                .groupId(items[0])
                .artifactId(items[1])
                .type(items[2])
                .version(items[3])
                .build();
    }
}
