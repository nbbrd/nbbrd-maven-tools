package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Compatibility;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.Report;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;

@lombok.Getter
@lombok.Setter
abstract class CompatibilityMojo extends AbstractMojo {

    @Parameter(defaultValue = "false", property = "compatibility.skip")
    private boolean skip;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String projectVersion;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File projectBaseDir;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File workingDir;

    @Parameter(defaultValue = "report.csv", readonly = true)
    private String reportFilename;

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

    protected void log(Job job) {
        Log log = getLog();
        job.getSources().forEach(source -> log.info(source.toString()));
        job.getTargets().forEach(target -> log.info(target.toString()));
        log.info(job.getWorkingDir().toString());
        log.info(job.getReportFilename());
    }

    protected Report exec(Job job) throws MojoExecutionException {
        Compatibility compatibility = Compatibility.ofServiceLoader();
        getLog().info("Using engine: " + compatibility.getEngine().getId());
        try {
            return compatibility.execute(job);
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to execute job", ex);
        }
    }
}
