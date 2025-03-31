package nbbrd.compatibility.maven.plugin;

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
@Mojo(name = "check-upstream", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public final class CheckUpstreamMojo extends CompatibilityMojo {

    @Parameter
    private List<Source> sources;

    @Parameter
    private URI uri;

    @Parameter
    private Tag tag;

    @Parameter
    private Mvn mvn;

    @Override
    public void execute() throws MojoExecutionException {
        if (isSkip()) {
            getLog().info("Upstream check has been skipped.");
            return;
        }

        checkUpstream();
    }

    private void checkUpstream() throws MojoExecutionException {
        Job job = toJob();
        log(job);
        exec(job);
    }

    private Job toJob() {
        return Job
                .builder()
                .sources(sources.stream().map(Source::toValue).collect(toList()))
                .target(asTarget().toValue())
                .workingDir(getWorkingDir())
                .reportFilename(getReportFilename())
                .build();
    }

    private Target asTarget() {
        Target result = new Target();
        result.setUri(uri);
        result.setTag(tag);
        result.setMvn(mvn);
        return result;
    }
}
