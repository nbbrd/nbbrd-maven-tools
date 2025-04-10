package tests.compatibility.spi;

import lombok.NonNull;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Versioning;
import nbbrd.compatibility.spi.VersioningLoader;

import static org.assertj.core.api.Assertions.*;

public final class VersioningAssert {

    private VersioningAssert() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    @SuppressWarnings({"DataFlowIssue", "ResultOfMethodCallIgnored"})
    public static void assertVersioningCompliance(@NonNull Versioning x) {
        assertThat(x.getVersioningId())
                .isNotNull()
                .matches(VersioningLoader.ID_PATTERN);

        assertThat(x.getVersioningName())
                .isNotNull()
                .isNotBlank();

        assertThatNullPointerException().isThrownBy(() -> x.isValidVersion(null));

        assertThatNoException().isThrownBy(x::getVersionComparator);
        assertThatNullPointerException().isThrownBy(() -> x.getVersionComparator().compare(null, Version.NO_VERSION));
        assertThatNullPointerException().isThrownBy(() -> x.getVersionComparator().compare(Version.NO_VERSION, null));
    }
}
