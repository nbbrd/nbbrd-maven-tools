package nbbrd.compatibility;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.net.URI;

@lombok.Value
@lombok.Builder
public class Target implements Project {

    @lombok.NonNull
    URI uri;

    @Nullable
    String property;

    @lombok.NonNull
    @lombok.Builder.Default
    Filter filter = Filter.DEFAULT;
}
