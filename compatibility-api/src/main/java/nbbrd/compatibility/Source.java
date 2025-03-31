package nbbrd.compatibility;

import java.net.URI;

@lombok.Value
@lombok.Builder
public class Source {

    @lombok.NonNull
    URI uri;

    @lombok.NonNull
    @lombok.Builder.Default
    Tag tag = Tag.DEFAULT;
}
