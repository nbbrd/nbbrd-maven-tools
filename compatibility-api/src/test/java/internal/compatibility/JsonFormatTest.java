package internal.compatibility;

import nbbrd.compatibility.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static tests.compatibility.FormatAssert.*;

class JsonFormatTest {

    @Test
    void testCompliance() {
        assertFormatCompliance(new JsonFormat());
    }

    @Test
    void testFormatJob() throws IOException {
        JsonFormat x = new JsonFormat();

        assertThat(Job.builder().workingDir(Paths.get("hello")).build())
                .extracting(withFormatJob(x), STRING)
                .isEqualTo("{\n" +
                        "  \"sources\": [],\n" +
                        "  \"targets\": [],\n" +
                        "  \"workingDir\": \"hello\"\n" +
                        "}");

        Job value = Job
                .builder()
                .source(Source.builder().uri(URI.create("hello:source")).build())
                .target(Target
                        .builder()
                        .uri(URI.create("hello:target"))
                        .property("x")
                        .build())
                .workingDir(Paths.get("folder"))
                .build();

        assertThat(formatToString(x, value))
                .isEqualTo("{\n" +
                        "  \"sources\": [\n" +
                        "    {\n" +
                        "      \"uri\": \"hello:source\",\n" +
                        "      \"versioning\": \"\"\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"targets\": [\n" +
                        "    {\n" +
                        "      \"uri\": \"hello:target\",\n" +
                        "      \"property\": \"x\",\n" +
                        "      \"building\": {\n" +
                        "        \"parameters\": \"\",\n" +
                        "        \"jdk\": \"\"\n" +
                        "      }\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"workingDir\": \"folder\"\n" +
                        "}");
    }

    @Test
    void testFormatReport() throws IOException {
        JsonFormat x = new JsonFormat();

        assertThat(Report.builder().build())
                .extracting(withFormatReport(x), STRING)
                .isEqualTo("{\n" +
                        "  \"items\": []\n" +
                        "}");

        Report value = Report
                .builder()
                .item(ReportItem
                        .builder()
                        .exitStatus(ExitStatus.VERIFIED)
                        .source(URI.create("source"), "1.2.3")
                        .target(URI.create("target"), "3.2.1")
                        .build())
                .build();

        assertThat(formatToString(x, value))
                .isEqualTo("{\n" +
                        "  \"items\": [\n" +
                        "    {\n" +
                        "      \"exitStatus\": \"VERIFIED\",\n" +
                        "      \"sourceUri\": \"source\",\n" +
                        "      \"sourceVersion\": \"1.2.3\",\n" +
                        "      \"targetUri\": \"target\",\n" +
                        "      \"targetVersion\": \"3.2.1\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}");
    }

    @Test
    void testGetFormatFileFilter() throws IOException {
        DirectoryStream.Filter<? super Path> x = new JsonFormat().getFormatFileFilter();

        assertThat(x.accept(Paths.get("hello.xml"))).isFalse();
        assertThat(x.accept(Paths.get("hello.json"))).isTrue();
        assertThat(x.accept(Paths.get("hello.JSON"))).isTrue();
    }
}