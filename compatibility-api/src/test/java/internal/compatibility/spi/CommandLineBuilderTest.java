package internal.compatibility.spi;

import nbbrd.compatibility.spi.Build;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static tests.compatibility.spi.BuilderAssert.assertBuilderCompliance;

class CommandLineBuilderTest {

    @Test
    void testCompliance() {
        assertBuilderCompliance(new CommandLineBuilder());
    }

    @Test
    void testBuild(@TempDir Path tmp) throws IOException {
        CommandLineBuilder x = new CommandLineBuilder();

        try (Build build = x.getBuild()) {
        }
    }
}