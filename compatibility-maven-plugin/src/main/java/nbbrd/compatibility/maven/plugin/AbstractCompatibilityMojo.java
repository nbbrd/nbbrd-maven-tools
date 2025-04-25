package nbbrd.compatibility.maven.plugin;

import internal.compatibility.maven.plugin.ParameterParsing;
import lombok.NonNull;
import nbbrd.compatibility.Compatibility;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.Report;
import nbbrd.compatibility.ReportItem;
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

import static java.nio.charset.StandardCharsets.UTF_8;

@lombok.Getter
@lombok.Setter
abstract class AbstractCompatibilityMojo extends AbstractMojo {

    @Parameter(property = "compatibility.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "compatibility.workingDir", defaultValue = "${java.io.tmpdir}")
    private File workingDir;

    @ParameterParsing
    protected @NonNull Compatibility toCompatibility() {
        return Compatibility.ofServiceLoader()
                .toBuilder()
                .onEvent(getLog()::info)
                .onDebug(getLog()::debug)
                .workingDir(toWorkingDir())
                .build();
    }

    @ParameterParsing
    protected @NonNull Path toWorkingDir() {
        return workingDir.toPath();
    }

    protected void logJob(Job job) {
        Log log = getLog();
        log.info("Job:");
        job.getSources().forEach(source -> {
            log.info("  Source:");
            log.info("           URI: " + source.getUri());
            log.info("    Versioning: " + source.getVersioning());
            log.info("        Filter: " + source.getFilter());
        });
        job.getTargets().forEach(target -> {
            log.info("  Target:");
            log.info("           URI: " + target.getUri());
            log.info("       Binding: " + target.getBinding());
            log.info("        Filter: " + target.getFilter());
        });
    }

    protected void logReport(Report report) {
        Log log = getLog();
        log.info("Report:");
        report.getItems().forEach(item -> {
            log.info("  Item:");
            log.info("    Status: " + item.getExitStatus());
            log.info("    Source: " + ReportItem.toLabel(item.getSourceUri(), item.getSourceVersion()));
            log.info("    Target: " + ReportItem.toLabel(item.getTargetUri(), item.getTargetVersion()));
        });
    }

    @VisibleForTesting
    static URI fixUnresolvedProperties(URI uri) {
        String text = uri.toString();
        return URI.create(text
                .replace("$%7Bproject.build.directory%7D/", "")
                .replace("$%7Bproject.basedir%7D/", ""));
    }

    @VisibleForTesting
    static <T> T load(Compatibility compatibility, Path file, Class<T> type) throws MojoExecutionException {
        try {
            return compatibility.getParserByFile(type, file)
                    .map(parser -> TextParser.onParsingReader(parser::parse))
                    .orElseThrow(() -> new MojoExecutionException("No parser found for " + file))
                    .parsePath(file, UTF_8);
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to load file " + file, ex);
        }
    }

    @VisibleForTesting
    static <T> void store(Compatibility compatibility, Path file, Class<T> type, T value) throws MojoExecutionException {
        try {
            Files.createDirectories(file.getParent());
            compatibility.getFormatterByFile(type, file)
                    .map(formatter -> TextFormatter.onFormattingWriter(formatter::format))
                    .orElseThrow(() -> new MojoExecutionException("No formatter found for " + file))
                    .formatPath(value, file, UTF_8);
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to store file " + file, ex);
        }
    }
}
