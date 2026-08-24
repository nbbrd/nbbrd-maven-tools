package nbbrd.enforcer.rules;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.Collections;

class RequireJavadocJarTest {

    @Test
    void testPomPackagingIsAlwaysValid() {
        MavenProject project = newProject("pom");

        assertThatCode(() -> new RequireJavadocJar(project).execute())
                .doesNotThrowAnyException();
    }

    @Test
    void testMavenArchetypePackagingIsValidWithoutJavadoc() {
        MavenProject project = newProject("maven-archetype");

        assertThatCode(() -> new RequireJavadocJar(project).execute())
                .doesNotThrowAnyException();
    }

    @Test
    void testNonPomWithJavadocIsValid() {
        MavenProject project = newProject("jar");
        project.addAttachedArtifact(newArtifact("javadoc"));

        assertThatCode(() -> new RequireJavadocJar(project).execute())
                .doesNotThrowAnyException();
    }

    @Test
    void testNonPomWithoutJavadocFails() {
        MavenProject project = newProject("jar");
        project.addAttachedArtifact(newArtifact("sources"));

        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> new RequireJavadocJar(project).execute())
                .withMessageContaining("Missing javadoc jar");
    }

    @Test
    void testNonPomWithoutJavadocIsValidWhenDeploySkippedByProperty() {
        MavenProject project = newProject("jar");
        project.getProperties().setProperty("maven.deploy.skip", "true");


        assertThatCode(() -> new RequireJavadocJar(project).execute())
                .doesNotThrowAnyException();
    }

    @Test
    void testNonPomWithoutJavadocIsValidWhenDeploySkippedByPluginConfig() {
        MavenProject project = newProject("jar");
        project.getModel().setBuild(buildWithDeploySkip());

        assertThatCode(() -> new RequireJavadocJar(project).execute())
                .doesNotThrowAnyException();
    }

    @Test
    void testCustomExemptedPackagingsExemptsMatchingPackaging() {
        MavenProject project = newProject("jar");
        RequireJavadocJar rule = new RequireJavadocJar(project);
        rule.setExemptedPackagings(Arrays.asList("pom", "jar"));

        assertThatCode(rule::execute)
                .doesNotThrowAnyException();
    }

    @Test
    void testCustomExemptedPackagingsFailsWhenMavenArchetypeNotListed() {
        MavenProject project = newProject("maven-archetype");
        RequireJavadocJar rule = new RequireJavadocJar(project);
        rule.setExemptedPackagings(Collections.singletonList("pom"));

        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(rule::execute)
                .withMessageContaining("Missing javadoc jar");
    }

    private static MavenProject newProject(String packaging) {
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("example");
        model.setVersion("1.0.0");
        model.setPackaging(packaging);
        return new MavenProject(model);
    }

    private static Artifact newArtifact(String classifier) {
        return new DefaultArtifact("com.example", "example", "1.0.0", "compile",
                "jar", classifier, new DefaultArtifactHandler("jar"));
    }

    private static Build buildWithDeploySkip() {
        Xpp3Dom skip = new Xpp3Dom("skip");
        skip.setValue("true");
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        configuration.addChild(skip);

        Plugin deployPlugin = new Plugin();
        deployPlugin.setGroupId("org.apache.maven.plugins");
        deployPlugin.setArtifactId("maven-deploy-plugin");
        deployPlugin.setConfiguration(configuration);

        Build build = new Build();
        build.addPlugin(deployPlugin);
        return build;
    }
}

