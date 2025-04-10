package tests.compatibility.spi;

import lombok.NonNull;
import nbbrd.compatibility.spi.Builder;
import nbbrd.compatibility.spi.BuilderLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public final class BuilderAssert {

    private BuilderAssert() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void assertBuilderCompliance(@NonNull Builder x) {
        assertThat(x.getBuilderId())
                .isNotNull()
                .matches(BuilderLoader.ID_PATTERN);

        assertThatCode(x::isBuilderAvailable)
                .doesNotThrowAnyException();
    }
}
