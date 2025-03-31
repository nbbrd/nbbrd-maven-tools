package nbbrd.compatibility;

@lombok.Value
@lombok.Builder
public class Mvn {

    public static final Mvn DEFAULT = Mvn.builder().build();

    @lombok.Builder.Default
    String property = "";

    @lombok.Builder.Default
    String parameters = "";

    @lombok.Builder.Default
    String jdk = "";
}
