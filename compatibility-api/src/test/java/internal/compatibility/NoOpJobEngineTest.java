package internal.compatibility;

import org.junit.jupiter.api.Test;

import static tests.compatibility.JobEngineAssert.assertEngineCompliance;

class NoOpJobEngineTest {

    @Test
    void testCompliance() {
        assertEngineCompliance(NoOpJobEngine.INSTANCE);
    }
}