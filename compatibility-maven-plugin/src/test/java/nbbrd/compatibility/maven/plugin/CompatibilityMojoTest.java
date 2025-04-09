package nbbrd.compatibility.maven.plugin;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityMojoTest {

    @Test
    void fixUnresolvedProperties() {
        Path file = Paths.get("${project.basedir}", "${project.build.directory}", "compatibility.md");
        assertThat(CompatibilityMojo.fixUnresolvedProperties(file.toUri()).toString())
                .doesNotContain("${project.basedir}", "${project.build.directory}")
                .contains("compatibility.md");
    }
}