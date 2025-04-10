package internal.compatibility.spi;

import nbbrd.compatibility.Report;
import nbbrd.compatibility.ReportItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import static internal.compatibility.spi.MarkdownFormat.Header.getShortNameIndex;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static nbbrd.compatibility.ExitStatus.*;
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
        assertThat(new MarkdownFormat().canFormatJob()).isFalse();
    }

    @Test
    void testFormatReport() throws IOException {
        MarkdownFormat x = new MarkdownFormat();

        assertThat(Report.EMPTY)
                .extracting(withFormatReport(x), STRING)
                .isEqualTo("");

        URI src = URI.create("src");
        URI trg = URI.create("trg");
        Report value = Report
                .builder()
                .item(ReportItem.builder().exitStatus(VERIFIED).source(src, "2.3.4").target(trg, "1.0.0").build())
                .item(ReportItem.builder().exitStatus(SKIPPED).source(src, "2.3.4").target(trg, "1.0.1").build())
                .item(ReportItem.builder().exitStatus(SKIPPED).source(src, "2.3.4").target(trg, "1.0.2").build())
                .item(ReportItem.builder().exitStatus(VERIFIED).source(src, "2.4.0").target(trg, "1.0.0").build())
                .item(ReportItem.builder().exitStatus(VERIFIED).source(src, "2.4.0").target(trg, "1.0.1").build())
                .item(ReportItem.builder().exitStatus(SKIPPED).source(src, "2.4.0").target(trg, "1.0.2").build())
                .item(ReportItem.builder().exitStatus(BROKEN).source(src, "3.0.0").target(trg, "1.0.0").build())
                .item(ReportItem.builder().exitStatus(BROKEN).source(src, "3.0.0").target(trg, "1.0.1").build())
                .item(ReportItem.builder().exitStatus(VERIFIED).source(src, "3.0.0").target(trg, "1.0.2").build())
                .build();

        assertThat(formatToString(x, value))
                .isEqualToNormalizingNewlines("\n" +
                        "|     |            | v2.3.4 | v2.4.0 | v3.0.0 |\n" +
                        "| --- | ---------- | ------ | ------ | ------ |\n" +
                        "| trg | v1.0.0     | ✅      | ✅      | ❌      |\n" +
                        "|     | v1.0.1     |        | ✅      | ❌      |\n" +
                        "|     | **v1.0.2** |        |        | ✅      |");
    }

    @Test
    void testGetFormatFileFilter() throws IOException {
        DirectoryStream.Filter<? super Path> x = new MarkdownFormat().getFormatFileFilter();

        assertThat(x.accept(Paths.get("hello.xml"))).isFalse();
        assertThat(x.accept(Paths.get("hello.md"))).isTrue();
        assertThat(x.accept(Paths.get("hello.MD"))).isTrue();
    }

    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    @Test
    void testGetShortNameIndex() {
        assertThat(getShortNameIndex(emptyList()))
                .isEqualTo(0);

        assertThat(getShortNameIndex(asList(
                new MarkdownFormat.Header(URI.create(""), null)
        ))).isEqualTo(0);

        assertThat(getShortNameIndex(asList(
                new MarkdownFormat.Header(URI.create("https://github.com/nbbrd/jdplus-sdmx"), null)
        ))).isEqualTo(0);

        assertThat(getShortNameIndex(asList(
                new MarkdownFormat.Header(URI.create("https://github.com/nbbrd/jdplus-sdmx"), null),
                new MarkdownFormat.Header(URI.create("https://github.com/nbbrd/jdplus-sdmx"), null)
        ))).isEqualTo(0);

        assertThat(getShortNameIndex(asList(
                new MarkdownFormat.Header(URI.create("https://github.com/jdemetra/jdplus-benchmarking"), null),
                new MarkdownFormat.Header(URI.create("https://github.com/nbbrd/jdplus-sdmx"), null)
        ))).isEqualTo(19);

        assertThat(getShortNameIndex(asList(
                new MarkdownFormat.Header(URI.create("https://github.com/jdemetra/jdplus-benchmarking"), null),
                new MarkdownFormat.Header(URI.create("https://github.com/jdemetra/jdplus-incubator"), null)
        ))).isEqualTo(35);
    }
}