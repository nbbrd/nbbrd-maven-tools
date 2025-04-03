package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Building;

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
                .property(mvn.getProperty())
                .building(Optional.ofNullable(mvn).map(Mvn::toValue).orElse(Building.DEFAULT))
                .build();
    }
}
