package tests.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Artifact;
import nbbrd.compatibility.Ref;
import nbbrd.compatibility.RefVersion;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Build;
import nbbrd.compatibility.spi.Builder;
import nbbrd.io.text.TextFormatter;
import nbbrd.io.text.TextParser;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.semver4j.Semver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static nbbrd.compatibility.RefVersion.remoteOf;

@lombok.Value
@lombok.Builder
public class MockedBuilder implements Builder {

    @lombok.Builder.Default
    String id = "mocked";

    @lombok.Builder.Default
    String name = "mocked";

    @lombok.Builder.Default
    boolean available = true;

    @lombok.Singular
    List<MockedProject> projects;

    @Override
    public @NonNull String getBuilderId() {
        return id;
    }

    @Override
    public @NonNull String getBuilderName() {
        return name;
    }

    @Override
    public boolean isBuilderAvailable() {
        return available;
    }

    @Override
    public @NonNull Build getBuild(@NonNull Consumer<? super String> onEvent) {
        return MockedBuild.open(projects);
    }

    @lombok.RequiredArgsConstructor
    private static final class MockedBuild implements Build {

        public static MockedBuild open(List<MockedProject> projects) {
            return new MockedBuild(projects.stream().collect(toMap(MockedProject::getProjectId, p -> p)));
        }

        private final Map<String, MockedProject> projects;
        private final Map<String, MockedStatus> stuff = new HashMap<>();

        private static MockedStatus checkAvailability(MockedStatus status) throws IOException {
            if (status == null) {
                throw new IOException("Status not available");
            }
            return status;
        }

        private MockedStatus initStatus(String x) {
            try {
                return MockedStatus.of(projects.get(x).getLatest());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        @Override
        public void clean(@NonNull Path project) throws IOException {
        }

        @Override
        public void restore(@NonNull Path project) throws IOException {
            checkAvailability(stuff.remove(loadProjectId(project)));
        }

        @Override
        public int verify(@NonNull Path project) throws IOException {
            MockedStatus status = checkAvailability(stuff.get(loadProjectId(project)));
            Semver original = new Semver(status.getOriginal().getValue());
            Semver modified = new Semver(status.getModified().getValue());
            if (original.isGreaterThan(modified) || !original.isApiCompatible(modified)) {
                return 1;
            }
            return 0;
        }

        private void setProperty(@NonNull Path project, @NonNull String propertyName, String propertyValue) throws IOException {
            String id = loadProjectId(project);
            stuff.put(id, stuff.computeIfAbsent(id, this::initStatus).withProperty(propertyName, propertyValue));
        }

        private String getProperty(@NonNull Path project, @NonNull String propertyName) throws IOException {
            String id = loadProjectId(project);
            return stuff.computeIfAbsent(id, this::initStatus).getProperty(propertyName);
        }

        @Override
        public @NonNull Version getProjectVersion(@NonNull Path project) throws IOException {
            String id = loadProjectId(project);
            return stuff.computeIfAbsent(id, this::initStatus).getModified().getVersion().getVersion();
        }

        @Override
        public @Nullable Version getArtifactVersion(@NonNull Path project, @NonNull Artifact artifact) throws IOException {
            String property = getProperty(project, artifact.getGroupId());
            return property != null ? Version.parse(property) : null;
        }

        @Override
        public void setArtifactVersion(@NonNull Path project, @NonNull Artifact artifact, @NonNull Version version) throws IOException {
            setProperty(project, artifact.getGroupId(), version.toString());
        }

        @Override
        public void checkoutTag(@NonNull Path project, @NonNull Ref ref) throws IOException {
            String id = loadProjectId(project);
            stuff.put(id, MockedStatus.of(projects.get(id).getByTag(ref)));
        }

        @Override
        public @NonNull List<Ref> getTags(@NonNull Path project) throws IOException {
            String id = loadProjectId(project);
            return projects.get(id)
                    .getVersions()
                    .stream()
                    .map(MockedVersion::getVersion)
                    .map(RefVersion::getRef)
                    .collect(toList());
        }

        @Override
        public void clone(@NonNull URI from, @NonNull Path to) throws IOException {
            if (!"mocked".equals(from.getScheme())) {
                throw new IOException("Unsupported URI scheme: " + from.getScheme());
            }
            if (projects.containsKey(loadProjectId(to))) {
                throw new IOException("Project " + to + " already exists");
            }
            Files.createDirectories(to.resolve("sub-dir"));
            MockedProject mockedProject = projects.get(from.toString().substring(7));
            projects.put(loadProjectId(to), mockedProject);
        }

        @Override
        public void close() throws IOException {
        }
    }

    @lombok.Value
    private static class MockedStatus {

        public static MockedStatus of(MockedVersion version) {
            return new MockedStatus(version, version);
        }

        @lombok.NonNull
        MockedVersion original;

        @lombok.NonNull
        MockedVersion modified;

        public MockedStatus withProperty(String propertyName, String propertyValue) throws IOException {
            return new MockedStatus(original, modified.withProperty(propertyName, propertyValue));
        }

        public String getProperty(String propertyName) throws IOException {
            return modified.getProperty(propertyName);
        }
    }

    public static URI localURI(Path tmp, String name) throws IOException {
        Path dir = tmp.resolve(name);
        Files.createDirectory(dir);
        storeProjectId(name, dir);
        return dir.toUri();
    }

    public static URI remoteURI(String name) {
        return URI.create("mocked:" + name);
    }

    private static String loadProjectId(Path project) throws IOException {
        Path idFile = project.resolve("id.txt");
        if (Files.exists(idFile)) return TextParser.onParsingLines(Collectors.joining()).parsePath(idFile, UTF_8);
        String result = project.getFileName().toString();
        if (result.isEmpty()) {
            throw new IOException("Invalid project name");
        }
        return result;
    }

    private static void storeProjectId(String id, Path project) throws IOException {
        Path idFile = project.resolve("id.txt");
        TextFormatter.onFormattingWriter((String id1, Writer writer) -> writer.write(id1)).formatPath(id, idFile, UTF_8);
    }

    public static final MockedBuilder EXAMPLE = MockedBuilder
            .builder()
            .project(MockedProject
                    .builder()
                    .projectId("source-project")
                    .version(MockedVersion.builder().version(remoteOf("2.3.4")).build())
                    .version(MockedVersion.builder().version(remoteOf("2.4.0")).build())
                    .version(MockedVersion.builder().version(remoteOf("3.0.0")).build())
                    .build())
            .project(MockedProject
                    .builder()
                    .projectId("target-project")
                    .version(MockedVersion.builder().version(remoteOf("1.0.0")).property("x", "2.3.4").build())
                    .version(MockedVersion.builder().version(remoteOf("1.0.1")).property("x", "2.4.0").build())
                    .version(MockedVersion.builder().version(remoteOf("1.0.2")).property("x", "3.0.0").build())
                    .build())
            .build();
}
