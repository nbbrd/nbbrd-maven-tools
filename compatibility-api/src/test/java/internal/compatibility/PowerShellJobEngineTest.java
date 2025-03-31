package internal.compatibility;

import nbbrd.compatibility.spi.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static tests.compatibility.JobEngineAssert.assertEngineCompliance;

class PowerShellJobEngineTest {

    @Test
    void testCompliance() {
        assertEngineCompliance(new PowerShellJobEngine());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testExecute(@TempDir Path tmp) throws IOException {
        PowerShellJobEngine x = new PowerShellJobEngine();

        try (JobExecutor executor = x.getExecutor()) {
        }
    }
}