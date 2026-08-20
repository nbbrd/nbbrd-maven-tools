package nbbrd.enforcer.rules;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class RequireArtifactIdPatternTest {

    @Test
    void testMatchingPackagingAndMatchingArtifactIdIsValid() {
        MavenProject project = newProject("nbm", "example-desktop-plugin");

        assertThatCode(() -> newRule(project, asList("nbm"), "^.+-desktop-plugin$", null).execute())
                .doesNotThrowAnyException();
    }

    @Test
    void testMatchingPackagingAndNonMatchingArtifactIdFails() {
        MavenProject project = newProject("nbm", "example-plugin");

        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> newRule(project, asList("nbm"), "^.+-desktop-plugin$", null).execute())
                .withMessageContaining("example-plugin");
    }

    @Test
    void testCustomMessageIsUsedOnFailure() {
        MavenProject project = newProject("nbm", "example-plugin");

        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> newRule(project, asList("nbm"), "^.+-desktop-plugin$", "custom message").execute())
                .withMessage("custom message");
    }

    @Test
    void testNonMatchingPackagingIsValidRegardlessOfArtifactId() {
        MavenProject project = newProject("jar", "example-plugin");

        assertThatCode(() -> newRule(project, asList("nbm"), "^.+-desktop-plugin$", null).execute())
                .doesNotThrowAnyException();
    }

    @Test
    void testEmptyPackagingsAppliesToAll() {
        MavenProject project = newProject("jar", "bad-name");

        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> newRule(project, Collections.emptyList(), "^good-.+$", null).execute());
    }

    @Test
    void testNullPackagingsAppliesToAll() {
        MavenProject project = newProject("jar", "good-name");

        assertThatCode(() -> newRule(project, null, "^good-.+$", null).execute())
                .doesNotThrowAnyException();
    }

    @Test
    void testMissingPatternFails() {
        MavenProject project = newProject("jar", "example");

        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> newRule(project, null, null, null).execute())
                .withMessageContaining("Missing required 'pattern'");
    }

    @Test
    void testInvalidPatternFails() {
        MavenProject project = newProject("jar", "example");

        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> newRule(project, null, "[", null).execute())
                .withMessageContaining("Invalid 'pattern'");
    }

    @Test
    void testFullMatchIsRequired() {
        MavenProject project = newProject("jar", "prefix-desktop-plugin-suffix");

        assertThatExceptionOfType(EnforcerRuleException.class)
                .isThrownBy(() -> newRule(project, null, "^.+-desktop-plugin$", null).execute());
    }

    private static RequireArtifactIdPattern newRule(MavenProject project, List<String> packagings,
                                                    String pattern, String message) {
        return new RequireArtifactIdPattern(project, packagings, pattern, message);
    }

    private static MavenProject newProject(String packaging, String artifactId) {
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId(artifactId);
        model.setVersion("1.0.0");
        model.setPackaging(packaging);
        return new MavenProject(model);
    }
}

