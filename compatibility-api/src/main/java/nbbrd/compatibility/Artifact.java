package nbbrd.compatibility;

import lombok.NonNull;
import nbbrd.design.RepresentableAsString;
import nbbrd.design.StaticFactoryMethod;

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
}
