package nbbrd.bundle.maven.plugin;

import internal.bundle.BundleRequest;
import internal.bundle.Bundler;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    /** Runtime archives, one per output platform; mutually exclusive with runtimeIds. */
    @Parameter
    List<Runtime> runtimes;

    /** Comma-separated runtime ids; requires both path templates and excludes runtimes. */
    @Parameter(property = "bundle.runtimeIds")
    String runtimeIds;

    /** Default runtime archive path; {id} is replaced with the runtime id. */
    @Parameter(property = "bundle.archivePathTemplate")
    String archivePathTemplate;

    /** Default output archive path; {id} is replaced with the runtime id. */
    @Parameter(property = "bundle.outputFileTemplate")
    String outputFileTemplate;

    /** Base directory for relative runtime paths. */
    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    File projectDirectory;

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
            if (inputFile == null) {
                throw new IllegalArgumentException("inputFile is required");
            }
            List<Runtime> selectedRuntimes = selectRuntimes();
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
            for (Runtime runtime : selectedRuntimes) {
                if (runtime == null) {
                    throw new IllegalArgumentException("Runtime must not be null");
                }
                try {
                    runtime.validateId();
                    Runtime source = new Runtime();
                    source.setId(runtime.getId());
                    source.setArchivePath(
                            resolvePath(runtime.getArchivePath(), archivePathTemplate, "archivePath", runtime.getId()));
                    Path archive = source.resolveArchive();
                    File output =
                            resolvePath(runtime.getOutputFile(), outputFileTemplate, "outputFile", runtime.getId());
                    builder.runtime(BundleRequest.RuntimeBundle.builder()
                            .id(runtime.getId())
                            .archive(archive)
                            .output(output.toPath())
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

    List<Runtime> selectRuntimes() {
        if (runtimeIds == null) {
            if (runtimes == null || runtimes.isEmpty()) {
                throw new IllegalArgumentException("Configure either runtimeIds or at least one entry in runtimes");
            }
            return runtimes;
        }
        if (runtimes != null && !runtimes.isEmpty()) {
            throw new IllegalArgumentException("runtimeIds and runtimes are mutually exclusive; configure only one");
        }
        List<Runtime> selected = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (String value : runtimeIds.split(",", -1)) {
            String id = value.trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("runtimeIds must contain non-empty comma-separated ids");
            }
            Runtime runtime = new Runtime();
            runtime.setId(id);
            runtime.validateId();
            if (!ids.add(id.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate runtime id in runtimeIds: " + id);
            }
            selected.add(runtime);
        }
        if (archivePathTemplate == null
                || archivePathTemplate.trim().isEmpty()
                || outputFileTemplate == null
                || outputFileTemplate.trim().isEmpty()) {
            throw new IllegalArgumentException("runtimeIds requires both archivePathTemplate and outputFileTemplate");
        }
        return selected;
    }

    File resolvePath(File explicit, String template, String parameter, String id) {
        File path = explicit;
        if (path == null) {
            if (template == null || template.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        parameter + " is required when " + parameter + "Template is not set");
            }
            String expanded = template.replace("{id}", id);
            if (expanded.contains("{") || expanded.contains("}")) {
                throw new IllegalArgumentException(
                        parameter + "Template supports only the {id} placeholder: " + template);
            }
            path = Paths.get(expanded).toFile();
        }
        return path.isAbsolute()
                ? path
                : projectDirectory.toPath().resolve(path.toPath()).toFile();
    }

    private static Instant parseTimestamp(String value) {
        try {
            return value.matches("[0-9]+") ? Instant.ofEpochSecond(Long.parseLong(value)) : Instant.parse(value);
        } catch (DateTimeException | ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid outputTimestamp: " + value, ex);
        }
    }
}
