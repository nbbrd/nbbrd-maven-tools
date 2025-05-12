package nbbrd.compatibility;

import java.net.URI;

@lombok.Value
@lombok.Builder
public class Target implements Project {

    @lombok.NonNull
    URI uri;

    @lombok.NonNull
    @lombok.Builder.Default
    Filter filter = Filter.DEFAULT;
}
