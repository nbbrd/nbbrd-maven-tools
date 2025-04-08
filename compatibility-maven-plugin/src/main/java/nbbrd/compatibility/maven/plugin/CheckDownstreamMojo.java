package nbbrd.compatibility.maven.plugin;

import internal.compatibility.maven.plugin.MojoFunction;
import nbbrd.compatibility.*;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

@lombok.Getter
@lombok.Setter
@Mojo(name = "check-downstream", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public final class CheckDownstreamMojo extends CompatibilityMojo {

    @Parameter(defaultValue = "${project.baseUri}", property = "compatibility.source")
    private URI source;

    @Parameter(defaultValue = Source.DEFAULT_VERSIONING, property = "compatibility.versioning")
    private String versioning;

    @Parameter(defaultValue = "${project.version}", property = "compatibility.source.ref")
    private String sourceRef;

    @Parameter(defaultValue = "-999999999-01-01", property = "compatibility.source.from")
    private String sourceFrom;

    @Parameter(defaultValue = "+999999999-12-31", property = "compatibility.source.to")
    private String sourceTo;

    @Parameter(defaultValue = "", property = "compatibility.targets")
    private List<URI> targets;

    @Parameter(defaultValue = Target.NO_PROPERTY, property = "compatibility.property")
    private String property;

    @Parameter(defaultValue = "${project.version}", property = "compatibility.target.ref")
    private String targetRef;

    @Parameter(defaultValue = "-999999999-01-01", property = "compatibility.target.from")
    private String targetFrom;

    @Parameter(defaultValue = "+999999999-12-31", property = "compatibility.target.to")
    private String targetTo;

    @Override
    public void execute() throws MojoExecutionException {
        if (isSkip()) {
            getLog().info("Downstream check has been skipped.");
            return;
        }

        checkDownstream();
    }

    private void checkDownstream() throws MojoExecutionException {
        Compatibility compatibility = loadCompatibility();
        Job job = toJob();
        log(compatibility, job);
        exec(compatibility, job);
    }

    private Job toJob() throws MojoExecutionException {
        return Job
                .builder()
                .source(toSource())
                .targets(toTargets())
                .workingDir(getWorkingDir().toPath())
                .build();
    }

    private Source toSource() throws MojoExecutionException {
        return Source
                .builder()
                .uri(requireNonNull(source, "Source URI must not be null"))
                .versioning(versioning != null ? versioning : Source.DEFAULT_VERSIONING)
                .filter(Filter
                        .builder()
                        .ref(Objects.toString(sourceRef, ""))
                        .from(FROM_PARSER.applyWithMojo(sourceFrom))
                        .to(TO_PARSER.applyWithMojo(sourceTo))
                        .build())
                .build();
    }

    private List<Target> toTargets() throws MojoExecutionException {
        List<Target> result = new ArrayList<>();
        for (URI target : targets) {
            result.add(toTarget(target));
        }
        return result;
    }

    private Target toTarget(URI uri) throws MojoExecutionException {
        return Target
                .builder()
                .uri(requireNonNull(uri, "Target URI must not be null"))
                .property(property != null ? property : Target.NO_PROPERTY)
                .filter(Filter
                        .builder()
                        .ref(Objects.toString(targetRef, ""))
                        .from(FROM_PARSER.applyWithMojo(targetFrom))
                        .to(TO_PARSER.applyWithMojo(targetTo))
                        .build())
                .build();
    }

    private static final MojoFunction<String, LocalDate> FROM_PARSER = MojoFunction.of(Filter::parseLocalDate, "Invalid format for 'from' parameter");
    private static final MojoFunction<String, LocalDate> TO_PARSER = MojoFunction.of(Filter::parseLocalDate, "Invalid format for 'to' parameter");
}
