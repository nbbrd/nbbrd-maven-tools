package nbbrd.compatibility;

import java.net.URI;

@lombok.Value
@lombok.Builder
public class Target {

    @lombok.NonNull
    URI uri;

    @lombok.NonNull
    @lombok.Builder.Default
    String property = NO_PROPERTY;

    @lombok.NonNull
    @lombok.Builder.Default
    Building building = Building.DEFAULT;

    @lombok.NonNull
    @lombok.Builder.Default
    Filter filter = Filter.DEFAULT;

    public static final String NO_PROPERTY = "";
}
