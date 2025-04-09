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
@Mojo(name = "check-downstream", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public final class CheckDownstreamMojo extends SimpleCheckMojo {

    @Parameter(defaultValue = "${project.baseUri}", property = "compatibility.source")
    private URI source;

    @Parameter(defaultValue = "", property = "compatibility.targets")
    private List<URI> targets;

    @Override
    public void execute() throws MojoExecutionException {
        if (isSkip()) {
            getLog().info("Downstream check has been skipped.");
            return;
        }

        check(toJob());
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
                .versioning(toVersioning())
                .filter(toSourceFilter())
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
                .property(toProperty())
                .filter(toTargetFilter())
                .build();
    }
}
