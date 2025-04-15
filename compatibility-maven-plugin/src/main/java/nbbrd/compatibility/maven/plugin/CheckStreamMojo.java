package nbbrd.compatibility.maven.plugin;

import internal.compatibility.maven.plugin.MojoFunction;
import internal.compatibility.maven.plugin.ParameterParsing;
import lombok.NonNull;
import nbbrd.compatibility.*;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Objects;

@lombok.Getter
@lombok.Setter
public abstract class CheckStreamMojo extends CompatibilityMojo {

    @Parameter(defaultValue = Source.DEFAULT_VERSIONING, property = "compatibility.versioning")
    private String versioning;

    @Parameter(defaultValue = "", property = "compatibility.source.ref")
    private String sourceRef;

    @Parameter(defaultValue = "-999999999-01-01", property = "compatibility.source.from")
    private String sourceFrom;

    @Parameter(defaultValue = "+999999999-12-31", property = "compatibility.source.to")
    private String sourceTo;

    @Parameter(defaultValue = "0x7fffffff", property = "compatibility.source.limit")
    private int sourceLimit;

    @Parameter(defaultValue = Target.NO_PROPERTY, property = "compatibility.property")
    private String property;

    @Parameter(defaultValue = "", property = "compatibility.target.ref")
    private String targetRef;

    @Parameter(defaultValue = "-999999999-01-01", property = "compatibility.target.from")
    private String targetFrom;

    @Parameter(defaultValue = "+999999999-12-31", property = "compatibility.target.to")
    private String targetTo;

    @Parameter(defaultValue = "0x7fffffff", property = "compatibility.target.limit")
    private int targetLimit;

    @Parameter(defaultValue = "${project.build.directory}/compatibility.md", property = "compatibility.report.file")
    private File reportFile;

    @ParameterParsing
    protected @NonNull String toVersioning() {
        return versioning != null ? versioning : Source.DEFAULT_VERSIONING;
    }

    @ParameterParsing
    protected @NonNull String toProperty() {
        return property != null ? property : Target.NO_PROPERTY;
    }

    @ParameterParsing
    protected @NonNull Filter toSourceFilter() throws MojoExecutionException {
        return Filter
                .builder()
                .ref(Objects.toString(sourceRef, ""))
                .from(FROM_PARSER.applyWithMojo(sourceFrom))
                .to(TO_PARSER.applyWithMojo(sourceTo))
                .limit(sourceLimit)
                .build();
    }

    @ParameterParsing
    protected @NonNull Filter toTargetFilter() throws MojoExecutionException {
        return Filter
                .builder()
                .ref(Objects.toString(targetRef, ""))
                .from(FROM_PARSER.applyWithMojo(targetFrom))
                .to(TO_PARSER.applyWithMojo(targetTo))
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

    private static final MojoFunction<String, LocalDate> FROM_PARSER = MojoFunction.of(Filter::parseLocalDate, "Invalid format for 'from' parameter");
    private static final MojoFunction<String, LocalDate> TO_PARSER = MojoFunction.of(Filter::parseLocalDate, "Invalid format for 'to' parameter");
}
