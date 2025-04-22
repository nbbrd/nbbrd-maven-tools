package nbbrd.compatibility;

import internal.compatibility.spi.NoOpBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tests.compatibility.MockedBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static nbbrd.compatibility.ExitStatus.*;
import static nbbrd.compatibility.RefVersion.localOf;
import static nbbrd.compatibility.RefVersion.remoteOf;
import static org.assertj.core.api.Assertions.*;
import static tests.compatibility.MockedBuilder.localURI;
import static tests.compatibility.MockedBuilder.remoteURI;

class CompatibilityTest {

    @Test
    void checkNoBuilder(@TempDir Path tmp) {
        Compatibility x = noOpCompatibility(tmp);

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
                .build();

        assertThatIOException()
                .isThrownBy(() -> x.check(job))
                .withMessage("No operation");

        assertThat(tmp).isEmptyDirectory();
    }

    @Test
    void checkEmptyJob(@TempDir Path tmp) throws IOException {
        Compatibility x = noOpCompatibility(tmp);

        assertThat(x.check(Job.EMPTY)).isEqualTo(Report.EMPTY);
        assertThat(tmp).isEmptyDirectory();
    }

    @Test
    void checkDownstream(@TempDir Path tmp) throws IOException {
        Compatibility x = mockedCompatibility(tmp);

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
                .build();

        assertThat(x.check(job).getItems())
                .containsExactly(
                        ReportItem.builder().exitStatus(BROKEN).source(localSource, localOf("3.0.0")).target(remoteTarget, remoteOf("1.0.0")).build(),
                        ReportItem.builder().exitStatus(BROKEN).source(localSource, localOf("3.0.0")).target(remoteTarget, remoteOf("1.0.1")).build(),
                        ReportItem.builder().exitStatus(VERIFIED).source(localSource, localOf("3.0.0")).target(remoteTarget, remoteOf("1.0.2")).build()
                );

        assertThat(tmp).isEmptyDirectory();
    }

