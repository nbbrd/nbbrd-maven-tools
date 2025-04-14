package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Job;
import nbbrd.compatibility.Source;
import nbbrd.compatibility.Target;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

@lombok.Getter
@lombok.Setter
@Mojo(name = "check-upstream", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public final class CheckUpstreamMojo extends SimpleCheckMojo {

    @Parameter(defaultValue = "", property = "compatibility.sources")
    private List<URI> sources;

    @Parameter(defaultValue = "${project.baseUri}", property = "compatibility.target")
    private URI target;

    @Override
    public void execute() throws MojoExecutionException {
        if (isSkip()) {
            getLog().info("Upstream check has been skipped.");
            return;
        }

        check(toJob());
    }

    private Job toJob() throws MojoExecutionException {
        return Job
                .builder()
                .sources(toSources())
                .target(toTarget())
                .build();
    }

    private List<Source> toSources() throws MojoExecutionException {
        List<Source> result = new ArrayList<>();
        for (URI source : sources) {
            result.add(toSource(source));
        }
        return result;
    }

    private Source toSource(URI uri) throws MojoExecutionException {
        return Source
                .builder()
                .uri(requireNonNull(uri, "Source URI must not be null"))
                .versioning(toVersioning())
                .filter(toSourceFilter())
                .build();
    }

    private Target toTarget() throws MojoExecutionException {
        return Target
                .builder()
                .uri(requireNonNull(target, "Target URI must not be null"))
                .property(toProperty())
                .filter(toTargetFilter())
                .build();
    }
}
