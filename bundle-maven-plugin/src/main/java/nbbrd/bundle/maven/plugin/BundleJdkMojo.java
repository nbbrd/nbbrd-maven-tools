package nbbrd.bundle.maven.plugin;

import internal.bundle.BundleRequest;
import internal.bundle.Bundler;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Bundle local Java runtimes into application distributions. */
@Mojo(name = "bundle-jdk", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class BundleJdkMojo extends AbstractMojo {

    /** Original application ZIP or TAR.GZ. */
    @Parameter(property = "bundle.inputFile", required = true)
    File inputFile;

    /** Application root within the input archive; dot denotes the archive root. */
    @Parameter(property = "bundle.applicationRoot", defaultValue = ".")
    String applicationRoot;

    /** Launcher provider id; built-in providers are appassembler and netbeans. */
    @Parameter(property = "bundle.launcherId", required = true)
    String launcherId;

    /** Filename stem, not display name: Appassembler script or NetBeans configuration basename. */
    @Parameter(property = "bundle.appName", defaultValue = "${project.artifactId}")
    String appName;

    /** Runtime archives, one per output platform. */
    @Parameter(required = true)
    List<Runtime> runtimes;

    /** Reproducible timestamp, ISO-8601 or seconds since the Unix epoch. */
    @Parameter(property = "bundle.outputTimestamp", defaultValue = "${project.build.outputTimestamp}", required = true)
    String outputTimestamp;

    /** Skip bundling. Required Maven parameters must still be configured. */
    @Parameter(property = "bundle.skip", defaultValue = "false")
    boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Java runtime bundling has been skipped.");
            return;
        }
        try {
            if (inputFile == null || runtimes == null || runtimes.isEmpty()) {
                throw new IllegalArgumentException("inputFile and at least one runtime are required");
            }
            if (outputTimestamp == null || outputTimestamp.trim().isEmpty()) {
                throw new IllegalArgumentException("outputTimestamp is required for reproducible bundles");
            }
            Instant timestamp = parseTimestamp(outputTimestamp);
            BundleRequest.Builder builder = BundleRequest.builder()
                    .application(inputFile.toPath())
                    .baseDirectory(applicationRoot)
                    .launcher(launcherId)
                    .appName(appName)
                    .timestamp(timestamp);
            for (Runtime runtime : runtimes) {
                if (runtime == null) {
                    throw new IllegalArgumentException("Runtime must not be null");
                }
                try {
                    Path archive = runtime.resolveArchive();
                    if (runtime.getOutputFile() == null) {
                        throw new IllegalArgumentException("outputFile is required");
                    }
                    builder.runtime(BundleRequest.RuntimeBundle.builder()
                            .id(runtime.getId())
                            .archive(archive)
                            .output(runtime.getOutputFile().toPath())
                            .javaHome(runtime.getJavaHome())
                            .build());
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Runtime '" + runtime.getId() + "': " + ex.getMessage(), ex);
                } catch (IOException ex) {
                    throw new IOException("Runtime '" + runtime.getId() + "': " + ex.getMessage(), ex);
                }
            }
            BundleRequest request = builder.build();
            Bundler.bundle(request, message -> getLog().info(message));
        } catch (IllegalArgumentException ex) {
            throw new MojoFailureException(ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoExecutionException("Cannot bundle Java runtime: " + ex.getMessage(), ex);
        }
    }

    private static Instant parseTimestamp(String value) {
        try {
            return value.matches("[0-9]+") ? Instant.ofEpochSecond(Long.parseLong(value)) : Instant.parse(value);
        } catch (DateTimeException | ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid outputTimestamp: " + value, ex);
        }
    }
}
