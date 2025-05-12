package nbbrd.compatibility.maven.plugin;

import internal.compatibility.maven.plugin.ParameterParsing;
import lombok.NonNull;
import nbbrd.compatibility.*;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@lombok.Getter
@lombok.Setter
abstract class AbstractCheckStreamMojo extends AbstractCompatibilityMojo {

    @Parameter(defaultValue = "${project.build.directory}/report.md", property = "compatibility.reportFile")
    private File reportFile;

    @ParameterParsing
    protected @NonNull Path toReportFile() {
        return Paths.get(fixUnresolvedProperties(reportFile.toURI()));
    }

    protected void checkStream(Job input) throws MojoExecutionException {
        logJob(input);
        Compatibility compatibility = toCompatibility();

        Path outputFile = toReportFile();
        Report output = check(compatibility, input);
        logReport(output);

        getLog().info("Writing report to " + outputFile);
        store(compatibility, outputFile, Report.class, output);
    }

    private static Report check(Compatibility compatibility, Job input) throws MojoExecutionException {
        try {
            return compatibility.check(input);
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to check job", ex);
        }
    }

    static @Nullable LocalDate parseLocalDate(@Nullable String text) throws MojoExecutionException {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Filter.parseLocalDate(text);
        } catch (DateTimeParseException ex) {
            throw new MojoExecutionException(ex);
        }
    }

    static @NonNull String parseVersioning(@Nullable String text) {
        return text != null && !text.isEmpty() ? text : Source.DEFAULT_VERSIONING;
    }

    static Filter parserFilter(String ref, String from, String to, int limit) throws MojoExecutionException {
        return Filter
                .builder()
                .ref(ref)
                .from(parseLocalDate(from))
                .to(parseLocalDate(to))
                .limit(limit)
                .build();
    }

    static final String NO_LIMIT = "-1";

    static void checkSize(List<?> list, int expectedSize, String name) throws MojoExecutionException {
        if (list.size() != expectedSize) {
            throw new MojoExecutionException(name + " must have " + expectedSize + " elements");
        }
    }

    static boolean hasItems(List<?> list) {
        return !list.isEmpty();
    }
}

