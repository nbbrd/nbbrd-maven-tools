package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Compatibility;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.Source;
import nbbrd.compatibility.Target;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.net.URI;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

@lombok.Getter
@lombok.Setter
@Mojo(name = "check-downstream", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public final class CheckDownstreamMojo extends CompatibilityMojo {

    @Parameter(defaultValue = "${project.baseUri}", property = "compatibility.source")
    private URI source;

    @Parameter(defaultValue = Source.DEFAULT_VERSIONING, property = "compatibility.versioning")
    private String versioning;

    @Parameter(defaultValue = "", property = "compatibility.targets")
    private List<URI> targets;

    @Parameter(defaultValue = Target.NO_PROPERTY, property = "compatibility.property")
    private String property;

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

    private Job toJob() {
        return Job
                .builder()
                .source(toSource())
                .targets(targets.stream().map(this::toTarget).collect(toList()))
                .workingDir(getWorkingDir().toPath())
                .build();
    }

    private Source toSource() {
        return Source
                .builder()
                .uri(requireNonNull(source, "Source URI must not be null"))
                .versioning(versioning != null ? versioning : Source.DEFAULT_VERSIONING)
                .build();
    }

    private Target toTarget(URI uri) {
        return Target
                .builder()
                .uri(requireNonNull(uri, "Target URI must not be null"))
                .property(property != null ? property : Target.NO_PROPERTY)
                .build();
    }
}
