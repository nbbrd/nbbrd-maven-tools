package internal.compatibility.spi;

import internal.compatibility.TempPath;
import lombok.NonNull;
import nbbrd.compatibility.Artifact;
import nbbrd.compatibility.Ref;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Build;
import nbbrd.design.MightBePromoted;
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
        // https://maven.apache.org/plugins/maven-clean-plugin/clean-mojo.html
        mvnOf(project)
                .goal("clean")
                .build()
                .collect(consuming(), onEvent);
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
                .collect(consuming(), onEvent);
    }

    @Override
    public int verify(@NonNull Path project) throws IOException {
        try {
            // https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html
            mvnOf(project)
                    .goal("clean")
                    .goal("verify")
                    .updateSnapshots()
                    .define("skipTests")
                    .define("enforcer.skip")
                    .build()
                    .collect(toLast(), onEvent);
            return 0;
        } catch (EndOfProcessException ex) {
            return ex.getExitValue();
        }
    }

    @Override
    public @NonNull Version getProjectVersion(@NonNull Path project) throws IOException {
        // https://maven.apache.org/plugins/maven-help-plugin/evaluate-mojo.html
        return mvnOf(project)
                .goal("help:evaluate")
                .define("expression", "project.version")
                .define("forceStdout")
                .build()
                .collect(toFirst(), onEvent)
                .map(Version::parse)
                .orElseThrow(() -> new IOException("Failed to get version"));
    }

    @Override
    public @Nullable Version getArtifactVersion(@NonNull Path project, @NonNull Artifact artifact) throws IOException {
        try (TempPath list = TempPath.of(Files.createTempFile("list", ".txt"))) {
            // https://maven.apache.org/plugins/maven-dependency-plugin/collect-mojo.html
            MvnCommandBuilder command = mvnOf(project)
                    .goal("dependency:LATEST:collect")
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
                    .collect(consuming(), onEvent);

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
        // https://www.mojohaus.org/versions/versions-maven-plugin/use-dep-version-mojo.html
        mvnOf(project)
                .goal("versions:use-dep-version")
                .define("depVersion", version.toString())
                .define("includes", artifact.toString())
                .define("processProperties")
                .define("generateBackupPoms", "false")
                .define("forceVersion")
                .build()
                .collect(consuming(), onEvent);
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
                .collect(consuming(), onEvent);
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
                .collect(mapping(Ref::parse, toList()), onEvent);
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
                .collect(consuming(), onEvent);
        fixReadOnlyFiles(to);
    }

    @Override
    public void close() {
    }

    @MightBePromoted
    private static <X> Collector<X, ?, Optional<X>> toFirst() {
        return reducing((first, ignore) -> first);
    }

    @MightBePromoted
    static <X> Collector<X, ?, Optional<X>> toLast() {
        return reducing((ignore, second) -> second);
    }

    @MightBePromoted
    private static <X> Collector<X, ?, Optional<X>> toSingle() {
        return collectingAndThen(toList(), list -> list.size() == 1 ? Optional.of(list.get(0)) : Optional.empty());
    }

    private static <X> Collector<X, ?, String> consuming() {
        return reducing("", ignore -> "", (ignoreFirst, ignoreSecond) -> "");
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