    @Test
    void checkUpstream(@TempDir Path tmp) throws IOException {
        Compatibility x = mockedCompatibility(tmp);

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
                .build();

        assertThat(x.check(job).getItems())
                .containsExactly(
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, remoteOf("2.3.4")).target(localTarget, localOf("1.0.2")).build(),
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, remoteOf("2.4.0")).target(localTarget, localOf("1.0.2")).build(),
                        ReportItem.builder().exitStatus(VERIFIED).source(remoteSource, remoteOf("3.0.0")).target(localTarget, localOf("1.0.2")).build()
                );

        assertThat(tmp).isEmptyDirectory();
    }

    @Test
    void checkRemoteStreams(@TempDir Path tmp) throws IOException {
        Compatibility x = mockedCompatibility(tmp);

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
                .build();

        assertThat(x.check(job).getItems())
                .containsExactly(
                        ReportItem.builder().exitStatus(VERIFIED).source(remoteSource, remoteOf("2.3.4")).target(remoteTarget, remoteOf("1.0.0")).build(),
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, remoteOf("2.3.4")).target(remoteTarget, remoteOf("1.0.1")).build(),
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, remoteOf("2.3.4")).target(remoteTarget, remoteOf("1.0.2")).build(),
                        ReportItem.builder().exitStatus(VERIFIED).source(remoteSource, remoteOf("2.4.0")).target(remoteTarget, remoteOf("1.0.0")).build(),
                        ReportItem.builder().exitStatus(VERIFIED).source(remoteSource, remoteOf("2.4.0")).target(remoteTarget, remoteOf("1.0.1")).build(),
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, remoteOf("2.4.0")).target(remoteTarget, remoteOf("1.0.2")).build(),
                        ReportItem.builder().exitStatus(BROKEN).source(remoteSource, remoteOf("3.0.0")).target(remoteTarget, remoteOf("1.0.0")).build(),
                        ReportItem.builder().exitStatus(BROKEN).source(remoteSource, remoteOf("3.0.0")).target(remoteTarget, remoteOf("1.0.1")).build(),
                        ReportItem.builder().exitStatus(VERIFIED).source(remoteSource, remoteOf("3.0.0")).target(remoteTarget, remoteOf("1.0.2")).build()
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
                .build();

        assertThat(x.check(jobWithFilter).getItems())
                .containsExactly(
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, remoteOf("2.3.4")).target(remoteTarget, remoteOf("1.0.2")).build(),
                        ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, remoteOf("2.4.0")).target(remoteTarget, remoteOf("1.0.2")).build()
                );

        assertThat(tmp).isEmptyDirectory();
    }

    @Test
    void checkLocalStreams(@TempDir Path tmp) throws IOException {
        Compatibility x = mockedCompatibility(tmp);

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
                .build();

        assertThat(x.check(job).getItems())
                .containsExactly(
                        ReportItem.builder().exitStatus(VERIFIED).source(localSource, localOf("3.0.0")).target(localTarget, localOf("1.0.2")).build()
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

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGetParserById() {
        Compatibility x = Compatibility.ofServiceLoader();

        assertThatNullPointerException().isThrownBy(() -> x.getParserById(null, "json"));
        assertThatNullPointerException().isThrownBy(() -> x.getParserById(Job.class, null));

        assertThat(x.getParserById(Job.class, "json")).isNotEmpty();
        assertThat(x.getParserById(Job.class, "stuff")).isEmpty();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGetParserByFile(@TempDir Path tmp) {
        Compatibility x = Compatibility.ofServiceLoader();

        Path json = tmp.resolve("hello.json");
        Path stuff = tmp.resolve("hello.stuff");

        assertThatNullPointerException().isThrownBy(() -> x.getParserByFile(null, json));
        assertThatNullPointerException().isThrownBy(() -> x.getParserByFile(Job.class, null));

        assertThat(x.getParserByFile(Job.class, json)).isNotEmpty();
        assertThat(x.getParserByFile(Job.class, stuff)).isEmpty();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testGetParserFilter(@TempDir Path tmp) throws IOException {
        Compatibility x = Compatibility.ofServiceLoader();

        Path json = tmp.resolve("hello.json");
        Path stuff = tmp.resolve("hello.stuff");

        assertThatNullPointerException().isThrownBy(() -> x.getParserFilter(null));

        assertThat(x.getParserFilter(Report.class).accept(json)).isTrue();
        assertThat(x.getParserFilter(Report.class).accept(stuff)).isFalse();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testMergeReports() {
        Compatibility x = Compatibility.ofServiceLoader();

        assertThatNullPointerException().isThrownBy(() -> x.mergeReports(null));

        assertThat(x.mergeReports(emptyList())).isEqualTo(Report.EMPTY);

        URI remoteSource = remoteURI("source-project");
        URI remoteTarget = remoteURI("target-project");
        ReportItem r1 = ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, remoteOf("2.3.4")).target(remoteTarget, remoteOf("1.0.2")).build();
        ReportItem r2 = ReportItem.builder().exitStatus(SKIPPED).source(remoteSource, remoteOf("2.4.0")).target(remoteTarget, remoteOf("1.0.2")).build();

        assertThat(x.mergeReports(asList(
                Report.builder().item(r1).build(),
                Report.builder().item(r2).build()))
        ).isEqualTo(Report.builder().item(r1).item(r2).build());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testSplitJob() {
        Compatibility x = Compatibility.ofServiceLoader();

        assertThatNullPointerException().isThrownBy(() -> x.splitJob(null));

        assertThat(x.splitJob(Job.EMPTY)).isEmpty();

        Source s1 = Source.builder().uri(remoteURI("source1")).build();
        Source s2 = Source.builder().uri(remoteURI("source2")).build();
        Target t1 = Target.builder().uri(remoteURI("target1")).build();
        Target t2 = Target.builder().uri(remoteURI("target2")).build();

        assertThat(x.splitJob(Job.builder().source(s1).source(s2).target(t1).target(t2).build())
        ).containsExactly(
                Job.builder().source(s1).source(s2).target(t1).build(),
                Job.builder().source(s1).source(s2).target(t2).build()
        );
    }

    private Compatibility noOpCompatibility(Path workingDir) {
        return Compatibility.ofServiceLoader().toBuilder().workingDir(workingDir).builder(NoOpBuilder.INSTANCE).build();
    }

    private Compatibility mockedCompatibility(Path workingDir) {
        return Compatibility.ofServiceLoader().toBuilder().workingDir(workingDir).builder(MockedBuilder.EXAMPLE).build();
    }
}