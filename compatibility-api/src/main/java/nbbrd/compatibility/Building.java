package nbbrd.compatibility;

@lombok.Value
@lombok.Builder
public class Building {

    public static final Building DEFAULT = Building.builder().build();

    @lombok.Builder.Default
    String property = "";

    @lombok.Builder.Default
    String parameters = "";

    @lombok.Builder.Default
    String jdk = "";
}
