package nbbrd.buf.maven.plugin;

import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import internal.buf.Buf;
import internal.buf.Platform;
import internal.buf.Problem;
import internal.buf.RuleRunner;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import nbbrd.buf.spi.LintRule;
import nbbrd.buf.spi.LintRuleLoader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

/** Lint protobuf files with buf and custom Java rules. */
@Mojo(name = "lint", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true, requiresProject = false)
public final class LintMojo extends AbstractMojo {

    static final String BUF_GROUP_ID = "build.buf";

    static final String BUF_ARTIFACT_ID = "buf";

    static final String BUF_TYPE = "exe";

    /** Default buf version, resolved from Maven repositories. */
    static final String DEFAULT_VERSION = "1.75.0";

    static final String DEFAULT_INPUT = "src/main/proto";

    /** buf version to resolve as {@code build.buf:buf:exe:<platform>:<version>}. */
    @Parameter(property = "buf.version", defaultValue = DEFAULT_VERSION)
    String version;

    /** Local buf executable; disables resolution when set. */
    @Parameter(property = "buf.executable")
    File executable;

    /** Input of buf: a directory of protobuf files or a buf workspace; defaults to {@code src/main/proto}. */
    @Parameter(property = "buf.input")
    File input;

    /** buf configuration file; buf looks for buf.yaml in the input and its parents by default. */
    @Parameter(property = "buf.configFile")
    File configFile;

    /** IDs of custom rules to disable. */
    @Parameter(property = "buf.disabledRules")
    List<String> disabledRules;

    /** Fail the build when problems are found. */
    @Parameter(property = "buf.failOnError", defaultValue = "true")
    boolean failOnError;

    /** Print GitHub Actions workflow commands so that problems are annotated on pull requests. */
    @Parameter(property = "buf.githubAnnotations", defaultValue = "${env.GITHUB_ACTIONS}")
    boolean githubAnnotations;

    /** Skip linting. */
    @Parameter(property = "buf.skip", defaultValue = "false")
    boolean skip;

    @Parameter(defaultValue = "${env.GITHUB_WORKSPACE}", readonly = true)
    File githubWorkspace;

    /** Base directory for relative paths; the current directory without project. */
    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    File projectDirectory;

    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    File executionRootDirectory;

    /** Directory of the executable and the image; a cache in the local repository without project. */
    @Parameter(defaultValue = "${project.build.directory}/buf", readonly = true, required = true)
    File workDirectory;

    @Parameter(defaultValue = "${settings.localRepository}/.cache/buf", readonly = true, required = true)
    File cacheDirectory;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    RepositorySystemSession repositorySession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> remoteRepositories;

