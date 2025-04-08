package tests.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Tag;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Build;
import nbbrd.compatibility.spi.Builder;
import org.semver4j.Semver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

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
    public @NonNull Build getBuild() {
        return MockedBuild.open(projects);
    }

    @lombok.RequiredArgsConstructor
    private static final class MockedBuild implements Build {

        public static MockedBuild open(List<MockedProject> projects) {
            return new MockedBuild(projects.stream().collect(toMap(MockedProject::getProjectId, p -> p)));
        }

        private final Map<String, MockedProject> projects;
        private final Map<String, MockedStatus> stuff = new HashMap<>();

        private static String toProjectId(Path project) throws IOException {
            String result = project.getFileName().toString();
            if (result.isEmpty()) {
                throw new IOException("Invalid project name");
            }
            return result;
        }

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
            checkAvailability(stuff.remove(toProjectId(project)));
        }

        @Override
        public int verify(@NonNull Path project) throws IOException {
            MockedStatus status = checkAvailability(stuff.get(toProjectId(project)));
            Semver original = new Semver(status.getOriginal().getValue());
            Semver modified = new Semver(status.getModified().getValue());
            if (original.isGreaterThan(modified) || !original.isApiCompatible(modified)) {
                return 1;
            }
            return 0;
        }

        @Override
        public void setProperty(@NonNull Path project, @NonNull String propertyName, String propertyValue) throws IOException {
            String id = toProjectId(project);
            stuff.put(id, stuff.computeIfAbsent(id, this::initStatus).withProperty(propertyName, propertyValue));
        }

        @Override
        public String getProperty(@NonNull Path project, @NonNull String propertyName) throws IOException {
            String id = toProjectId(project);
            return stuff.computeIfAbsent(id, this::initStatus).getProperty(propertyName);
        }

        @Override
        public @NonNull Version getVersion(@NonNull Path project) throws IOException {
            String id = toProjectId(project);
            return Version.parse(stuff.computeIfAbsent(id, this::initStatus).getModified().getVersion());
        }

        @Override
        public void checkoutTag(@NonNull Path project, @NonNull Tag tag) throws IOException {
            String id = toProjectId(project);
            stuff.put(id, MockedStatus.of(projects.get(id).getByTag(tag)));
        }

        @Override
        public @NonNull List<Tag> getTags(@NonNull Path project) throws IOException {
            String id = toProjectId(project);
            return projects.get(id)
                    .getVersions()
                    .stream()
                    .map(MockedVersion::getTag)
                    .collect(toList());
        }

        @Override
        public void clone(@NonNull URI from, @NonNull Path to) throws IOException {
            if (!"mocked".equals(from.getScheme())) {
                throw new IOException("Unsupported URI scheme: " + from.getScheme());
            }
            if (projects.containsKey(toProjectId(to))) {
                throw new IOException("Project " + to + " already exists");
            }
            Files.createDirectories(to.resolve("sub-dir"));
            MockedProject mockedProject = projects.get(from.toString().substring(7));
            projects.put(toProjectId(to), mockedProject);
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

    public static URI localURI(Path tmp, String name) {
        return tmp.resolve(name).toUri();
    }

    public static URI remoteURI(String name) {
        return URI.create("mocked:" + name);
    }
}
