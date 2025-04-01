package internal.compatibility;

import org.junit.jupiter.api.Test;

import static tests.compatibility.VersioningAssert.assertVersioningCompliance;

class NoOpVersioningTest {

    @Test
    void testCompliance() {
        assertVersioningCompliance(NoOpVersioning.INSTANCE);
    }
}