package nbbrd.enforcer.rules;

import lombok.NonNull;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Ensures that a javadoc jar is attached for every non-pom artifact.
 * <p>
 * Maven Central requires a {@code -javadoc.jar} to be published for non-pom
 * artifacts. This rule inspects the attached artifacts of the project and fails
 * when no artifact with the {@code javadoc} classifier is present.
 * <p>
 * Projects that are not deployed are exempted, since Maven Central only requires
 * a javadoc jar for published artifacts. Deployment is considered skipped when
 * the {@code maven.deploy.skip} property is {@code true} or when the
 * {@code maven-deploy-plugin} is configured with {@code <skip>true</skip>}.
 */
@Named("requireJavadocJar")
public final class RequireJavadocJar extends AbstractEnforcerRule {

    private static final String POM_PACKAGING = "pom";
    private static final String JAVADOC_CLASSIFIER = "javadoc";
    private static final String DEPLOY_SKIP_PROPERTY = "maven.deploy.skip";
    private static final String DEPLOY_PLUGIN_KEY = "org.apache.maven.plugins:maven-deploy-plugin";

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
        if (isDeploymentSkipped()) {
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

    private boolean isDeploymentSkipped() {
        if (Boolean.parseBoolean(project.getProperties().getProperty(DEPLOY_SKIP_PROPERTY))) {
            return true;
        }
        Plugin deployPlugin = project.getBuild() != null
                ? project.getBuild().getPluginsAsMap().get(DEPLOY_PLUGIN_KEY)
                : null;
        return deployPlugin != null && isSkip(deployPlugin.getConfiguration());
    }

    private static boolean isSkip(Object configuration) {
        if (configuration instanceof Xpp3Dom) {
            Xpp3Dom skip = ((Xpp3Dom) configuration).getChild("skip");
            return skip != null && Boolean.parseBoolean(skip.getValue());
        }
        return false;
    }

    @Override
    public String toString() {
        return "RequireJavadocJar[]";
    }
}

