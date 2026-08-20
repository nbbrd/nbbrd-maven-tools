package nbbrd.enforcer.rules;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class RequireJavadocJarTest {

    @Test
    void testPomPackagingIsAlwaysValid() {
        MavenProject project = newProject("pom");

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
}

