package tests.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.Report;
import nbbrd.compatibility.spi.Format;
import nbbrd.compatibility.spi.FormatLoader;
import nbbrd.io.function.IOFunction;

import java.io.IOException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;

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

        Job job = Job.builder().build();
        if (x.canFormatJob()) {
            assertThatNullPointerException().isThrownBy(() -> x.formatJob(null, job));
            assertThatNullPointerException().isThrownBy(() -> x.formatJob(new StringBuilder(), null));
            assertThatCode(() -> x.formatJob(new StringBuilder(), job)).doesNotThrowAnyException();
        } else {
            assertThatIOException().isThrownBy(() -> x.formatJob(new StringBuilder(), job));
        }

        Report report = Report.builder().build();
        if (x.canFormatReport()) {
            assertThatNullPointerException().isThrownBy(() -> x.formatReport(null, report));
            assertThatNullPointerException().isThrownBy(() -> x.formatReport(new StringBuilder(), null));
            assertThatCode(() -> x.formatReport(new StringBuilder(), report)).doesNotThrowAnyException();
        } else {
            assertThatIOException().isThrownBy(() -> x.formatReport(new StringBuilder(), report));
        }

        assertThat(x.getFormatFileFilter()).isNotNull();
        assertThatNullPointerException().isThrownBy(() -> x.getFormatFileFilter().accept(null));
    }

    public static String formatToString(Format format, Job value) throws IOException {
        StringBuilder sb = new StringBuilder();
        format.formatJob(sb, value);
        return sb.toString();
    }

    public static String formatToString(Format format, Report value) throws IOException {
        StringBuilder sb = new StringBuilder();
        format.formatReport(sb, value);
        return sb.toString();
    }

    public static Function<Job, String> withFormatJob(Format x) {
        return IOFunction.unchecked(value -> formatToString(x, value));
    }

    public static Function<Report, String> withFormatReport(Format x) {
        return IOFunction.unchecked(value -> formatToString(x, value));
    }
}
