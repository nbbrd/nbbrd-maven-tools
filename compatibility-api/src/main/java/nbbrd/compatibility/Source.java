package nbbrd.compatibility;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.net.URI;

@lombok.Value
@lombok.Builder
public class Source implements Project {

    @lombok.NonNull
    URI uri;

    @Nullable
    String binding;

    @lombok.Builder.Default
    String versioning = DEFAULT_VERSIONING;

    @lombok.NonNull
    @lombok.Builder.Default
    Filter filter = Filter.DEFAULT;

    public static final String DEFAULT_VERSIONING = "semver";
}
