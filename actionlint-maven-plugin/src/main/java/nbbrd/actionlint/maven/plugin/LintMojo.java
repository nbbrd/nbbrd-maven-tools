package nbbrd.actionlint.maven.plugin;

import internal.actionlint.Actionlint;
import internal.actionlint.HttpProxy;
import internal.actionlint.Installer;
import internal.actionlint.Platform;
import internal.actionlint.Problem;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.jspecify.annotations.Nullable;

/** Lint GitHub Actions workflow files with actionlint. */
@Mojo(name = "lint", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true, requiresProject = false)
public final class LintMojo extends AbstractMojo {

    static final String WORKFLOWS = ".github/workflows";

    /** Default actionlint version; its checksums must be pinned in the plugin resources. */
    static final String DEFAULT_VERSION = "1.7.12";

    /** actionlint version to download. */
    @Parameter(property = "actionlint.version", defaultValue = DEFAULT_VERSION)
    String version;

    /** Local actionlint executable; disables downloading when set. */
    @Parameter(property = "actionlint.executable")
    File executable;

    /** Base URL of actionlint releases; the version tag and archive name are appended. */
    @Parameter(property = "actionlint.baseUrl", defaultValue = "https://github.com/rhysd/actionlint/releases/download")
    String baseUrl;

    /** Directory where downloaded archives are cached; archives are verified on each use. */
    @Parameter(property = "actionlint.cacheDirectory", defaultValue = "${settings.localRepository}/.cache/actionlint")
    File cacheDirectory;

    /** Directory where the executable is extracted from the cached archive; the cache directory without project. */
    @Parameter(defaultValue = "${project.build.directory}/actionlint", readonly = true, required = true)
    File installDirectory;

    /** Base directory for workflow discovery and relative paths; the current directory without project. */
    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    File projectDirectory;

    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    File executionRootDirectory;

    /** Files to lint; defaults to YAML files in .github/workflows. */
    @Parameter
    List<File> files;

    /** actionlint configuration file. */
    @Parameter(property = "actionlint.configFile")
    File configFile;

    /** Regular expressions of error messages to ignore. */
    @Parameter
    List<String> ignores;

    /** shellcheck executable for run scripts; actionlint uses the one on PATH by default. */
    @Parameter(property = "actionlint.shellcheck")
    String shellcheck;

    /** pyflakes executable for Python scripts; actionlint uses the one on PATH by default. */
    @Parameter(property = "actionlint.pyflakes")
    String pyflakes;

    /** Fail the build when problems are found. */
    @Parameter(property = "actionlint.failOnError", defaultValue = "true")
    boolean failOnError;

    /** Print GitHub Actions workflow commands so that problems are annotated on pull requests. */
    @Parameter(property = "actionlint.githubAnnotations", defaultValue = "${env.GITHUB_ACTIONS}")
    boolean githubAnnotations;

    @Parameter(defaultValue = "${env.GITHUB_WORKSPACE}", readonly = true)
    File githubWorkspace;

    @Parameter(defaultValue = "${settings}", readonly = true)
    Settings settings;

    @Parameter(defaultValue = "${settings.offline}", readonly = true)
    boolean offline;

    /** Skip linting. */
    @Parameter(property = "actionlint.skip", defaultValue = "false")
    boolean skip;

    /** Lint only in the top-level project of a multi-module build; other modules are skipped. */
    @Parameter(property = "actionlint.runOnlyAtRoot", defaultValue = "true")
    boolean runOnlyAtRoot;

