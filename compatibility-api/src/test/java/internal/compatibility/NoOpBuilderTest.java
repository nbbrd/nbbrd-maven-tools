package internal.compatibility;

import org.junit.jupiter.api.Test;

import static tests.compatibility.BuilderAssert.assertBuilderCompliance;

class NoOpBuilderTest {

    @Test
    void testCompliance() {
        assertBuilderCompliance(NoOpBuilder.INSTANCE);
    }
}