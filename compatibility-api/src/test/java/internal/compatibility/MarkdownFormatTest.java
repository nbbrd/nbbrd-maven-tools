package internal.compatibility;

import nbbrd.compatibility.Report;
import nbbrd.compatibility.ReportItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import static nbbrd.compatibility.ExitStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static tests.compatibility.FormatAssert.*;

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

        assertThat(Report.builder().build())
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
}