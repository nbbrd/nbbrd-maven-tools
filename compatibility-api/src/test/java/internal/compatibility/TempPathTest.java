package internal.compatibility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TempPathTest {

    @Test
    void test(@TempDir Path tmp) throws IOException {
        Path tempFile = Files.createTempFile(tmp, "", "");
        assertThat(tempFile).exists().isRegularFile();
        try (TempPath x = TempPath.of(tempFile)) {
            assertThat(x.getPath())
                    .isRegularFile()
                    .isEqualByComparingTo(tempFile)
                    .hasToString(tempFile.toString());
        }
        assertThat(tempFile).doesNotExist();

        Path tempDir = Files.createTempDirectory(tmp, "");
        assertThat(tempDir).exists().isDirectory();
        try (TempPath x = TempPath.of(tempDir)) {
            Files.createTempFile(x.getPath(), "hello", ".txt");
            assertThat(x.getPath())
                    .isNotEmptyDirectory()
                    .isEqualByComparingTo(tempDir)
                    .hasToString(tempDir.toString());
        }
        assertThat(tempDir).doesNotExist();
    }
}