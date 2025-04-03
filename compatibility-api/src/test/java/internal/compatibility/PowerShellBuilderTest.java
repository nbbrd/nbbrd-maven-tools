package internal.compatibility;

import nbbrd.compatibility.spi.Build;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static tests.compatibility.BuilderAssert.assertBuilderCompliance;

class PowerShellBuilderTest {

    @Test
    void testCompliance() {
        assertBuilderCompliance(new PowerShellBuilder());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testBuild(@TempDir Path tmp) throws IOException {
        PowerShellBuilder x = new PowerShellBuilder();

        try (Build build = x.getBuild()) {
        }
    }
}