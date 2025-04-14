package nbbrd.compatibility.maven.plugin;

import lombok.NonNull;
import nbbrd.compatibility.*;
import nbbrd.compatibility.spi.Builder;
import nbbrd.design.VisibleForTesting;
import nbbrd.io.text.TextFormatter;
import nbbrd.io.text.TextParser;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    @Parameter(defaultValue = "${java.io.tmpdir}", property = "compatibility.working.dir")
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

    protected @NonNull Path toReportFile() {
        return Paths.get(fixUnresolvedProperties(reportFile.toURI()));
    }

    protected void log(Compatibility compatibility, Job job) throws MojoExecutionException {
        Log log = getLog();
        try {
            log.info("Job: ");
            log.info(
                    compatibility.getFormatterById(Job.class, "json")
                            .map(CompatibilityMojo::asTextFormatter)
                            .orElseThrow(() -> new MojoExecutionException("No JSON formatter found"))
                            .formatToString(job)
            );
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to format job", ex);
        }
        log.info("ReportFile: " + toReportFile());
    }

    protected Report check(Compatibility compatibility, Job job) throws MojoExecutionException {
        try {
            return compatibility.check(job);
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to execute job", ex);
        }
    }

    protected void writeReport(Compatibility compatibility, Report report) throws MojoExecutionException {
        Path file = toReportFile();
        try {
            Files.createDirectories(file.getParent());
            storeReport(compatibility, file, report);
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to write report", ex);
        }
        getLog().info("Report written to " + file);
    }

    protected Compatibility loadCompatibility() {
        Compatibility original = Compatibility.ofServiceLoader();
        return original
                .toBuilder()
                .builder(Builder.logging(getLog()::debug, original.getBuilder()))
                .onEvent(getLog()::info)
                .workingDir(getWorkingDir().toPath())
                .build();
    }

    @VisibleForTesting
    static URI fixUnresolvedProperties(URI uri) {
        String text = uri.toString();
        return URI.create(text
                .replace("$%7Bproject.build.directory%7D/", "")
                .replace("$%7Bproject.basedir%7D/", ""));
    }

    protected static <T> TextFormatter<T> asTextFormatter(Formatter<T> formatter) {
        return TextFormatter.onFormattingWriter(formatter::format);
    }

    protected static <T> TextParser<T> asTextParser(Parser<T> parser) {
        return TextParser.onParsingReader(parser::parse);
    }

    @VisibleForTesting
    static Report loadReport(Compatibility compatibility, Path file) throws MojoExecutionException, IOException {
        return compatibility.getParserByFile(Report.class, file)
                .map(CompatibilityMojo::asTextParser)
                .orElseThrow(() -> new MojoExecutionException("No parser found for " + file))
                .parsePath(file, UTF_8);
    }

    @VisibleForTesting
    static void storeReport(Compatibility compatibility, Path file, Report report) throws MojoExecutionException, IOException {
        compatibility.getFormatterByFile(Report.class, file)
                .map(CompatibilityMojo::asTextFormatter)
                .orElseThrow(() -> new MojoExecutionException("No formatter found for " + file))
                .formatPath(report, file, UTF_8);
    }
}
