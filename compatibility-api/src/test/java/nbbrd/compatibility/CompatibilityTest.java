package nbbrd.compatibility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tests.compatibility.MockedBuilder;
import tests.compatibility.MockedProject;
import tests.compatibility.MockedVersion;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import static nbbrd.compatibility.ExitStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static tests.compatibility.MockedBuilder.localURI;
import static tests.compatibility.MockedBuilder.remoteURI;

class CompatibilityTest {

    @Test
    void executeNoOp() {
        Compatibility x = Compatibility.builder().build();
        assertThatIOException()
                .isThrownBy(() -> x.execute(Job.builder().build()));
    }

    @Test
    void executeDownstream(@TempDir Path tmp) throws IOException {
        URI localSource = localURI(tmp, "source-project");
        URI remoteTarget = remoteURI("target-project");

        Compatibility x = Compatibility.ofServiceLoader().toBuilder().builder(example).build();

        Job job = Job
                .builder()
                .source(Source
                        .builder()
                        .uri(localSource)
                        .versioning("semver")
                        .build())
                .target(Target
                        .builder()
                        .uri(remoteTarget)
                        .property("x")
                        .build())
                .workingDir(tmp)
                .build();

        assertThat(x.execute(job).getItems())
                .containsExactly(
                        ReportItem.builder().exitStatus(BROKEN).source(localSource, "3.0.0").target(remoteTarget, "1.0.0").build(),
                        ReportItem.builder().exitStatus(BROKEN).source(localSource, "3.0.0").target(remoteTarget, "1.0.1").build(),
                        ReportItem.builder().exitStatus(VERIFIED).source(localSource, "3.0.0").target(remoteTarget, "1.0.2").build()
                );
    }

    @Test
    void executeUpstream(@TempDir Path tmp) throws IOException {
        URI remoteSource = remoteURI("source-project");
        URI localTarget = localURI(tmp, "target-project");

        Compatibility x = Compatibility.ofServiceLoader().toBuilder().builder(example).build();

        Job job = Job
                .builder()
                .source(Source
                        .builder()
                        .uri(remoteSource)
                        .versioning("semver")
                        .build())
                .target(Target
                        .builder()
                        .uri(localTarget)
                        .property("x")
                        .build())
                .workingDir(tmp)
                .build();

        assertThat(x.execute(job).getItems())
                .containsExactly(
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, "2.3.4").target(localTarget, "1.0.2").build(),
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, "2.4.0").target(localTarget, "1.0.2").build(),
                        ReportItem.builder().exitStatus(VERIFIED).source(remoteSource, "3.0.0").target(localTarget, "1.0.2").build()
                );
    }

    @Test
    void executeRemoteStreams(@TempDir Path tmp) throws IOException {
        URI remoteSource = remoteURI("source-project");
        URI remoteTarget = remoteURI("target-project");

        Compatibility x = Compatibility.ofServiceLoader().toBuilder().builder(example).build();

        Job job = Job
                .builder()
                .source(Source
                        .builder()
                        .uri(remoteSource)
                        .versioning("semver")
                        .build())
                .target(Target
                        .builder()
                        .uri(remoteTarget)
                        .property("x")
                        .build())
                .workingDir(tmp)
                .build();

        assertThat(x.execute(job).getItems())
                .containsExactly(
                        ReportItem.builder().exitStatus(VERIFIED).source(remoteSource, "2.3.4").target(remoteTarget, "1.0.0").build(),
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, "2.3.4").target(remoteTarget, "1.0.1").build(),
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, "2.3.4").target(remoteTarget, "1.0.2").build(),
                        ReportItem.builder().exitStatus(VERIFIED).source(remoteSource, "2.4.0").target(remoteTarget, "1.0.0").build(),
                        ReportItem.builder().exitStatus(VERIFIED).source(remoteSource, "2.4.0").target(remoteTarget, "1.0.1").build(),
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, "2.4.0").target(remoteTarget, "1.0.2").build(),
                        ReportItem.builder().exitStatus(BROKEN).source(remoteSource, "3.0.0").target(remoteTarget, "1.0.0").build(),
                        ReportItem.builder().exitStatus(BROKEN).source(remoteSource, "3.0.0").target(remoteTarget, "1.0.1").build(),
                        ReportItem.builder().exitStatus(VERIFIED).source(remoteSource, "3.0.0").target(remoteTarget, "1.0.2").build()
                );
    }

    @Test
    void executeLocalStreams(@TempDir Path tmp) throws IOException {
        URI localSource = localURI(tmp, "source-project");
        URI localTarget = localURI(tmp, "target-project");

        Compatibility x = Compatibility.ofServiceLoader().toBuilder().builder(example).build();

        Job job = Job
                .builder()
                .source(Source
                        .builder()
                        .uri(localSource)
                        .versioning("semver")
                        .build())
                .target(Target
                        .builder()
                        .uri(localTarget)
                        .property("x")
                        .build())
                .workingDir(tmp)
                .build();

        assertThat(x.execute(job).getItems())
                .containsExactly(
                        ReportItem.builder().exitStatus(VERIFIED).source(localSource, "3.0.0").target(localTarget, "1.0.2").build()
                );
    }

    private final MockedBuilder example = MockedBuilder
            .builder()
            .project(MockedProject
                    .builder()
                    .projectId("source-project")
                    .version(MockedVersion.builderOf("2.3.4").build())
                    .version(MockedVersion.builderOf("2.4.0").build())
                    .version(MockedVersion.builderOf("3.0.0").build())
                    .build())
            .project(MockedProject
                    .builder()
                    .projectId("target-project")
                    .version(MockedVersion.builderOf("1.0.0").property("x", "2.3.4").build())
                    .version(MockedVersion.builderOf("1.0.1").property("x", "2.4.0").build())
                    .version(MockedVersion.builderOf("1.0.2").property("x", "3.0.0").build())
                    .build())
            .build();
}