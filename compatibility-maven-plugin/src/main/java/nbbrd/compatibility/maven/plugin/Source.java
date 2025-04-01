package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Tagging;

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
                .tagging(Optional.ofNullable(tag).map(Tag::toValue).orElse(Tagging.DEFAULT))
                .build();
    }
}
