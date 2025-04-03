package nbbrd.compatibility.maven.plugin;

import nbbrd.compatibility.Building;

@lombok.Data
public class Mvn implements MutableRepresentationOf<Building> {

    private String property;
    private String parameters;
    private String jdk;

    @Override
    public Building toValue() {
        return Building
                .builder()
                .parameters(parameters)
                .jdk(jdk)
                .build();
    }
}
