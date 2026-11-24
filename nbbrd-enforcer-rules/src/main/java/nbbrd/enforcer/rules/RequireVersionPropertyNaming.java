package nbbrd.enforcer.rules;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.NonNull;
import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * Ensures that explicit dependency and Maven plugin versions use the expected property prefixes and suffix.
 * <p>
 * Only versions declared in the original POM model are checked. Versions inherited from a parent or supplied by
 * dependency management in an imported BOM are not inspected; the import dependency's own declared version is
 * checked. All profiles are checked, whether or not they are active.
 */
@Named("requireVersionPropertyNaming")
public final class RequireVersionPropertyNaming extends AbstractEnforcerRule {

    private static final String DEPENDENCY_PREFIX = "dep.";
    private static final String PLUGIN_PREFIX = "dep.plugin.";
    private static final String VERSION_SUFFIX = ".version";

    private final MavenProject project;

    @Inject
    public RequireVersionPropertyNaming(@NonNull MavenProject project) {
        this.project = project;
    }

    @Override
    public void execute() throws EnforcerRuleException {
        Model model = project.getOriginalModel();
        if (model == null) {
            throw new EnforcerRuleException(
                    "Cannot enforce version property naming because the original POM model is unavailable for "
                            + project.getArtifactId() + ".");
        }
        List<String> violations = new ArrayList<>();

        checkDependencies(model.getDependencies(), "dependencies", violations);
        checkDependencyManagement(model.getDependencyManagement(), "dependencyManagement", violations);
        checkExtensions(model.getBuild(), "build", violations);
        checkBuild(model.getBuild(), "build", violations);
        checkReporting(model.getReporting(), "reporting", violations);
        for (Profile profile : model.getProfiles()) {
            String profilePath = "profile '" + profile.getId() + "'";
            checkDependencies(profile.getDependencies(), profilePath + "/dependencies", violations);
            checkDependencyManagement(
                    profile.getDependencyManagement(), profilePath + "/dependencyManagement", violations);
            checkBuild(profile.getBuild(), profilePath + "/build", violations);
            checkReporting(profile.getReporting(), profilePath + "/reporting", violations);
        }

        if (!violations.isEmpty()) {
            throw new EnforcerRuleException("Explicit dependency/plugin versions must use a property matching "
                    + "'${dep.<name>.version}' for dependencies or '${dep.plugin.<name>.version}' for plugins:"
                    + System.lineSeparator() + String.join(System.lineSeparator(), violations));
        }
    }

    private static void checkDependencies(List<Dependency> dependencies, String path, List<String> violations) {
        for (Dependency dependency : dependencies) {
            checkVersion(
                    dependency.getVersion(),
                    DEPENDENCY_PREFIX,
                    path + " dependency " + coordinates(dependency.getGroupId(), dependency.getArtifactId()),
                    violations);
        }
    }

    private static void checkDependencyManagement(
            @Nullable DependencyManagement management, String path, List<String> violations) {
        if (management != null) {
            checkDependencies(management.getDependencies(), path + "/dependencies", violations);
        }
    }

    private static void checkBuild(@Nullable BuildBase build, String path, List<String> violations) {
        if (build == null) {
            return;
        }
        checkPlugins(build.getPlugins(), path + "/plugins", violations);
        PluginManagement management = build.getPluginManagement();
        if (management != null) {
            checkPlugins(management.getPlugins(), path + "/pluginManagement/plugins", violations);
        }
    }

    private static void checkExtensions(@Nullable Build build, String path, List<String> violations) {
        if (build == null) {
            return;
        }
        for (Extension extension : build.getExtensions()) {
            checkVersion(
                    extension.getVersion(),
                    DEPENDENCY_PREFIX,
                    path + "/extensions extension " + coordinates(extension.getGroupId(), extension.getArtifactId()),
                    violations);
        }
    }

    private static void checkPlugins(List<Plugin> plugins, String path, List<String> violations) {
        for (Plugin plugin : plugins) {
            String pluginPath = path + " plugin " + coordinates(plugin.getGroupId(), plugin.getArtifactId());
            checkVersion(plugin.getVersion(), PLUGIN_PREFIX, pluginPath, violations);
            checkDependencies(plugin.getDependencies(), pluginPath + "/dependencies", violations);
        }
    }

    private static void checkReporting(@Nullable Reporting reporting, String path, List<String> violations) {
        if (reporting != null) {
            for (ReportPlugin plugin : reporting.getPlugins()) {
                checkVersion(
                        plugin.getVersion(),
                        PLUGIN_PREFIX,
                        path + " plugin " + coordinates(plugin.getGroupId(), plugin.getArtifactId()),
                        violations);
            }
        }
    }

    private static void checkVersion(@Nullable String version, String prefix, String path, List<String> violations) {
        if (version == null) {
            return;
        }
        String propertyName = extractPropertyName(version);
        if (propertyName == null
                || !hasExpectedPrefix(propertyName, prefix)
                || !propertyName.endsWith(VERSION_SUFFIX)
                || propertyName.length() <= prefix.length() + VERSION_SUFFIX.length()) {
            String expectedPrefix = DEPENDENCY_PREFIX.equals(prefix)
                    ? "'" + DEPENDENCY_PREFIX + "' but not '" + PLUGIN_PREFIX + "'"
                    : "'" + prefix + "'";
            violations.add(path + " has version '" + version + "'; expected a property starting with " + expectedPrefix
                    + " and ending with '" + VERSION_SUFFIX + "'.");
        }
    }

    private static boolean hasExpectedPrefix(String propertyName, String prefix) {
        return propertyName.startsWith(prefix)
                && !(DEPENDENCY_PREFIX.equals(prefix) && propertyName.startsWith(PLUGIN_PREFIX));
    }

    private static @Nullable String extractPropertyName(String version) {
        if (version.startsWith("${") && version.endsWith("}") && version.length() > 3) {
            String propertyName = version.substring(2, version.length() - 1);
            return propertyName.contains("{") || propertyName.contains("}") ? null : propertyName;
        }
        return null;
    }

    private static String coordinates(@Nullable String groupId, @Nullable String artifactId) {
        return (groupId != null ? groupId : "<unknown>") + ":" + (artifactId != null ? artifactId : "<unknown>");
    }

    @Override
    public String toString() {
        return "RequireVersionPropertyNaming";
    }
}
