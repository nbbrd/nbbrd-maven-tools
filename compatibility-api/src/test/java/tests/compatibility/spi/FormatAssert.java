package tests.compatibility.spi;

import lombok.NonNull;
import nbbrd.compatibility.Formatter;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.Report;
import nbbrd.compatibility.spi.Format;
import nbbrd.compatibility.spi.FormatLoader;
import nbbrd.io.text.TextFormatter;
import nbbrd.io.text.TextParser;
import org.assertj.core.util.Files;

import java.io.IOException;
import java.io.StringWriter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;
import static tests.compatibility.Examples.resolveResource;

public final class FormatAssert {

    private FormatAssert() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @SuppressWarnings("DataFlowIssue")
    public static void assertFormatCompliance(@NonNull Format x) {
        assertThat(x.getFormatId())
                .isNotNull()
                .matches(FormatLoader.ID_PATTERN);

        assertThat(x.getFormatName())
                .isNotNull()
                .isNotBlank();

        assertThatNullPointerException().isThrownBy(() -> x.getFormatter(null));

        Job job = Job.EMPTY;
        if (x.canFormat(Job.class)) {
            Formatter<Job> formatter = x.getFormatter(Job.class);
            assertThatNullPointerException().isThrownBy(() -> formatter.format(job, null));
            assertThatNullPointerException().isThrownBy(() -> formatter.format(null, new StringWriter()));
            assertThatCode(() -> formatter.format(job, new StringWriter())).doesNotThrowAnyException();
        } else {
            assertThatIllegalArgumentException().isThrownBy(() -> x.getFormatter(Job.class));
        }

        Report report = Report.EMPTY;
        if (x.canFormat(Report.class)) {
            Formatter<Report> formatter = x.getFormatter(Report.class);
            assertThatNullPointerException().isThrownBy(() -> formatter.format(report, null));
            assertThatNullPointerException().isThrownBy(() -> formatter.format(null, new StringWriter()));
            assertThatCode(() -> formatter.format(report, new StringWriter())).doesNotThrowAnyException();
        } else {
            assertThatIllegalArgumentException().isThrownBy(() -> x.getFormatter(Report.class));
        }

        assertThat(x.getFormatFileFilter()).isNotNull();
        assertThatNullPointerException().isThrownBy(() -> x.getFormatFileFilter().accept(null));
    }

    public static String getContentOf(Class<?> anchor, String name) throws IOException {
        return Files.contentOf(resolveResource(anchor, name).toFile(), UTF_8);
    }

    public static <T> TextFormatter<T> asTextFormatter(Format format, Class<T> type) {
        return TextFormatter.onFormattingWriter(format.getFormatter(type)::format);
    }

    public static <T> TextParser<T> asTextParser(Format format, Class<T> type) {
        return TextParser.onParsingReader(format.getParser(type)::parse);
    }
}
