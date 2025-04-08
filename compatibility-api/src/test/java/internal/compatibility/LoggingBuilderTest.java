package internal.compatibility;

import org.junit.jupiter.api.Test;

import static tests.compatibility.BuilderAssert.assertBuilderCompliance;

class LoggingBuilderTest {

    @Test
    void testCompliance() {
        assertBuilderCompliance(new LoggingBuilder(LoggingBuilderTest::ignore, NoOpBuilder.INSTANCE));
    }

    private static void ignore(Object ignore) {
    }
}