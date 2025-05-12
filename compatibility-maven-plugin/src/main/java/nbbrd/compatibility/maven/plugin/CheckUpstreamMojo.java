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
@Mojo(name = "check-upstream", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public final class CheckUpstreamMojo extends AbstractCheckStreamMojo {

    @Parameter(property = "compatibility.sources", required = true)
    private List<URI> sources;

    @Parameter(property = "compatibility.sourceBindings", required = true)
    private List<String> sourceBindings;

    @Parameter(property = "compatibility.sourceVersionings")
    private List<String> sourceVersionings;

    @Parameter(property = "compatibility.sourceRefs")
    private List<String> sourceRefs;

    @Parameter(property = "compatibility.sourceFroms")
    private List<String> sourceFroms;

    @Parameter(property = "compatibility.sourceTos")
    private List<String> sourceTos;

    @Parameter(property = "compatibility.sourceLimits", defaultValue = NO_LIMIT)
    private List<Integer> sourceLimits;

    @Parameter(property = "compatibility.target", required = true, defaultValue = "${project.baseUri}")
    private URI target;

    @Parameter(property = "compatibility.targetRef")
    private String targetRef;

    @Parameter(property = "compatibility.targetFrom")
    private String targetFrom;

    @Parameter(property = "compatibility.targetTo")
    private String targetTo;

    @Parameter(property = "compatibility.targetLimit", defaultValue = NO_LIMIT)
    private int targetLimit;

    @ParameterParsing
    private Job toJob() throws MojoExecutionException {
        return Job
                .builder()
                .sources(toSources())
                .target(toTarget())
                .build();
    }

    @ParameterParsing
    private List<Source> toSources() throws MojoExecutionException {
        int size = sources.size();

        // required
        if (size == 0) throw new MojoExecutionException("No sources provided");
        checkSize(sourceBindings, size, "sourceBindings");

        // optional
        if (hasItems(sourceVersionings)) checkSize(sourceVersionings, size, "sourceVersionings");
        if (hasItems(sourceRefs)) checkSize(sourceRefs, size, "sourceRefs");
        if (hasItems(sourceFroms)) checkSize(sourceFroms, size, "sourceFroms");
        if (hasItems(sourceTos)) checkSize(sourceTos, size, "sourceTos");
        if (hasItems(sourceLimits)) checkSize(sourceLimits, size, "sourceLimits");

        List<Source> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(Source
                    .builder()
                    .uri(sources.get(i))
                    .binding(sourceBindings.get(i))
                    .versioning(hasItems(sourceVersionings) ? parseVersioning(sourceVersionings.get(i)) : Source.DEFAULT_VERSIONING)
                    .filter(parserFilter(
                            hasItems(sourceRefs) ? sourceRefs.get(i) : null,
                            hasItems(sourceFroms) ? sourceFroms.get(i) : null,
                            hasItems(sourceTos) ? sourceTos.get(i) : null,
                            hasItems(sourceLimits) ? sourceLimits.get(i) : -1))
                    .build());
        }
        return result;
    }

    @ParameterParsing
    private Target toTarget() throws MojoExecutionException {
        return Target
                .builder()
                .uri(requireNonNull(target, "Target URI must not be null"))
                .filter(parserFilter(targetRef, targetFrom, targetTo, targetLimit))
                .build();
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (isSkip()) {
            getLog().info("Upstream check has been skipped.");
            return;
        }

        checkStream(toJob());
    }
}
