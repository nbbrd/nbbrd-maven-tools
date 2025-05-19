package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Compatibility;
import nbbrd.compatibility.Filter;
import nbbrd.compatibility.RefVersion;
import nbbrd.compatibility.Source;
import nbbrd.compatibility.spi.Build;
import nbbrd.compatibility.spi.Builder;
import nbbrd.design.MightBePromoted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tests.compatibility.MockedBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static nbbrd.compatibility.RefVersion.localOf;
import static nbbrd.compatibility.RefVersion.remoteOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

class ProjectContextTest {

    @Test
    void testBuilderInit(@TempDir Path tmp) throws IOException {
        Path workingDir = Files.createDirectory(tmp.resolve("working-dir"));

        URI local = MockedBuilder.localURI(tmp, "source-project");
        URI remote = MockedBuilder.remoteURI("source-project");

        try (Build build = MockedBuilder.EXAMPLE.getBuild(Builder.IGNORE_EVENT)) {
            MockedProjectContextBuilder x = new MockedProjectContextBuilder();

            assertThat(x.clear().init(Source.builder().uri(remote).build(), false, workingDir, build).result)
                    .returns(remote, MockedProjectContext::getUri)
                    .returns(getFirstDir(workingDir), MockedProjectContext::getDirectory)
                    .extracting(MockedProjectContext::getVersions, list(RefVersion.class))
                    .containsExactly(
                            remoteOf("2.3.4"),
                            remoteOf("2.4.0"),
                            remoteOf("3.0.0")
                    );
            assertThat(workingDir).isNotEmptyDirectory();
            x.result.clean();
            assertThat(workingDir).isEmptyDirectory();

            assertThat(x.clear().init(Source.builder().uri(remote).filter(Filter.builder().limit(2).build()).build(), false, workingDir, build).result)
                    .returns(remote, MockedProjectContext::getUri)
                    .returns(getFirstDir(workingDir), MockedProjectContext::getDirectory)
                    .extracting(MockedProjectContext::getVersions, list(RefVersion.class))
                    .containsExactly(
                            remoteOf("2.4.0"),
                            remoteOf("3.0.0")
                    );
            assertThat(workingDir).isNotEmptyDirectory();
            x.result.clean();
            assertThat(workingDir).isEmptyDirectory();

            assertThat(x.clear().init(Source.builder().uri(local).build(), true, workingDir, build).result)
                    .returns(local, MockedProjectContext::getUri)
                    .returns(getFirstDir(workingDir), MockedProjectContext::getDirectory)
                    .extracting(MockedProjectContext::getVersions, list(RefVersion.class))
                    .containsExactly(
                            localOf("3.0.0")
                    );
            assertThat(workingDir).isNotEmptyDirectory();
            x.result.clean();
            assertThat(workingDir).isEmptyDirectory();
        }
    }

    @lombok.Data
    private static class MockedProjectContext implements ProjectContext {
        URI uri;
        Path directory;
        List<RefVersion> versions = new ArrayList<>();
    }

    private static class MockedProjectContextBuilder implements ProjectContext.Builder<MockedProjectContextBuilder> {

        MockedProjectContext result = new MockedProjectContext();

        @Override
        public MockedProjectContextBuilder uri(@NonNull URI uri) {
            result.setUri(uri);
            return this;
        }

        @Override
        public MockedProjectContextBuilder directory(@NonNull Path directory) {
            result.setDirectory(directory);
            return this;
        }

        @Override
        public MockedProjectContextBuilder version(@NonNull RefVersion version) {
            result.getVersions().add(version);
            return this;
        }

        MockedProjectContextBuilder clear() {
            this.result = new MockedProjectContext();
            return this;
        }
    }

    @MightBePromoted
    private static Path getFirstDir(Path tmp) throws IOException {
        try (Stream<Path> stream = Files.list(tmp)) {
            return stream.findFirst().orElse(null);
        }
    }
}
