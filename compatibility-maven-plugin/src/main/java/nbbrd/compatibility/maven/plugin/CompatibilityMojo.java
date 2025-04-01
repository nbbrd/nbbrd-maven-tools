package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Compatibility;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.Report;
import nbbrd.compatibility.spi.Format;
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

    protected void log(Compatibility compatibility, Job job, String reportFilename) throws MojoExecutionException {
        Log log = getLog();
        Format format = compatibility.getFormats()
                .stream()
                .filter(item -> item.getFormatId().equals("json"))
                .findFirst()
                .orElse(null);
        if (format != null) {
            try {
                format.formatJob(asAppendable(log), job);
            } catch (IOException ex) {
                throw new MojoExecutionException("Failed to format job", ex);
            }
        } else {
            job.getSources().forEach(source -> log.info(source.toString()));
            job.getTargets().forEach(target -> log.info(target.toString()));
            log.info(job.getWorkingDir().toString());
        }
        log.info(reportFilename);
    }

    protected Report exec(Compatibility compatibility, Job job) throws MojoExecutionException {
        getLog().info("Using engine: " + compatibility.getEngine().getJobEngineId());
        try {
            return compatibility.execute(job);
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to execute job", ex);
        }
    }

    protected Compatibility loadCompatibility() {
        return Compatibility.ofServiceLoader();
    }

    private static Appendable asAppendable(Log log) {
        return new Appendable() {
            @Override
            public Appendable append(CharSequence csq) {
                log.info(csq);
                return this;
            }

            @Override
            public Appendable append(CharSequence csq, int start, int end) {
                log.info(csq.subSequence(start, end));
                return this;
            }

            @Override
            public Appendable append(char c) {
                log.info(String.valueOf(c));
                return this;
            }
        };
    }
}
