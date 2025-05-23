package nbbrd.compatibility;

import lombok.NonNull;
import nbbrd.design.RepresentableAsString;
import nbbrd.design.StaticFactoryMethod;

import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * Pattern: groupId:artifactId:type:classifier:version
 */
@RepresentableAsString
@lombok.Value
@lombok.Builder
public class Artifact {

    @NonNull
    @lombok.Builder.Default
    String groupId = "";

    @NonNull
    @lombok.Builder.Default
    String artifactId = "";

    @NonNull
    @lombok.Builder.Default
    String type = "";

    @NonNull
    @lombok.Builder.Default
    String classifier = "";

    @NonNull
    @lombok.Builder.Default
    String version = "";

    @StaticFactoryMethod
    public static @NonNull Artifact parse(@NonNull CharSequence text) {
        if (text.length() == 0) {
            throw new IllegalArgumentException("Empty Artifact");
        }
        String[] items = text.toString().split(":", -1);
        switch (items.length) {
            case 1:
                return new Artifact(items[0], "", "", "", "");
            case 2:
                return new Artifact(items[0], items[1], "", "", "");
            case 3:
                return new Artifact(items[0], items[1], items[2], "", "");
            case 4:
                return new Artifact(items[0], items[1], items[2], items[3], "");
            case 5:
                return new Artifact(items[0], items[1], items[2], items[3], items[4]);
            default:
                throw new IllegalArgumentException("Invalid Artifact: '" + text + "'");
        }
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + type + ":" + classifier + ":" + version;
    }

    public Predicate<Artifact> toFilter() {
        Predicate<String> groupIdFilter = getFilter(groupId);
        Predicate<String> artifactIdFilter = getFilter(artifactId);
        Predicate<String> typeFilter = getFilter(type);
        Predicate<String> classifierFilter = getFilter(classifier);
        Predicate<String> versionFilter = getFilter(version);
        return artifact ->
                groupIdFilter.test(artifact.getGroupId())
                        && artifactIdFilter.test(artifact.getArtifactId())
                        && typeFilter.test(artifact.getType())
                        && classifierFilter.test(artifact.getClassifier())
                        && versionFilter.test(artifact.getVersion());
    }

    private static Predicate<String> getFilter(String pattern) {
        if (pattern.isEmpty() || pattern.equals("*")) return ignore -> true;
        if (!pattern.contains("*")) return value -> value.equals(pattern);
        String regex = Stream.of(pattern.split(Pattern.quote("*"), -1))
                .map(Pattern::quote)
                .collect(joining(".*", "^", "$"));
        return Pattern.compile(regex).asPredicate();
    }
}
