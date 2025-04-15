package nbbrd.compatibility.maven.plugin;

import internal.compatibility.maven.plugin.ParameterParsing;
import lombok.NonNull;
import nbbrd.compatibility.Compatibility;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.Report;
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

import static java.nio.charset.StandardCharsets.UTF_8;

@lombok.Getter
@lombok.Setter
abstract class CompatibilityMojo extends AbstractMojo {

    @Parameter(defaultValue = "false", property = "compatibility.skip")
    private boolean skip;

    @Parameter(defaultValue = "${java.io.tmpdir}", property = "compatibility.working.dir")
    private File workingDir;

    @ParameterParsing
    protected @NonNull Compatibility toCompatibility() {
        Compatibility original = Compatibility.ofServiceLoader();
        return original
                .toBuilder()
                .builder(Builder.logging(getLog()::debug, original.getBuilder()))
                .onEvent(getLog()::info)
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
            log.info("      Property: " + target.getProperty());
            log.info("      Building: " + target.getBuilding());
            log.info("        Filter: " + target.getFilter());
        });
    }

    protected void logReport(Report report) {
        Log log = getLog();
        log.info("Report:");
        report.getItems().forEach(item -> {
            log.info("  Item:");
            log.info("    Status: " + item.getExitStatus());
            log.info("    Source: " + item.getSourceUri() + "@" + item.getSourceVersion());
            log.info("    Target: " + item.getTargetUri() + "@" + item.getTargetVersion());
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
