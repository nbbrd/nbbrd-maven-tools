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
public abstract class CheckStreamMojo extends CompatibilityMojo {

    @Parameter(defaultValue = Source.DEFAULT_VERSIONING, property = "compatibility.versioning")
    private String versioning;

    @Parameter(property = "compatibility.source.ref")
    private String sourceRef;

    @Parameter(property = "compatibility.source.from")
    private String sourceFrom;

    @Parameter(property = "compatibility.source.to")
    private String sourceTo;

    @Parameter(defaultValue = "-1", property = "compatibility.source.limit")
    private int sourceLimit;

    @Parameter(property = "compatibility.target.binding")
    private String targetBinding;

    @Parameter(property = "compatibility.target.ref")
    private String targetRef;

    @Parameter(property = "compatibility.target.from")
    private String targetFrom;

    @Parameter(property = "compatibility.target.to")
    private String targetTo;

    @Parameter(defaultValue = "-1", property = "compatibility.target.limit")
    private int targetLimit;

    @Parameter(defaultValue = "${project.build.directory}/compatibility.md", property = "compatibility.report.file")
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

