package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Compatibility;
import nbbrd.compatibility.Job;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.net.URI;
import java.util.List;

import static java.util.stream.Collectors.toList;

@lombok.Getter
@lombok.Setter
@Mojo(name = "check-downstream", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public final class CheckDownstreamMojo extends CompatibilityMojo {

    @Parameter
    private URI uri;

    @Parameter
    private Tag tag;

    @Parameter
    private List<Target> targets;

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
        log(compatibility, job, getReportFilename());
        exec(compatibility, job);
    }

    private Job toJob() {
        return Job
                .builder()
                .source(asSource().toValue())
                .targets(targets.stream().map(Target::toValue).collect(toList()))
                .workingDir(getWorkingDir().toPath())
                .build();
    }

    private Source asSource() {
        Source result = new Source();
        result.setUri(uri);
        result.setTag(tag);
        return result;
    }
}
