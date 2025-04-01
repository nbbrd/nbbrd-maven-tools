package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Tagging;

@lombok.Data
public class Tag implements MutableRepresentationOf<Tagging> {

    private String versioning;
    private String prefix;

    @Override
    public Tagging toValue() {
        return Tagging
                .builder()
                .versioning(versioning)
                .prefix(prefix)
                .build();
    }
}
