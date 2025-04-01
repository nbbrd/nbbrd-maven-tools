package nbbrd.compatibility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tests.compatibility.MockedEngine;
import tests.compatibility.MockedProject;
import tests.compatibility.MockedVersion;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

class CompatibilityTest {

    @Test
    void executeNoOp() {
        Compatibility x = Compatibility.builder().build();
        assertThatIOException()
                .isThrownBy(() -> x.execute(Job.builder().build()));
    }

    @Test
    void executeDownstream(@TempDir Path tmp) throws IOException {
        URI localSource = tmp.resolve("source-project").toUri();
        URI remoteTarget = URI.create("mocked:target-project");

        Compatibility x = Compatibility
                .ofServiceLoader()
                .toBuilder()
                .engine(MockedEngine
                        .builder()
                        .project(MockedProject
                                .builder()
                                .projectId("source-project")
                                .version(MockedVersion.builder().versionId("2.3.4").build())
                                .build())
                        .project(MockedProject
                                .builder()
                                .projectId("target-project")
                                .version(MockedVersion.builder().versionId("1.0.0").name("x").value("2.3.4").build())
                                .version(MockedVersion.builder().versionId("1.0.1").name("x").value("2.4.0").build())
                                .version(MockedVersion.builder().versionId("1.0.2").name("x").value("3.0.0").build())
                                .build())
                        .build())
                .build();

        Job job = Job
                .builder()
                .source(Source
                        .builder()
                        .uri(localSource)
                        .tagging(Tagging.builder().versioning("semver").build())
                        .build())
                .target(Target
                        .builder()
                        .uri(remoteTarget)
                        .building(Building.builder().property("x").build())
                        .build())
                .workingDir(tmp)
                .build();

        assertThat(x.execute(job).getItems())
                .containsExactly(
                        ReportItem.builder().exitCode(0).targetUri(URI.create("mocked:target-project")).sourceVersion("2.3.4").targetVersion("1.0.0").defaultVersion("2.3.4").build()
                );
    }
}