package nbbrd.compatibility.maven.plugin;

import internal.compatibility.maven.plugin.MojoFunction;
import lombok.NonNull;
import nbbrd.compatibility.*;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import java.time.LocalDate;
import java.util.Objects;

@lombok.Getter
@lombok.Setter
public abstract class SimpleCheckMojo extends CompatibilityMojo {

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

    protected @NonNull String toVersioning() {
        return versioning != null ? versioning : Source.DEFAULT_VERSIONING;
    }

    protected @NonNull String toProperty() {
        return property != null ? property : Target.NO_PROPERTY;
    }

    protected @NonNull Filter toSourceFilter() throws MojoExecutionException {
        return Filter
                .builder()
                .ref(Objects.toString(sourceRef, ""))
                .from(FROM_PARSER.applyWithMojo(sourceFrom))
                .to(TO_PARSER.applyWithMojo(sourceTo))
                .limit(sourceLimit)
                .build();
    }

    protected @NonNull Filter toTargetFilter() throws MojoExecutionException {
        return Filter
                .builder()
                .ref(Objects.toString(targetRef, ""))
                .from(FROM_PARSER.applyWithMojo(targetFrom))
                .to(TO_PARSER.applyWithMojo(targetTo))
                .limit(targetLimit)
                .build();
    }

    protected void check(Job job) throws MojoExecutionException {
        Compatibility compatibility = loadCompatibility();
        log(compatibility, job);
        Report report = exec(compatibility, job);
        writeReport(compatibility, report);
    }

    private static final MojoFunction<String, LocalDate> FROM_PARSER = MojoFunction.of(Filter::parseLocalDate, "Invalid format for 'from' parameter");
    private static final MojoFunction<String, LocalDate> TO_PARSER = MojoFunction.of(Filter::parseLocalDate, "Invalid format for 'to' parameter");
}
