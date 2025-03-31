package nbbrd.compatibility.maven.plugin;

@lombok.Data
public class Tag implements MutableRepresentationOf<nbbrd.compatibility.Tag> {

    private String versioning;
    private String prefix;

    @Override
    public nbbrd.compatibility.Tag toValue() {
        return nbbrd.compatibility.Tag
                .builder()
                .versioning(versioning)
                .prefix(prefix)
                .build();
    }
}
