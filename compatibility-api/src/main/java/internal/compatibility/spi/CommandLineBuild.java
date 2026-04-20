package internal.compatibility.spi;

import com.github.mustachejava.DefaultMustacheFactory;
import internal.compatibility.TempPath;
import lombok.NonNull;
import nbbrd.compatibility.Artifact;
import nbbrd.compatibility.Ref;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Build;
import nbbrd.design.VisibleForTesting;
import nbbrd.io.text.TextParser;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static internal.compatibility.Collectors2.toFirst;
import static internal.compatibility.Collectors2.toSingle;
import static internal.compatibility.spi.MvnCommand.FailStrategy.FAIL_NEVER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@lombok.Builder
public final class CommandLineBuild implements Build {

    @lombok.Builder.Default
    private final @NonNull Consumer<? super String> onEvent = nbbrd.compatibility.spi.Builder.IGNORE_EVENT;

    private final @Nullable Path mvn;

    private final @Nullable Path git;

    @lombok.Builder.Default
    private final @NonNull DefaultMustacheFactory mustacheFactory = new DefaultMustacheFactory("internal/compatibility/spi");

    private MvnCommand.Builder mvnOf(Path project) {
        return MvnCommand.builder().binary(mvn).quiet(true).batchMode(true).file(project);
    }

    @Override
    public void clean(@NonNull Path project) throws IOException {
        // https://maven.apache.org/plugins/maven-clean-plugin/clean-mojo.html
        mvnOf(project)
                .goal("clean")
                .build()
                .toTextProcessor()
                .withListener(onEvent)
                .process();
    }

    @Override
    public void restore(@NonNull Path project) throws IOException {
        GitCommand
                .builder()
                .binary(git)
                .quiet(true)
                .workingDir(project)
                .command("restore")
                .parameter(".")
                .build()
                .toTextProcessor()
                .withListener(onEvent)
                .process();
    }

    @Override
    public @Nullable String verify(@NonNull Path project) throws IOException {
        // https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html
        String errorMessage = mvnOf(project)
                .goal("clean")
                .goal("verify")
                .updateSnapshots(true)
                .failStrategy(FAIL_NEVER)
                .property("skipTests", null)
                .property("enforcer.skip", null)
                .build()
                .toTextProcessor()
                .withListener(onEvent)
                .processToString();
        return !errorMessage.isEmpty() ? errorMessage : null;
    }

    @Override
    public @NonNull Version getProjectVersion(@NonNull Path project) throws IOException {
        // https://maven.apache.org/plugins/maven-help-plugin/evaluate-mojo.html
        return mvnOf(project)
                .goal("help:evaluate")
                .property("expression", "project.version")
                .property("forceStdout", null)
                .build()
                .toTextProcessor()
                .withListener(onEvent)
                .process(toFirst())
                .map(Version::parse)
                .orElseThrow(() -> new IOException("Failed to get version"));
    }

    @Override
    public @Nullable Version getArtifactVersion(@NonNull Path project, @NonNull Artifact artifact) throws IOException {
        try (TempPath list = TempPath.of(Files.createTempFile("list", ".txt"))) {
            // https://maven.apache.org/plugins/maven-dependency-plugin/collect-mojo.html
            mvnOf(project)
                    .goal("dependency:LATEST:collect")
                    .property("includeScope", "compile")
                    .property("mdep.outputScope", "false")
                    .property("excludeTransitive", null)
                    .property("outputFile", list.toString())
                    .property("appendOutput", null)
                    .build()
                    .toTextProcessor()
                    .withListener(onEvent)
                    .process();

            return TextParser.onParsingLines(CommandLineBuild::parseDependencyList)
                    .parsePath(list.getPath(), UTF_8)
                    .stream()
                    .filter(artifact.toFilter())
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
                .property("depVersion", version.toString())
                .property("includes", artifact.toString())
                .property("processProperties", null)
                .property("generateBackupPoms", "false")
                .property("forceVersion", null)
                .build()
                .toTextProcessor()
                .withListener(onEvent)
                .process();
    }

    @Override
    public @Nullable Version getArtifactLatestRelease(@NonNull Artifact artifact) throws IOException {
        try (TempPath tmp = TempPath.of(Files.createTempFile("pom", ".xml"))) {
            Artifact fixedArtifact = artifact
                    .toBuilder()
                    .type(artifact.getType().isEmpty() ? "jar" : artifact.getType())
                    .build();

            try (Writer writer = Files.newBufferedWriter(tmp.getPath(), UTF_8)) {
                mustacheFactory
                        .compile("latestRelease.xml")
                        .execute(writer, fixedArtifact);
            }

            String result = mvnOf(tmp.getPath())
                    .quiet(false)
                    .goal("versions:use-latest-releases")
                    .property("generateBackupPoms", "false")
                    .build()
                    .toTextProcessor()
                    .withListener(onEvent)
                    .processToString();

            int start = result.indexOf("to version ");
            int end = result.indexOf(" in ");

            return start != -1 && end != -1 && start < end
                    ? Version.parse(result.substring(start + "to version ".length(), end))
                    : null;
        }
    }

    @Override
    public void checkoutTag(@NonNull Path project, @NonNull Ref ref) throws IOException {
        GitCommand
                .builder()
                .binary(git)
                .quiet(true)
                .workingDir(project)
                .command("checkout")
                .parameter(ref.getName())
                .build()
                .toTextProcessor()
                .withListener(onEvent)
                .process();
    }

    @Override
    public @NonNull List<Ref> getTags(@NonNull Path project) throws IOException {
        return GitCommand
                .builder()
                .binary(git)
                .workingDir(project)
                .command("tag")
                .option("--sort", "creatordate")
                .option("--format", "%(creatordate:short)/%(refname:strip=2)")
                .build()
                .toTextProcessor()
                .withListener(onEvent)
                .process(mapping(Ref::parse, toList()));
    }

    @Override
    public void clone(@NonNull URI from, @NonNull Path to) throws IOException {
        GitCommand
                .builder()
                .binary(git)
                .quiet(true)
                .command("clone")
                .parameter(from.toString())
                .parameter(to.toString())
                .build()
                .toTextProcessor()
                .withListener(onEvent)
                .process();
        fixReadOnlyFiles(to);
    }

    @Override
    public void close() {
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
