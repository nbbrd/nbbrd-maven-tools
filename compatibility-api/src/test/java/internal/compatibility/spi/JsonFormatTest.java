package internal.compatibility.spi;

import nbbrd.compatibility.*;
import nbbrd.io.text.Formatter;
import nbbrd.io.text.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static nbbrd.compatibility.RefVersion.remoteOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static tests.compatibility.spi.FormatAssert.*;

class JsonFormatTest {

    @Test
    void testCompliance() {
        assertFormatCompliance(new JsonFormat());
    }

    @Test
    void testFormatJob() throws IOException {
        JsonFormat x = new JsonFormat();

        Formatter<Job> formatter = asTextFormatter(x, Job.class).asFormatter();
        Parser<Job> parser = asTextParser(x, Job.class).asParser();

        Job emptyJob = Job.EMPTY;
        String emptyJobText = getContentOf(JsonFormatTest.class, "empty_job.json");

        assertThat(emptyJob)
                .extracting(formatter::format, STRING)
                .isEqualToNormalizingNewlines(emptyJobText);

        assertThatObject(emptyJobText)
                .extracting(parser::parse, type(Job.class))
                .isEqualTo(emptyJob);

        Job value = Job
                .builder()
                .source(Source.builder().uri(URI.create("hello:source")).binding("x").build())
                .target(Target
                        .builder()
                        .uri(URI.create("hello:target"))
                        .build())
                .build();
        String valueText = getContentOf(JsonFormatTest.class, "job.json");

        assertThat(value)
                .extracting(formatter::format, STRING)
                .isEqualToNormalizingNewlines(valueText);

        assertThatObject(valueText)
                .extracting(parser::parse, type(Job.class))
                .isEqualTo(value);
    }

    @Test
    void testFormatReport() throws IOException {
        JsonFormat x = new JsonFormat();

        Formatter<Report> formatter = asTextFormatter(x, Report.class).asFormatter();
        Parser<Report> parser = asTextParser(x, Report.class).asParser();

        Report emptyReport = Report.EMPTY;
        String emptyReportText = getContentOf(JsonFormatTest.class, "empty_report.json");

        assertThat(emptyReport)
                .extracting(formatter::format, STRING)
                .isEqualToNormalizingNewlines(emptyReportText);

        assertThatObject(emptyReportText)
                .extracting(parser::parse, type(Report.class))
                .isEqualTo(emptyReport);

        Report value = Report
                .builder()
                .item(ReportItem
                        .builder()
                        .exitStatus(ExitStatus.VERIFIED)
                        .source(URI.create("source"), remoteOf("1.2.3"))
                        .target(URI.create("target"), remoteOf("3.2.1"))
                        .build())
                .build();
        String valueText = getContentOf(JsonFormatTest.class, "report.json");

        assertThat(value)
                .extracting(formatter::format, STRING)
                .isEqualToNormalizingNewlines(valueText);

        assertThatObject(valueText)
                .extracting(parser::parse, type(Report.class))
                .isEqualTo(value);
    }

    @Test
    void testGetFormatFileFilter(@TempDir Path tmp) throws IOException {
        DirectoryStream.Filter<? super Path> x = new JsonFormat().getFormatFileFilter();

        assertThat(x.accept(tmp.resolve("a.xml"))).isFalse();
        assertThat(x.accept(tmp.resolve("b.json"))).isTrue();
        assertThat(x.accept(tmp.resolve("c.JSON"))).isTrue();

        assertThat(x.accept(Files.createTempFile(tmp, "a", ".xml"))).isFalse();
        assertThat(x.accept(Files.createTempFile(tmp, "b", ".json"))).isTrue();
        assertThat(x.accept(Files.createTempFile(tmp, "c", ".JSON"))).isTrue();

        assertThat(x.accept(Files.createDirectory(tmp.resolve("a.xml")))).isFalse();
        assertThat(x.accept(Files.createDirectory(tmp.resolve("b.json")))).isFalse();
        assertThat(x.accept(Files.createDirectory(tmp.resolve("c.JSON")))).isFalse();
    }
}