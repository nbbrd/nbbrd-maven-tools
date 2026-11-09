package nbbrd.bundle.maven.plugin;

import internal.bundle.BundleRequest;
import internal.bundle.Bundler;
import internal.bundle.Launchers;
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

    /** Destination directory. */
    @Parameter(defaultValue = "${project.build.directory}", required = true)
    File outputDirectory;

    /** Output basename, followed by the runtime id and archive extension. */
    @Parameter(defaultValue = "${project.build.finalName}-standalone", required = true)
    String finalName;

    /** Application root within the input archive; dot denotes the archive root. */
    @Parameter(defaultValue = ".")
    String baseDirectory;

    /** Launcher provider id; built-in providers are appassembler and netbeans. */
    @Parameter(required = true)
    String launcher;

    /** Application name: Appassembler script name or NetBeans configuration filename stem. */
    @Parameter
    String appName;

    /** Runtime archives, one per output platform. */
    @Parameter(required = true)
    List<Runtime> runtimes;

    /** Output archive format: zip or tar.gz. */
    @Parameter(property = "bundle.format", defaultValue = "zip")
    String format;

    /** Reproducible timestamp, ISO-8601 or seconds since the Unix epoch. */
    @Parameter(defaultValue = "${project.build.outputTimestamp}")
    String outputTimestamp;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (inputFile == null || outputDirectory == null || runtimes == null || runtimes.isEmpty()) {
                throw new IllegalArgumentException("inputFile, outputDirectory and at least one runtime are required");
            }
            Launchers.component(finalName, "finalName");
            if (outputTimestamp == null || outputTimestamp.trim().isEmpty()) {
                throw new IllegalArgumentException("outputTimestamp is required for reproducible bundles");
            }
            Instant timestamp = parseTimestamp(outputTimestamp);
            BundleRequest.Builder builder = BundleRequest.builder()
                    .application(inputFile.toPath())
                    .baseDirectory(baseDirectory)
                    .launcher(launcher)
                    .appName(appName)
                    .format(format)
                    .timestamp(timestamp);
            for (Runtime runtime : runtimes) {
                if (runtime == null) {
                    throw new IllegalArgumentException("Runtime must not be null");
                }
                Path output = outputDirectory.toPath().resolve(finalName + "-" + runtime.getId() + "." + format);
                try {
                    Path archive = runtime.resolveArchive();
                    builder.runtime(BundleRequest.RuntimeBundle.builder()
                            .id(runtime.getId())
                            .archive(archive)
                            .output(output)
                            .javaHome(runtime.getJavaHome())
                            .build());
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Runtime '" + runtime.getId() + "': " + ex.getMessage(), ex);
                } catch (IOException ex) {
                    throw new IOException("Runtime '" + runtime.getId() + "': " + ex.getMessage(), ex);
                }
            }
            BundleRequest request = builder.build();
            Bundler.bundle(request);
            for (BundleRequest.RuntimeBundle runtime : request.getRuntimes()) {
                getLog().info("Bundled runtime " + runtime.getId() + ": " + runtime.getOutput());
            }
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
