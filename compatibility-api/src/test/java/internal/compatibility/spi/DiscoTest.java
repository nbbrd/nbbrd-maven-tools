package internal.compatibility.spi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoTest {

    public static void main(String[] args) throws IOException {
        Path workingDir = Files.createTempDirectory("jdk");
        Path unpackedJdk = Disco.setup(workingDir, "17", msg -> System.out.println("[EVENT] " + msg));
        System.out.println("Unpacked JDK to " + unpackedJdk);
    }

    @Test
    void testSetup(@TempDir Path tmp) throws IOException {
        assertThat(Disco.setup(tmp, "21", DiscoTest::ignore))
                .exists()
                .isDirectory()
                .isNotEmptyDirectory();
    }

    private static void ignore(Object ignore) {
    }
}