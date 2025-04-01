package tests.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.spi.Format;
import nbbrd.compatibility.spi.FormatLoader;

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

        assertThat(x.getFormatId())
                .isNotNull()
                .isNotBlank();

        assertThatNullPointerException().isThrownBy(() -> x.formatJob(null, Job.builder().build()));
        assertThatNullPointerException().isThrownBy(() -> x.formatJob(new StringBuilder(), null));

        assertThatCode(() -> x.formatJob(new StringBuilder(), Job.builder().build()))
                .doesNotThrowAnyException();
    }
}
