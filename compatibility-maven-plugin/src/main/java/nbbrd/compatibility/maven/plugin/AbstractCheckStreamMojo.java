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

@lombok.Getter
@lombok.Setter
abstract class AbstractCheckStreamMojo extends AbstractCompatibilityMojo {

    @Parameter(property = "compatibility.versioning", defaultValue = Source.DEFAULT_VERSIONING)
    private String versioning;

    @Parameter(property = "compatibility.sourceRef")
    private String sourceRef;

    @Parameter(property = "compatibility.sourceFrom")
    private String sourceFrom;

    @Parameter(property = "compatibility.sourceTo")
    private String sourceTo;

    @Parameter(property = "compatibility.sourceLimit", defaultValue = "-1")
    private int sourceLimit;

    @Parameter(property = "compatibility.targetBinding")
    private String targetBinding;

    @Parameter(property = "compatibility.targetRef")
    private String targetRef;

    @Parameter(property = "compatibility.targetFrom")
    private String targetFrom;

    @Parameter(property = "compatibility.targetTo")
    private String targetTo;

    @Parameter(property = "compatibility.targetLimit", defaultValue = "-1")
    private int targetLimit;

    @Parameter(defaultValue = "${project.build.directory}/compatibility.md", property = "compatibility.reportFile")
    private File reportFile;

    @ParameterParsing
    protected @NonNull String toVersioning() {
        return versioning != null ? versioning : Source.DEFAULT_VERSIONING;
    }

    @ParameterParsing
    protected @Nullable String toTargetBinding() {
        return targetBinding;
    }

    @ParameterParsing
    protected @NonNull Filter toSourceFilter() throws MojoExecutionException {
        return Filter
                .builder()
                .ref(sourceRef)
                .from(parseLocalDate(sourceFrom))
                .to(parseLocalDate(sourceTo))
                .limit(sourceLimit)
                .build();
    }

    @ParameterParsing
    protected @NonNull Filter toTargetFilter() throws MojoExecutionException {
        return Filter
                .builder()
                .ref(targetRef)
                .from(parseLocalDate(targetFrom))
                .to(parseLocalDate(targetTo))
                .limit(targetLimit)
                .build();
    }

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

    private LocalDate parseLocalDate(String text) throws MojoExecutionException {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Filter.parseLocalDate(text);
        } catch (DateTimeParseException ex) {
            throw new MojoExecutionException(ex);
        }
    }
}