    @Inject
    RepositorySystem repositorySystem;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("buf has been skipped.");
            return;
        }
        if (projectDirectory == null) {
            // standalone execution, without pom.xml: project paths are unresolved
            projectDirectory = executionRootDirectory;
            workDirectory = cacheDirectory;
        }
        if (input == null) {
            input = projectDirectory.toPath().resolve(DEFAULT_INPUT).toFile();
        }
        if (!input.exists()) {
            getLog().info("No protobuf input found in " + input);
            return;
        }
        try {
            List<LintRule> rules = loadRules();
            getLog().info("Linting " + toInput() + " with buf and " + rules.size() + " custom rule(s)");
            Buf buf = Buf.builder()
                    .executable(resolveExecutable())
                    .workingDirectory(projectDirectory.toPath())
                    .input(toInput())
                    .configFile(configFile != null ? configFile.toPath() : null)
                    .build();
            List<Problem> problems = new ArrayList<>(buf.lint());
            if (!rules.isEmpty()) {
                if (problems.stream().anyMatch(problem -> Buf.COMPILE_RULE.equals(problem.getRule()))) {
                    getLog().warn("Custom rules have been skipped because of compilation errors");
                } else {
                    problems.addAll(checkRules(buf, rules));
                }
            }
            report(problems);
        } catch (IllegalArgumentException ex) {
            throw new MojoFailureException(ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoExecutionException("Cannot run buf: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("buf has been interrupted", ex);
        }
    }

    String toInput() {
        Path base = projectDirectory.toPath().toAbsolutePath().normalize();
        Path path = input.toPath().toAbsolutePath().normalize();
        if (path.equals(base)) {
            return ".";
        }
        return path.startsWith(base) ? base.relativize(path).toString() : path.toString();
    }

    private List<LintRule> loadRules() {
        ClassLoader classLoader = getClass().getClassLoader();
        List<LintRule> result = LintRuleLoader.builder()
                .backend(type -> ServiceLoader.load(type, classLoader), loader -> loader)
                .build()
                .get();
        return disabledRules != null ? filterRules(result, disabledRules) : result;
    }

    List<LintRule> filterRules(List<LintRule> rules, List<String> disabled) {
        Set<String> ids = rules.stream().map(LintRule::getLintRuleId).collect(Collectors.toSet());
        for (String id : disabled) {
            if (!ids.contains(id)) {
                getLog().warn("Unknown custom rule cannot be disabled: " + id);
            }
        }
        return rules.stream()
                .filter(rule -> !disabled.contains(rule.getLintRuleId()))
                .collect(Collectors.toList());
    }

    private List<Problem> checkRules(Buf buf, List<LintRule> rules) throws IOException, InterruptedException {
        Path image = workDirectory.toPath().resolve("image.binpb");
        Files.createDirectories(image.getParent());
        buf.build(image);
        Map<String, String> files = buf.listFiles();
        FileDescriptorSet descriptors;
        try (InputStream stream = Files.newInputStream(image)) {
            descriptors = FileDescriptorSet.parseFrom(stream);
        }
        return RuleRunner.builder().rules(rules).build().check(descriptors, files);
    }

    private Path resolveExecutable() throws IOException {
        if (executable != null) {
            return executable.toPath();
        }
        if (version == null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw new IllegalArgumentException("Invalid buf version, expected x.y.z: " + version);
        }
        Platform platform = Platform.current();
        ArtifactRequest request = new ArtifactRequest()
                .setArtifact(
                        new DefaultArtifact(BUF_GROUP_ID, BUF_ARTIFACT_ID, platform.getClassifier(), BUF_TYPE, version))
                .setRepositories(remoteRepositories);
        Path artifact;
        try {
            artifact = repositorySystem
                    .resolveArtifact(repositorySession, request)
                    .getArtifact()
                    .getFile()
                    .toPath();
        } catch (ArtifactResolutionException ex) {
            throw new IOException("Cannot resolve buf " + version + " for " + platform.getClassifier(), ex);
        }
        return install(artifact, workDirectory.toPath().resolve(platform.getExecutableName(version)));
    }

    /** Copies the resolved artifact out of the local repository, so that it can be made executable. */
    static Path install(Path artifact, Path target) throws IOException {
        if (!Files.isRegularFile(target) || Files.size(target) != Files.size(artifact)) {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(
                    target.getParent(), target.getFileName().toString(), ".tmp");
            try {
                Files.copy(artifact, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        if (!Files.isExecutable(target) && !target.toFile().setExecutable(true)) {
            throw new IOException("Cannot make buf executable: " + target);
        }
        return target;
    }

    private void report(List<Problem> problems) throws MojoFailureException {
        if (problems.isEmpty()) {
            getLog().info("buf found no problems");
            return;
        }
        for (Problem problem : problems) {
            if (failOnError) {
                getLog().error(problem.toString());
            } else {
                getLog().warn(problem.toString());
            }
            if (githubAnnotations) {
                // workflow commands must start the line, so the Maven log prefix cannot be used
                System.out.println(toWorkflowCommand(failOnError ? "error" : "warning", problem));
            }
        }
        String summary = "buf found " + problems.size() + " problem(s)";
        if (failOnError) {
            throw new MojoFailureException(summary);
        }
        getLog().warn(summary);
    }

    String toWorkflowCommand(String level, Problem problem) {
        Path file = projectDirectory.toPath().resolve(problem.getFile()).normalize();
        if (githubWorkspace != null) {
            Path workspace = githubWorkspace.toPath().normalize();
            if (file.startsWith(workspace)) {
                file = workspace.relativize(file);
            }
        }
        return "::" + level
                + " file=" + escapeProperty(file.toString().replace('\\', '/'))
                + ",line=" + problem.getLine()
                + ",col=" + problem.getColumn()
                + ",title=" + escapeProperty("buf [" + problem.getRule() + "]")
                + "::" + escapeData(problem.getMessage());
    }

    private static String escapeData(String text) {
        return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A");
    }

    private static String escapeProperty(String text) {
        return escapeData(text).replace(":", "%3A").replace(",", "%2C");
    }
}
