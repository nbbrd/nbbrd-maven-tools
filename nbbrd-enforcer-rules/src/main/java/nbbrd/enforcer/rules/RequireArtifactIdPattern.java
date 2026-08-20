package nbbrd.enforcer.rules;

import lombok.NonNull;
import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Ensures that the {@code artifactId} matches a regular expression, optionally
 * restricted to a set of packagings.
 * <p>
 * When {@link #packagings} is empty, the rule applies to every packaging.
 * Otherwise it only applies when the project packaging is one of the listed
 * values (other packagings pass silently). The {@code artifactId} must fully
 * match {@link #pattern}.
 */
@Named("requireArtifactIdPattern")
public final class RequireArtifactIdPattern extends AbstractEnforcerRule {

    /**
     * Packagings this rule applies to. When {@code null} or empty, the rule
     * applies to every packaging.
     */
    private @Nullable List<String> packagings;

    /**
     * Regular expression the {@code artifactId} must fully match.
     */
    private @Nullable String pattern;

    /**
     * Optional custom failure message.
     */
    private @Nullable String message;

    private final MavenProject project;

    @Inject
    public RequireArtifactIdPattern(@NonNull MavenProject project) {
        this.project = project;
    }

    // package-private constructor for testing (fields are otherwise populated by the enforcer configurator)
    RequireArtifactIdPattern(@NonNull MavenProject project, @Nullable List<String> packagings,
                             @Nullable String pattern, @Nullable String message) {
        this.project = project;
        this.packagings = packagings;
        this.pattern = pattern;
        this.message = message;
    }

    @Override
    public void execute() throws EnforcerRuleException {
        if (pattern == null || pattern.isEmpty()) {
            throw new EnforcerRuleException("Missing required 'pattern' parameter for requireArtifactIdPattern.");
        }
        if (!appliesToPackaging()) {
            return;
        }
        Pattern compiledPattern = compilePattern();
        String artifactId = project.getArtifactId();
        if (!compiledPattern.matcher(artifactId).matches()) {
            throw new EnforcerRuleException(message != null ? message
                    : "Project artifactId '" + artifactId + "' must match pattern '" + pattern
                    + "' for packaging '" + project.getPackaging() + "'.");
        }
    }

    private boolean appliesToPackaging() {
        return packagings == null || packagings.isEmpty() || packagings.contains(project.getPackaging());
    }

    private Pattern compilePattern() throws EnforcerRuleException {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            throw new EnforcerRuleException("Invalid 'pattern' parameter for requireArtifactIdPattern: " + ex.getMessage());
        }
    }

    @Override
    public String toString() {
        return "RequireArtifactIdPattern[packagings=" + packagings + ", pattern=" + pattern + "]";
    }
}







