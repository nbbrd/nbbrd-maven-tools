package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Compatibility;
import nbbrd.compatibility.Report;
import nbbrd.compatibility.ReportItem;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static nbbrd.compatibility.ExitStatus.VERIFIED;
import static nbbrd.compatibility.maven.plugin.CompatibilityMojo.*;
import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityMojoTest {

    @Test
    void testFixUnresolvedProperties() {
        Path file = Paths.get("${project.basedir}", "${project.build.directory}", "compatibility.md");
        assertThat(fixUnresolvedProperties(file.toUri()).toString())
                .doesNotContain("${project.basedir}", "${project.build.directory}")
                .contains("compatibility.md");
    }

    @Test
    void testLoadReport() throws IOException, MojoExecutionException {
        assertThat(loadReport(Compatibility.ofServiceLoader(), resolveResource(CompatibilityMojoTest.class, "r1.json")))
                .isEqualTo(Report.builder().item(SDMX_320).item(SDMX_330).build());
    }

    @Test
    void testStoreReport(@TempDir Path tempDir) throws IOException, MojoExecutionException {
        Path json = tempDir.resolve("test.json");
        storeReport(Compatibility.ofServiceLoader(), json, Report.builder().item(SDMX_320).item(SDMX_330).build());
        assertThat(json).hasSameTextualContentAs(resolveResource(CompatibilityMojoTest.class, "r1.json"));
    }

    static final ReportItem SDMX_320 = ReportItem.builder().exitStatus(VERIFIED).source(URI.create("https://github.com/jdemetra/jdplus-main"), "3.4.0").target(URI.create("https://github.com/nbbrd/jdplus-sdmx"), "3.2.0").build();
    static final ReportItem SDMX_330 = ReportItem.builder().exitStatus(VERIFIED).source(URI.create("https://github.com/jdemetra/jdplus-main"), "3.4.0").target(URI.create("https://github.com/nbbrd/jdplus-sdmx"), "3.3.0").build();
    static final ReportItem NOWCASTING_102 = ReportItem.builder().exitStatus(VERIFIED).source(URI.create("https://github.com/jdemetra/jdplus-main"), "3.4.0").target(URI.create("https://github.com/jdemetra/jdplus-nowcasting"), "1.0.2").build();
    static final ReportItem NOWCASTING_200 = ReportItem.builder().exitStatus(VERIFIED).source(URI.create("https://github.com/jdemetra/jdplus-main"), "3.4.0").target(URI.create("https://github.com/jdemetra/jdplus-nowcasting"), "2.0.0").build();

    static Path resolveResource(Class<?> anchor, String name) throws IOException {
        URL resource = anchor.getResource(name);
        if (resource == null) {
            throw new IOException("Resource not found: " + name);
        }
        try {
            return Paths.get(resource.toURI());
        } catch (URISyntaxException ex) {
            throw new IOException(ex);
        }
    }
}