package nbbrd.compatibility;

import java.net.URI;

@lombok.Value
@lombok.Builder
public class Source {

    @lombok.NonNull
    URI uri;

    @lombok.Builder.Default
    String versioning = DEFAULT_VERSIONING;

    @lombok.NonNull
    @lombok.Builder.Default
    Filter filter = Filter.DEFAULT;

    public static final String DEFAULT_VERSIONING = "semver";
}
