package nbbrd.enforcer.rules;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.Collections;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

class RequireVersionPropertyNamingTest {

    @Test
    void testValidDependencyAndPluginProperties() {
        Model model = new Model();
        model.setDependencies(Collections.singletonList(newDependency("dep.gson.version")));
        Build build = new Build();
        build.setPlugins(Collections.singletonList(newPlugin("dep.plugin.compiler.version")));
        model.setBuild(build);

        assertThatCode(() -> newRule(model).execute()).doesNotThrowAnyException();
    }

    @Test
    void testInvalidDependencyAndPluginPropertiesAreReportedTogether() {
        Model model = new Model();
        model.setDependencies(Arrays.asList(newDependency("project.version"), newDependency("dep.gson")));
        Build build = new Build();
        build.setPluginManagement(new PluginManagement());
        build.getPluginManagement().setPlugins(Collections.singletonList(newPlugin("dep.compiler.version")));
        model.setBuild(build);

        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> newRule(model).execute())
                .withMessageContaining("com.example:example-dependency")
                .withMessageContaining("project.version")
                .withMessageContaining("dep.gson")
                .withMessageContaining("org.example:example-plugin")
                .withMessageContaining("dep.compiler.version");
    }

    @Test
    void testUnversionedDeclarationsAreIgnored() {
        Model model = new Model();
        model.setDependencies(Collections.singletonList(new Dependency()));
        Build build = new Build();
        build.setPlugins(Collections.singletonList(new Plugin()));
        model.setBuild(build);

        assertThatCode(() -> newRule(model).execute()).doesNotThrowAnyException();
    }

    @Test
    void testProfileDependenciesAndReportingPluginsAreChecked() {
        Model model = new Model();
        Profile profile = new Profile();
        profile.setId("inactive-profile");
        profile.setDependencies(Collections.singletonList(newDependency("1.2.3")));
        Reporting reporting = new Reporting();
        ReportPlugin plugin = new ReportPlugin();
        plugin.setGroupId("org.example");
        plugin.setArtifactId("example-report-plugin");
        plugin.setVersion("${dep.example-report.version}");
        reporting.setPlugins(Collections.singletonList(plugin));
        profile.setReporting(reporting);
        model.setProfiles(Collections.singletonList(profile));

        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> newRule(model).execute())
                .withMessageContaining("profile 'inactive-profile'/dependencies")
                .withMessageContaining("1.2.3")
                .withMessageContaining("profile 'inactive-profile'/reporting")
                .withMessageContaining("dep.example-report.version");
    }

    @Test
    void testPluginDependenciesAndBuildExtensionsUseDependencyPrefix() {
        Model model = new Model();
        Build build = new Build();
        Plugin plugin = newPlugin("dep.plugin.example-plugin.version");
        plugin.setDependencies(Collections.singletonList(newDependency("dep.plugin.library.version")));
        build.setPlugins(Collections.singletonList(plugin));
        Extension extension = new Extension();
        extension.setGroupId("org.example");
        extension.setArtifactId("example-extension");
        extension.setVersion("${dep.plugin.example-extension.version}");
        build.setExtensions(Collections.singletonList(extension));
        model.setBuild(build);

        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> newRule(model).execute())
                .withMessageContaining("plugin org.example:example-plugin/dependencies")
                .withMessageContaining("dep.plugin.library.version")
                .withMessageContaining("extensions extension org.example:example-extension")
                .withMessageContaining("dep.plugin.example-extension.version");
    }

    @Test
    void testMissingOriginalModelFailsClearly() {
        MavenProject project = new MavenProject(new Model());

        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> new RequireVersionPropertyNaming(project).execute())
                .withMessageContaining("original POM model is unavailable");
    }

    private static RequireVersionPropertyNaming newRule(Model model) {
        MavenProject project = new MavenProject(model);
        project.setOriginalModel(model);
        return new RequireVersionPropertyNaming(project);
    }

    private static Dependency newDependency(String version) {
        Dependency dependency = new Dependency();
        dependency.setGroupId("com.example");
        dependency.setArtifactId("example-dependency");
        dependency.setVersion("${" + version + "}");
        return dependency;
    }

    private static Plugin newPlugin(String version) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.example");
        plugin.setArtifactId("example-plugin");
        plugin.setVersion("${" + version + "}");
        return plugin;
    }
}
