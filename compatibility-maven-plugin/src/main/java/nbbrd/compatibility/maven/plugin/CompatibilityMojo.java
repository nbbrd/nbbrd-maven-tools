package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Compatibility;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.Report;
import nbbrd.compatibility.spi.Format;
import nbbrd.io.text.TextFormatter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

@lombok.Getter
@lombok.Setter
abstract class CompatibilityMojo extends AbstractMojo {

    @Parameter(defaultValue = "false", property = "compatibility.skip")
    private boolean skip;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String projectVersion;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File projectBaseDir;

    @Parameter(defaultValue = "${project.build.directory}/compatibility", readonly = true)
    private File workingDir;

    @Parameter(defaultValue = "${project.build.directory}/compatibility.md", readonly = true)
    private String reportFile;

    protected boolean isRootProject() {
        if (projectBaseDir == null) {
            return true;
        }
        File parentDir = projectBaseDir.getParentFile();
        if (parentDir != null) {
            File parentPom = parentDir.toPath().resolve("pom.xml").toFile();
            return !parentPom.exists();
        }
        return true;
    }

    protected void log(Compatibility compatibility, Job job) throws MojoExecutionException {
        Log log = getLog();
        TextFormatter<Job> formatter = compatibility.getFormats()
                .stream()
                .filter(item -> item.getFormatId().equals("json"))
                .findFirst()
                .map(CompatibilityMojo::asJobFormatter)
                .orElse(onToString());
        try {
            log.info("Job: ");
            log.info(formatter.formatToString(job));
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to format job", ex);
        }
        log.info("ReportFile: " + reportFile);
    }

    protected Report exec(Compatibility compatibility, Job job) throws MojoExecutionException {
        getLog().info("Using builder: " + compatibility.getBuilder().getBuilderId());
        try {
            return compatibility.execute(job);
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to execute job", ex);
        }
    }

    protected Compatibility loadCompatibility() {
        return Compatibility.ofServiceLoader()
                .toBuilder()
                .onEvent(getLog()::info)
                .build();
    }

    private static TextFormatter<Job> onToString() {
        return TextFormatter.onFormattingWriter((j, w) -> w.write(j.toString()));
    }

    private static TextFormatter<Job> asJobFormatter(Format format) {
        return TextFormatter.onFormattingWriter((Job j, Writer w) -> format.formatJob(w, j));
    }
}
