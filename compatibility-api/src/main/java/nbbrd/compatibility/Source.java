package nbbrd.compatibility;

import java.net.URI;

@lombok.Value
@lombok.Builder
public class Source {

    @lombok.NonNull
    URI uri;

    @lombok.Builder.Default
    String versioning = "";
}
