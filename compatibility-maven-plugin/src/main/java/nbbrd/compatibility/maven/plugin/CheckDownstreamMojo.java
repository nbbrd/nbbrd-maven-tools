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

    @Parameter(property = "compatibility.source", required = true, defaultValue = "${project.baseUri}")
    private URI source;

    @Parameter(property = "compatibility.sourceBinding", required = true)
    private String sourceBinding;

    @Parameter(property = "compatibility.sourceVersioning", defaultValue = Source.DEFAULT_VERSIONING)
    private String sourceVersioning;

    @Parameter(property = "compatibility.sourceRef")
    private String sourceRef;

    @Parameter(property = "compatibility.sourceFrom")
    private String sourceFrom;

    @Parameter(property = "compatibility.sourceTo")
    private String sourceTo;

    @Parameter(property = "compatibility.sourceLimit", defaultValue = NO_LIMIT)
    private int sourceLimit;

    @Parameter(property = "compatibility.targets", required = true)
    private List<URI> targets;

    @Parameter(property = "compatibility.targetRefs")
    private List<String> targetRefs;

    @Parameter(property = "compatibility.targetFroms")
    private List<String> targetFroms;

    @Parameter(property = "compatibility.targetTos")
    private List<String> targetTos;

    @Parameter(property = "compatibility.targetLimits")
    private List<Integer> targetLimits;

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
                .binding(requireNonNull(sourceBinding, "Source binding must not be null"))
                .versioning(parseVersioning(sourceVersioning))
                .filter(parserFilter(sourceRef, sourceFrom, sourceTo, sourceLimit))
                .build();
    }

    @ParameterParsing
    private List<Target> toTargets() throws MojoExecutionException {
        int size = targets.size();

        // required
        if (size == 0) throw new MojoExecutionException("No targets provided");

        // optional
        if (hasItems(targetRefs)) checkSize(targetRefs, size, "targetRefs");
        if (hasItems(targetFroms)) checkSize(targetFroms, size, "targetFroms");
        if (hasItems(targetTos)) checkSize(targetTos, size, "targetTos");
        if (hasItems(targetLimits)) checkSize(targetLimits, size, "targetLimits");

        List<Target> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(Target
                    .builder()
                    .uri(targets.get(i))
                    .filter(parserFilter(
                            hasItems(targetRefs) ? targetRefs.get(i) : null,
                            hasItems(targetFroms) ? targetFroms.get(i) : null,
                            hasItems(targetTos) ? targetTos.get(i) : null,
                            hasItems(targetLimits) ? targetLimits.get(i) : -1))
                    .build());
        }
        return result;
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
