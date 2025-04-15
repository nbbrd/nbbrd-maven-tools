package nbbrd.compatibility.maven.plugin;

import internal.compatibility.maven.plugin.ParameterParsing;
import nbbrd.compatibility.Compatibility;
import nbbrd.compatibility.Job;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@lombok.Getter
@lombok.Setter
@Mojo(name = "split-job", defaultPhase = LifecyclePhase.NONE, threadSafe = true, requiresProject = false)
public final class SplitJobMojo extends CompatibilityMojo {

    @Parameter(defaultValue = "", property = "compatibility.job")
    private File job;

    @ParameterParsing
    private Path toJobFile() {
        return job.toPath();
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (isSkip()) {
            getLog().info("Job splitting has been skipped.");
            return;
        }

        if (job == null || !Files.isRegularFile(job.toPath())) {
            getLog().info("No job to split.");
            return;
        }

        Compatibility compatibility = toCompatibility();
        Function<Job, Path> outputFileFactory = getFileNameFactory().andThen(toWorkingDir()::resolve);

        Path inputFile = toJobFile();
        Job input = load(compatibility, inputFile, Job.class);

        for (Job output : compatibility.splitJob(input)) {
            Path outputFile = outputFileFactory.apply(output);
            store(compatibility, outputFile, Job.class, output);
            getLog().info("Split job written to: " + outputFile);
        }
    }

    private Function<Job, String> getFileNameFactory() {
        AtomicInteger i = new AtomicInteger(0);
        return ignore -> "job_" + i.getAndIncrement() + ".json";
    }
}
