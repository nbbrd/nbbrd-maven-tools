package internal.compatibility.spi;

import org.junit.jupiter.api.Test;

import static tests.compatibility.spi.BuilderAssert.assertBuilderCompliance;

class NoOpBuilderTest {

    @Test
    void testCompliance() {
        assertBuilderCompliance(NoOpBuilder.INSTANCE);
    }
}