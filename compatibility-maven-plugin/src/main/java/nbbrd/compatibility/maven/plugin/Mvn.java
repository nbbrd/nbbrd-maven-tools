package nbbrd.compatibility.maven.plugin;

@lombok.Data
public class Mvn implements MutableRepresentationOf<nbbrd.compatibility.Mvn> {

    private String property;
    private String parameters;
    private String jdk;

    @Override
    public nbbrd.compatibility.Mvn toValue() {
        return nbbrd.compatibility.Mvn
                .builder()
                .property(property)
                .parameters(parameters)
                .jdk(jdk)
                .build();
    }
}
