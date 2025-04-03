package nbbrd.compatibility;

import java.net.URI;

@lombok.Value
@lombok.Builder
public class Target {

    @lombok.NonNull
    URI uri;

    @lombok.Builder.Default
    String property = "";

    @lombok.NonNull
    @lombok.Builder.Default
    Building building = Building.DEFAULT;
}
