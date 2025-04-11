package nbbrd.compatibility;

import internal.compatibility.spi.NoOpBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tests.compatibility.MockedBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import static nbbrd.compatibility.ExitStatus.*;
import static org.assertj.core.api.Assertions.*;
import static tests.compatibility.MockedBuilder.localURI;
import static tests.compatibility.MockedBuilder.remoteURI;

class CompatibilityTest {

    @Test
    void checkNoBuilder(@TempDir Path tmp) {
        Compatibility x = noOpCompatibility();

        URI remoteSource = remoteURI("source-project");
        URI remoteTarget = remoteURI("target-project");

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

        assertThatIOException()
                .isThrownBy(() -> x.check(job))
                .withMessage("No operation");

        assertThat(tmp).isEmptyDirectory();
    }

    @Test
    void checkEmptyJob(@TempDir Path tmp) throws IOException {
        Compatibility x = noOpCompatibility();

        Job job = Job
                .builder()
                .workingDir(tmp)
                .build();

        assertThat(x.check(job)).isEqualTo(Report.EMPTY);
        assertThat(tmp).isEmptyDirectory();
    }

    @Test
    void checkDownstream(@TempDir Path tmp) throws IOException {
        Compatibility x = mockedCompatibility();

        URI localSource = localURI(tmp, "source-project");
        URI remoteTarget = remoteURI("target-project");

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

        assertThat(x.check(job).getItems())
                .containsExactly(
                        ReportItem.builder().exitStatus(BROKEN).source(localSource, "3.0.0").target(remoteTarget, "1.0.0").build(),
                        ReportItem.builder().exitStatus(BROKEN).source(localSource, "3.0.0").target(remoteTarget, "1.0.1").build(),
                        ReportItem.builder().exitStatus(VERIFIED).source(localSource, "3.0.0").target(remoteTarget, "1.0.2").build()
                );

        assertThat(tmp).isEmptyDirectory();
    }

    @Test
    void checkUpstream(@TempDir Path tmp) throws IOException {
        Compatibility x = mockedCompatibility();

        URI remoteSource = remoteURI("source-project");
        URI localTarget = localURI(tmp, "target-project");

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

        assertThat(x.check(job).getItems())
                .containsExactly(
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, "2.3.4").target(localTarget, "1.0.2").build(),
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, "2.4.0").target(localTarget, "1.0.2").build(),
                        ReportItem.builder().exitStatus(VERIFIED).source(remoteSource, "3.0.0").target(localTarget, "1.0.2").build()
                );

        assertThat(tmp).isEmptyDirectory();
    }

    @Test
    void checkRemoteStreams(@TempDir Path tmp) throws IOException {
        Compatibility x = mockedCompatibility();

        URI remoteSource = remoteURI("source-project");
        URI remoteTarget = remoteURI("target-project");

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

        assertThat(x.check(job).getItems())
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

        Job jobWithFilter = Job
                .builder()
                .source(Source
                        .builder()
                        .uri(remoteSource)
                        .versioning("semver")
                        .filter(Filter.builder().ref("2.").build())
                        .build())
                .target(Target
                        .builder()
                        .uri(remoteTarget)
                        .property("x")
                        .filter(Filter.builder().ref("1.0.2").build())
                        .build())
                .workingDir(tmp)
                .build();

        assertThat(x.check(jobWithFilter).getItems())
                .containsExactly(
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, "2.3.4").target(remoteTarget, "1.0.2").build(),
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, "2.4.0").target(remoteTarget, "1.0.2").build()
                );

        assertThat(tmp).isEmptyDirectory();
    }

    @Test
    void checkLocalStreams(@TempDir Path tmp) throws IOException {
        Compatibility x = mockedCompatibility();

        URI localSource = localURI(tmp, "source-project");
        URI localTarget = localURI(tmp, "target-project");

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

        assertThat(x.check(job).getItems())
                .containsExactly(
                        ReportItem.builder().exitStatus(VERIFIED).source(localSource, "3.0.0").target(localTarget, "1.0.2").build()
                );

        assertThat(tmp).isEmptyDirectory();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGetFormatterById() {
        Compatibility x = Compatibility.ofServiceLoader();

        assertThatNullPointerException().isThrownBy(() -> x.getFormatterById(null, "json"));
        assertThatNullPointerException().isThrownBy(() -> x.getFormatterById(Job.class, null));

        assertThat(x.getFormatterById(Job.class, "json")).isNotEmpty();
        assertThat(x.getFormatterById(Job.class, "stuff")).isEmpty();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGetFormatterByFile(@TempDir Path tmp) {
        Compatibility x = Compatibility.ofServiceLoader();

        Path json = tmp.resolve("hello.json");
        Path stuff = tmp.resolve("hello.stuff");

        assertThatNullPointerException().isThrownBy(() -> x.getFormatterByFile(null, json));
        assertThatNullPointerException().isThrownBy(() -> x.getFormatterByFile(Job.class, null));

        assertThat(x.getFormatterByFile(Job.class, json)).isNotEmpty();
        assertThat(x.getFormatterByFile(Job.class, stuff)).isEmpty();
    }

    private Compatibility noOpCompatibility() {
        return Compatibility.ofServiceLoader().toBuilder().builder(NoOpBuilder.INSTANCE).build();
    }

    private Compatibility mockedCompatibility() {
        return Compatibility.ofServiceLoader().toBuilder().builder(MockedBuilder.EXAMPLE).build();
    }
}