package nbbrd.buf.maven.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import internal.buf.Problem;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LintMojoTest {

    @Test
    void testToInput(@TempDir Path dir) {
        LintMojo mojo = new LintMojo();
        mojo.projectDirectory = dir.toFile();

        mojo.input = dir.resolve("src/main/proto").toFile();
        assertThat(mojo.toInput()).isEqualTo("src" + File.separator + "main" + File.separator + "proto");

        mojo.input = dir.toFile();
        assertThat(mojo.toInput()).isEqualTo(".");

        Path outside = dir.resolveSibling("other").toAbsolutePath();
        mojo.input = outside.toFile();
        assertThat(mojo.toInput()).isEqualTo(outside.toString());
    }

    @Test
    void testToWorkflowCommand(@TempDir Path dir) {
        LintMojo mojo = new LintMojo();
        mojo.projectDirectory = dir.resolve("module").toFile();
        mojo.githubWorkspace = dir.toFile();
        Problem problem = Problem.builder()
                .file("src/main/proto/foo.proto")
                .line(3)
                .column(5)
                .rule("FIELD_LOWER_SNAKE_CASE")
                .message("50% wrong,\nreally")
                .build();
        assertThat(mojo.toWorkflowCommand("error", problem))
                .isEqualTo("::error file=module/src/main/proto/foo.proto,line=3,col=5,"
                        + "title=buf [FIELD_LOWER_SNAKE_CASE]::50%25 wrong,%0Areally");
    }

    @Test
    void testInstall(@TempDir Path dir) throws Exception {
        Path artifact = dir.resolve("repository/buf-1.75.0-linux-x86_64.exe");
        Files.createDirectories(artifact.getParent());
        Files.write(artifact, "v1".getBytes(StandardCharsets.UTF_8));
        Path target = dir.resolve("target/buf/buf-1.75.0");

        assertThat(LintMojo.install(artifact, target))
                .isEqualTo(target)
                .isExecutable()
                .hasContent("v1");

        Files.write(artifact, "v22".getBytes(StandardCharsets.UTF_8));
        assertThat(LintMojo.install(artifact, target)).hasContent("v22");
        assertThat(target.getParent()).isDirectoryNotContaining("glob:**.tmp");
    }
}