    @Parameter(defaultValue = "${session.topLevelProject.basedir}", readonly = true)
    File topLevelDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("actionlint has been skipped.");
            return;
        }
        if (runOnlyAtRoot && !isTopLevelProject()) {
            getLog().debug("actionlint runs only in the top-level project");
            return;
        }
        if (projectDirectory == null) {
            // standalone execution, without pom.xml: project paths are unresolved
            projectDirectory = executionRootDirectory;
            installDirectory = cacheDirectory;
        }
        try {
            List<String> targets = selectFiles();
            if (targets.isEmpty()) {
                getLog().info("No workflow files found in "
                        + projectDirectory.toPath().resolve(WORKFLOWS));
                return;
            }
            getLog().info("Linting " + targets.size() + " workflow file(s) with actionlint");
            List<Problem> problems = Actionlint.builder()
                    .executable(resolveExecutable())
                    .workingDirectory(projectDirectory.toPath())
                    .files(targets)
                    .ignores(ignores != null ? ignores : Collections.emptyList())
                    .configFile(configFile != null ? configFile.toPath() : null)
                    .shellcheck(shellcheck)
                    .pyflakes(pyflakes)
                    .build()
                    .run();
            report(problems);
        } catch (IllegalArgumentException ex) {
            throw new MojoFailureException(ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoExecutionException("Cannot run actionlint: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("actionlint has been interrupted", ex);
        }
    }

    private boolean isTopLevelProject() {
        return topLevelDirectory == null
                || projectDirectory == null
                || topLevelDirectory
                        .toPath()
                        .toAbsolutePath()
                        .normalize()
                        .equals(projectDirectory.toPath().toAbsolutePath().normalize());
    }

    List<String> selectFiles() throws IOException {
        List<String> result = new ArrayList<>();
        if (files != null && !files.isEmpty()) {
            for (File file : files) {
                result.add(file.getPath());
            }
            return result;
        }
        Path directory = projectDirectory.toPath().resolve(WORKFLOWS);
        if (!Files.isDirectory(directory)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.{yml,yaml}")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    result.add(WORKFLOWS + "/" + file.getFileName());
                }
            }
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    private Path resolveExecutable() throws IOException {
        if (executable != null) {
            return executable.toPath();
        }
        if (version == null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw new IllegalArgumentException("Invalid actionlint version, expected x.y.z: " + version);
        }
        return Installer.builder()
                .version(version)
                .baseUrl(baseUrl)
                .cacheDirectory(cacheDirectory.toPath())
                .installDirectory(installDirectory.toPath())
                .platform(Platform.current())
                .offline(offline)
                .proxy(selectProxy())
                .onInfo(getLog()::info)
                .onWarn(getLog()::warn)
                .build()
                .install();
    }

    @Nullable HttpProxy selectProxy() {
        if (settings == null) {
            return null;
        }
        String scheme = URI.create(baseUrl).getScheme();
        Proxy result = null;
        for (Proxy candidate : settings.getProxies()) {
            if (!candidate.isActive()) {
                continue;
            }
            if (candidate.getProtocol().equalsIgnoreCase(scheme)) {
                result = candidate;
                break;
            }
            if (result == null && "http".equalsIgnoreCase(candidate.getProtocol())) {
                result = candidate;
            }
        }
        if (result == null) {
            return null;
        }
        if (result.getUsername() != null && !result.getUsername().isEmpty()) {
            getLog().warn("Proxy authentication is not supported; connecting to proxy '" + result.getId()
                    + "' without credentials");
        }
        HttpProxy.Builder builder = HttpProxy.builder().host(result.getHost()).port(result.getPort());
        if (result.getNonProxyHosts() != null) {
            for (String host : result.getNonProxyHosts().split("[|,]")) {
                if (!host.trim().isEmpty()) {
                    builder.nonProxyHost(host.trim());
                }
            }
        }
        return builder.build();
    }

    private void report(List<Problem> problems) throws MojoFailureException {
        if (problems.isEmpty()) {
            getLog().info("actionlint found no problems");
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
        String summary = "actionlint found " + problems.size() + " problem(s)";
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
                + ",title=" + escapeProperty("actionlint [" + problem.getKind() + "]")
                + "::" + escapeData(problem.getMessage());
    }

    private static String escapeData(String text) {
        return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A");
    }

    private static String escapeProperty(String text) {
        return escapeData(text).replace(":", "%3A").replace(",", "%2C");
    }
}
