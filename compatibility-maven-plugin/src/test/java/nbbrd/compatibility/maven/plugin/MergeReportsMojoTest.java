package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Compatibility;
import nbbrd.compatibility.Report;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static java.util.Collections.singletonList;
import static nbbrd.compatibility.maven.plugin.AbstractCompatibilityMojoTest.*;
import static nbbrd.compatibility.maven.plugin.MergeReportsMojo.loadAll;
import static org.assertj.core.api.Assertions.assertThat;

class MergeReportsMojoTest {

    @Test
    void testLoadAll() throws IOException, MojoExecutionException {
        assertThat(loadAll(Compatibility.ofServiceLoader(), singletonList(resolveResource(MergeReportsMojoTest.class, "r1.json").getParent())))
                .containsExactly(
                        Report.builder().item(SDMX_320).item(SDMX_330).build(),
                        Report.builder().item(NOWCASTING_102).item(NOWCASTING_200).build()
                );
    }
}