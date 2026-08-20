package nbbrd.enforcer.rules;

import lombok.NonNull;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Ensures that a javadoc jar is attached for every non-pom artifact.
 * <p>
 * Maven Central requires a {@code -javadoc.jar} to be published for non-pom
 * artifacts. This rule inspects the attached artifacts of the project and fails
 * when no artifact with the {@code javadoc} classifier is present.
 */
@Named("requireJavadocJar")
public final class RequireJavadocJar extends AbstractEnforcerRule {

    private static final String POM_PACKAGING = "pom";
    private static final String JAVADOC_CLASSIFIER = "javadoc";

    private final MavenProject project;

    @Inject
    public RequireJavadocJar(@NonNull MavenProject project) {
        this.project = project;
    }

    @Override
    public void execute() throws EnforcerRuleException {
        if (POM_PACKAGING.equals(project.getPackaging())) {
            return;
        }
        if (!hasAttachedJavadocJar()) {
            throw new EnforcerRuleException("Missing javadoc jar for " + project.getArtifactId()
                    + "; Maven Central requires a -javadoc.jar for non-pom artifacts.");
        }
    }

    private boolean hasAttachedJavadocJar() {
        for (Artifact artifact : project.getAttachedArtifacts()) {
            if (JAVADOC_CLASSIFIER.equals(artifact.getClassifier())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "RequireJavadocJar[]";
    }
}

