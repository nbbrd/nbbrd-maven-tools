package tests.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.spi.JobEngine;
import nbbrd.compatibility.spi.JobEngineLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public final class JobEngineAssert {

    private JobEngineAssert() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void assertEngineCompliance(@NonNull JobEngine x) {
        assertThat(x.getJobEngineId())
                .isNotNull()
                .matches(JobEngineLoader.ID_PATTERN);

        assertThatCode(x::isJobEngineAvailable)
                .doesNotThrowAnyException();
    }
}
