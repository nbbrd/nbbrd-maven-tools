package internal.compatibility.spi;

import nbbrd.compatibility.Job;
import nbbrd.compatibility.Report;
import nbbrd.compatibility.ReportItem;
import nbbrd.io.text.Formatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static nbbrd.compatibility.ExitStatus.*;
import static nbbrd.compatibility.RefVersion.localOf;
import static nbbrd.compatibility.RefVersion.remoteOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static tests.compatibility.spi.FormatAssert.*;

class MarkdownFormatTest {

    @Test
    void testCompliance() {
        assertFormatCompliance(new MarkdownFormat());
    }

    @Test
    void testFormatJob() {
        assertThat(new MarkdownFormat().canFormat(Job.class)).isFalse();
    }

    @Test
    void testFormatReport() throws IOException {
        MarkdownFormat x = new MarkdownFormat();

        Formatter<Report> formatter = asTextFormatter(x, Report.class).asFormatter();

        assertThat(Report.EMPTY)
                .extracting(formatter::format, STRING)
                .isEqualToNormalizingNewlines("");

        URI src = URI.create("src");
        URI trg = URI.create("trg");
        Report value = Report
                .builder()
                .item(ReportItem.builder().exitStatus(VERIFIED).source(src, remoteOf("2.3.4")).target(trg, remoteOf("1.0.0")).build())
                .item(ReportItem.builder().exitStatus(SKIPPED).source(src, remoteOf("2.3.4")).target(trg, remoteOf("1.0.1")).build())
                .item(ReportItem.builder().exitStatus(SKIPPED).source(src, remoteOf("2.3.4")).target(trg, remoteOf("1.0.2")).build())
                .item(ReportItem.builder().exitStatus(VERIFIED).source(src, remoteOf("2.4.0")).target(trg, remoteOf("1.0.0")).build())
                .item(ReportItem.builder().exitStatus(VERIFIED).source(src, remoteOf("2.4.0")).target(trg, remoteOf("1.0.1")).build())
                .item(ReportItem.builder().exitStatus(SKIPPED).source(src, remoteOf("2.4.0")).target(trg, remoteOf("1.0.2")).build())
                .item(ReportItem.builder().exitStatus(BROKEN).source(src, remoteOf("3.0.0")).target(trg, remoteOf("1.0.0")).build())
                .item(ReportItem.builder().exitStatus(BROKEN).source(src, remoteOf("3.0.0")).target(trg, remoteOf("1.0.1")).build())
                .item(ReportItem.builder().exitStatus(VERIFIED).source(src, remoteOf("3.0.0")).target(trg, remoteOf("1.0.2")).build())
                .build();

        assertThat(value)
                .extracting(formatter::format, STRING)
                .isEqualToNormalizingNewlines(getContentOf(MarkdownFormatTest.class, "report.md"));
    }

    @Test
    void testGetFormatFileFilter(@TempDir Path tmp) throws IOException {
        DirectoryStream.Filter<? super Path> x = new MarkdownFormat().getFormatFileFilter();

        assertThat(x.accept(tmp.resolve("a.xml"))).isFalse();
        assertThat(x.accept(tmp.resolve("b.md"))).isTrue();
        assertThat(x.accept(tmp.resolve("c.MD"))).isTrue();

        assertThat(x.accept(Files.createTempFile(tmp, "a", ".xml"))).isFalse();
        assertThat(x.accept(Files.createTempFile(tmp, "b", ".md"))).isTrue();
        assertThat(x.accept(Files.createTempFile(tmp, "c", ".MD"))).isTrue();

        assertThat(x.accept(Files.createDirectory(tmp.resolve("a.xml")))).isFalse();
        assertThat(x.accept(Files.createDirectory(tmp.resolve("b.md")))).isFalse();
        assertThat(x.accept(Files.createDirectory(tmp.resolve("c.MD")))).isFalse();
    }

    @Test
    void testToProjectLabel() {
        assertThat(new MarkdownFormat.Header(URI.create(""), localOf("1.2.3")).toProjectLabel())
                .isEqualTo("");

        assertThat(new MarkdownFormat.Header(URI.create("https://github.com/nbbrd/jdplus-sdmx"), localOf("1.2.3")).toProjectLabel())
                .isEqualTo("jdplus-sdmx");

        assertThat(new MarkdownFormat.Header(URI.create(""), remoteOf("1.2.3")).toProjectLabel())
                .isEqualTo("");

        assertThat(new MarkdownFormat.Header(URI.create("https://github.com/nbbrd/jdplus-sdmx"), remoteOf("1.2.3")).toProjectLabel())
                .isEqualTo("jdplus-sdmx");
    }

    @Test
    void testToVersionLabel() {
        assertThat(new MarkdownFormat.Header(URI.create(""), localOf("1.2.3")).toVersionLabel())
                .isEqualTo("HEAD");

        assertThat(new MarkdownFormat.Header(URI.create("https://github.com/nbbrd/jdplus-sdmx"), localOf("1.2.3")).toVersionLabel())
                .isEqualTo("HEAD");

        assertThat(new MarkdownFormat.Header(URI.create(""), remoteOf("1.2.3")).toVersionLabel())
                .isEqualTo("v1.2.3");

        assertThat(new MarkdownFormat.Header(URI.create("https://github.com/nbbrd/jdplus-sdmx"), remoteOf("1.2.3")).toVersionLabel())
                .isEqualTo("v1.2.3");
    }
}