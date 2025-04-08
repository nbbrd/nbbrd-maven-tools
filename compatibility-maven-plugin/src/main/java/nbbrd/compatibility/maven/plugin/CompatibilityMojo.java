package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Compatibility;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.Report;
import nbbrd.compatibility.spi.Builder;
import nbbrd.compatibility.spi.Format;
import nbbrd.io.text.TextFormatter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.function.Predicate;

import static java.nio.charset.StandardCharsets.UTF_8;

@lombok.Getter
@lombok.Setter
abstract class CompatibilityMojo extends AbstractMojo {

    @Parameter(defaultValue = "false", property = "compatibility.skip")
    private boolean skip;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String projectVersion;

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File projectBaseDir;

    @Parameter(defaultValue = "${java.io.tmpdir}", readonly = true)
    private File workingDir;

    @Parameter(defaultValue = "${project.build.directory}/compatibility.md", property = "compatibility.report.file")
    private File reportFile;

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
        try {
            log.info("Job: ");
            log.info(getJobTextFormatter(compatibility, onId("json")).formatToString(job));
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to format job", ex);
        }
        log.info("ReportFile: " + reportFile);
    }

    protected Report exec(Compatibility compatibility, Job job) throws MojoExecutionException {
        getLog().info("Using builder: " + compatibility.getBuilder().getBuilderId());
        try {
            return compatibility.check(job);
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to execute job", ex);
        }
    }

    protected void writeReport(Compatibility compatibility, Report report) throws MojoExecutionException {
        try {
            Files.createDirectories(reportFile.toPath().getParent());
            getReportTextFormatter(compatibility, onFile(reportFile)).formatFile(report, reportFile, UTF_8);
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to write report", ex);
        }
    }

    protected Compatibility loadCompatibility() {
        Compatibility original = Compatibility.ofServiceLoader();
        return original
                .toBuilder()
                .builder(Builder.logging(getLog()::debug, original.getBuilder()))
                .onEvent(getLog()::info)
                .build();
    }

    protected static TextFormatter<Job> getJobTextFormatter(Compatibility compatibility, Predicate<? super Format> filter) {
        return compatibility
                .getFormats()
                .stream()
                .filter(Format::canFormatJob)
                .filter(filter)
                .findFirst()
                .map(CompatibilityMojo::asJobFormatter)
                .orElse(onToString());
    }

    private static TextFormatter<Report> getReportTextFormatter(Compatibility compatibility, Predicate<? super Format> filter) {
        return compatibility
                .getFormats()
                .stream()
                .filter(Format::canFormatReport)
                .filter(filter)
                .findFirst()
                .map(CompatibilityMojo::asReportFormatter)
                .orElse(onToString());
    }

    private static Predicate<Format> onId(String id) {
        return format -> format.getFormatId().equals(id);
    }

    private static Predicate<Format> onFile(File file) {
        return format -> {
            try {
                return format.getFormatFileFilter().accept(file.toPath());
            } catch (IOException e) {
                return false;
            }
        };
    }

    private static <T> TextFormatter<T> onToString() {
        return TextFormatter.onFormattingWriter((j, w) -> w.write(j.toString()));
    }

    private static TextFormatter<Job> asJobFormatter(Format format) {
        return TextFormatter.onFormattingWriter((Job j, Writer w) -> format.formatJob(w, j));
    }

    private static TextFormatter<Report> asReportFormatter(Format format) {
        return TextFormatter.onFormattingWriter((Report j, Writer w) -> format.formatReport(w, j));
    }
}
