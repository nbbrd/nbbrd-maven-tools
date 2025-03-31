package nbbrd.compatibility.maven.plugin;

import java.net.URI;
import java.util.Optional;

@lombok.Data
public class Source implements MutableRepresentationOf<nbbrd.compatibility.Source> {

    private URI uri;
    private Tag tag;

    @Override
    public nbbrd.compatibility.Source toValue() {
        return nbbrd.compatibility.Source
                .builder()
                .uri(uri)
                .tag(Optional.ofNullable(tag).map(Tag::toValue).orElse(nbbrd.compatibility.Tag.DEFAULT))
                .build();
    }
}
