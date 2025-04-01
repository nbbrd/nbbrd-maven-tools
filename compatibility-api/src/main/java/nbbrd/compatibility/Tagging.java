package nbbrd.compatibility;

@lombok.Value
@lombok.Builder
public class Tagging {

    public static final Tagging DEFAULT = Tagging.builder().build();

    @lombok.Builder.Default
    String versioning = "";

    @lombok.Builder.Default
    String prefix = "";
}
