package nbbrd.compatibility;

import java.net.URI;

@lombok.Value
@lombok.Builder
public class Target {

    @lombok.NonNull
    URI uri;

    @lombok.NonNull
    @lombok.Builder.Default
    Tag tag = Tag.DEFAULT;

    @lombok.NonNull
    @lombok.Builder.Default
    Mvn mvn = Mvn.DEFAULT;
}
