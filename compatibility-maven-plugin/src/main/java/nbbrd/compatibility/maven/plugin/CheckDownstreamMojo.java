package nbbrd.compatibility.maven.plugin;

import internal.compatibility.maven.plugin.ParameterParsing;
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
@Mojo(name = "check-downstream", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public final class CheckDownstreamMojo extends AbstractCheckStreamMojo {

    @Parameter(property = "compatibility.source", defaultValue = "${project.baseUri}")
    private URI source;

    @Parameter(property = "compatibility.targets")
    private List<URI> targets;

    @ParameterParsing
    private Job toJob() throws MojoExecutionException {
        return Job
                .builder()
                .source(toSource())
                .targets(toTargets())
                .build();
    }

    @ParameterParsing
    private Source toSource() throws MojoExecutionException {
        return Source
                .builder()
                .uri(requireNonNull(source, "Source URI must not be null"))
                .versioning(toVersioning())
                .filter(toSourceFilter())
                .build();
    }

    @ParameterParsing
    private List<Target> toTargets() throws MojoExecutionException {
        List<Target> result = new ArrayList<>();
        for (URI target : targets) {
            result.add(toTarget(target));
        }
        return result;
    }

    @ParameterParsing
    private Target toTarget(URI uri) throws MojoExecutionException {
        return Target
                .builder()
                .uri(requireNonNull(uri, "Target URI must not be null"))
                .binding(toTargetBinding())
                .filter(toTargetFilter())
                .build();
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (isSkip()) {
            getLog().info("Downstream check has been skipped.");
            return;
        }

        checkStream(toJob());
    }
}
