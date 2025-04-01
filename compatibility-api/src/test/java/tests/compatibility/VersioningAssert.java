package tests.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.spi.Versioning;
import nbbrd.compatibility.spi.VersioningLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public final class VersioningAssert {

    private VersioningAssert() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @SuppressWarnings("DataFlowIssue")
    public static void assertVersioningCompliance(@NonNull Versioning x) {
        assertThat(x.getVersioningId())
                .isNotNull()
                .matches(VersioningLoader.ID_PATTERN);

        assertThat(x.getVersioningName())
                .isNotNull()
                .isNotBlank();

        assertThatNullPointerException().isThrownBy(() -> x.isValidVersion(null));

        assertThatNullPointerException().isThrownBy(() -> x.isOrdered(null, ""));
        assertThatNullPointerException().isThrownBy(() -> x.isOrdered("", null));
    }
}
