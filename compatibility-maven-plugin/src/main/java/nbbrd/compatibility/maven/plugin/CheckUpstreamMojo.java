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
@Mojo(name = "check-upstream", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public final class CheckUpstreamMojo extends CompatibilityMojo {

    @Parameter(defaultValue = "", property = "compatibility.sources")
    private List<URI> sources;

    @Parameter(defaultValue = Source.DEFAULT_VERSIONING, property = "compatibility.versioning")
    private String versioning;

    @Parameter(defaultValue = "${project.baseUri}", property = "compatibility.target")
    private URI target;

    @Parameter(defaultValue = Target.NO_PROPERTY, property = "compatibility.property")
    private String property;

    @Override
    public void execute() throws MojoExecutionException {
        if (isSkip()) {
            getLog().info("Upstream check has been skipped.");
            return;
        }

        checkUpstream();
    }

    private void checkUpstream() throws MojoExecutionException {
        Compatibility compatibility = loadCompatibility();
        Job job = toJob();
        log(compatibility, job);
        exec(compatibility, job);
    }

    private Job toJob() {
        return Job
                .builder()
                .sources(sources.stream().map(this::toSource).collect(toList()))
                .target(toTarget())
                .workingDir(getWorkingDir().toPath())
                .build();
    }

    private Source toSource(URI uri) {
        return Source
                .builder()
                .uri(requireNonNull(uri, "Source URI must not be null"))
                .versioning(versioning != null ? versioning : Source.DEFAULT_VERSIONING)
                .build();
    }

    private Target toTarget() {
        return Target
                .builder()
                .uri(requireNonNull(target, "Target URI must not be null"))
                .property(property != null ? property : Target.NO_PROPERTY)
                .build();
    }
}
