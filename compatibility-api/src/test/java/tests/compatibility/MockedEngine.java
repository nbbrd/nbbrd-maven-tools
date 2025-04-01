package tests.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.spi.JobEngine;
import nbbrd.compatibility.spi.JobExecutor;
import org.semver4j.Semver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@lombok.Value
@lombok.Builder
public class MockedEngine implements JobEngine {

    @lombok.Builder.Default
    String id = "mocked";

    @lombok.Builder.Default
    String name = "mocked";

    @lombok.Builder.Default
    boolean available = true;

    @lombok.Singular
    List<MockedProject> projects;

    @Override
    public @NonNull String getJobEngineId() {
        return id;
    }

    @Override
    public @NonNull String getJobEngineName() {
        return name;
    }

    @Override
    public boolean isJobEngineAvailable() {
        return available;
    }

    @Override
    public @NonNull JobExecutor getExecutor() {
        return MockedExecutor.open(projects);
    }

    @lombok.RequiredArgsConstructor
    private static final class MockedExecutor implements JobExecutor {

        public static MockedExecutor open(List<MockedProject> projects) {
            return new MockedExecutor(projects.stream().collect(toMap(MockedProject::getProjectId, p -> p)));
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
        public void cleanAndRestore(Path project) throws IOException {
            checkAvailability(stuff.remove(toProjectId(project)));
        }

        @Override
        public int verify(Path project) throws IOException {
            MockedStatus status = checkAvailability(stuff.get(toProjectId(project)));
            String original = status.getOriginal().getValue();
            String modified = status.getModified().getValue();
            if (new Semver(original).isGreaterThan(modified)) {
                return 1;
            }
            return 0;
        }

        @Override
        public void setProperty(Path project, String propertyName, String propertyValue) throws IOException {
            String id = toProjectId(project);
            stuff.put(id, stuff.computeIfAbsent(id, this::initStatus).withProperty(propertyName, propertyValue));
        }

        @Override
        public String getProperty(Path project, String propertyName) throws IOException {
            String id = toProjectId(project);
            return stuff.computeIfAbsent(id, this::initStatus).getProperty(propertyName);
        }

        @Override
        public String getVersion(Path project) throws IOException {
            String id = toProjectId(project);
            return stuff.computeIfAbsent(id, this::initStatus).getModified().getVersionId();
        }

        @Override
        public void checkoutTag(Path project, String tag) throws IOException {
            String id = toProjectId(project);
            stuff.put(id, MockedStatus.of(projects.get(id).getByTag(tag)));
        }

        @Override
        public List<String> getTags(Path project) throws IOException {
            String id = toProjectId(project);
            return projects.get(id)
                    .getVersions()
                    .stream()
                    .map(MockedVersion::getVersionId)
                    .collect(toList());
        }

        @Override
        public void clone(URI from, Path to) throws IOException {
            if (!from.getScheme().equals("mocked")) {
                throw new IOException("Unsupported URI scheme: " + from.getScheme());
            }
            if (projects.containsKey(toProjectId(to))) {
                throw new IOException("Project " + to + " already exists");
            }
            MockedProject mockedProject = projects.get(from.toString().substring(7));
            projects.put(toProjectId(to), mockedProject);
        }

        @Override
        public void install(Path project) throws IOException {
            String id = toProjectId(project);
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
}
