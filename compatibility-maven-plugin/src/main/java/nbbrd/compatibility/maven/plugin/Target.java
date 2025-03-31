package nbbrd.compatibility.maven.plugin;

import java.net.URI;
import java.util.Optional;

@lombok.Data
public class Target implements MutableRepresentationOf<nbbrd.compatibility.Target> {

    private URI uri;
    private Tag tag;
    private Mvn mvn;

    @Override
    public nbbrd.compatibility.Target toValue() {
        return nbbrd.compatibility.Target
                .builder()
                .uri(uri)
                .tag(Optional.ofNullable(tag).map(Tag::toValue).orElse(nbbrd.compatibility.Tag.DEFAULT))
                .mvn(Optional.ofNullable(mvn).map(Mvn::toValue).orElse(nbbrd.compatibility.Mvn.DEFAULT))
                .build();
    }
}
