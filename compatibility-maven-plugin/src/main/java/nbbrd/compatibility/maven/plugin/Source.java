package nbbrd.compatibility.maven.plugin;

import java.net.URI;

@lombok.Data
public class Source implements MutableRepresentationOf<nbbrd.compatibility.Source> {

    private URI uri;
    private Tag tag;

    @Override
    public nbbrd.compatibility.Source toValue() {
        return nbbrd.compatibility.Source
                .builder()
                .uri(uri)
                .versioning(tag != null ? tag.getVersioning() : "")
                .build();
    }
}
