package internal.compatibility;

import nbbrd.compatibility.Building;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.Source;
import nbbrd.compatibility.Target;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;

import static tests.compatibility.FormatAssert.assertFormatCompliance;

class JsonFormatTest {

    @Test
    void testCompliance() {
        assertFormatCompliance(new JsonFormat());
    }

    @Test
    void testFormatJob() throws IOException {
        JsonFormat x = new JsonFormat();

        StringBuilder sb = new StringBuilder();

        Job job = Job
                .builder()
                .source(Source.builder().uri(URI.create("hello:source")).build())
                .target(Target
                        .builder()
                        .uri(URI.create("hello:target"))
                        .building(Building.builder().property("x").build())
                        .build())
                .workingDir(Paths.get("folder"))
                .build();

        x.formatJob(sb, job);

        Assertions.assertThat(sb.toString())
                .isEqualTo("{\n" +
                        "  \"sources\": [\n" +
                        "    {\n" +
                        "      \"uri\": \"hello:source\",\n" +
                        "      \"tagging\": {\n" +
                        "        \"versioning\": \"\",\n" +
                        "        \"prefix\": \"\"\n" +
                        "      }\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"targets\": [\n" +
                        "    {\n" +
                        "      \"uri\": \"hello:target\",\n" +
                        "      \"tagging\": {\n" +
                        "        \"versioning\": \"\",\n" +
                        "        \"prefix\": \"\"\n" +
                        "      },\n" +
                        "      \"building\": {\n" +
                        "        \"property\": \"x\",\n" +
                        "        \"parameters\": \"\",\n" +
                        "        \"jdk\": \"\"\n" +
                        "      }\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"workingDir\": \"folder\"\n" +
                        "}");
    }
}