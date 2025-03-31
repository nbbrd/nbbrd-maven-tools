package nbbrd.compatibility;

@lombok.Value
@lombok.Builder
public class Tag {

    public static final Tag DEFAULT = Tag.builder().build();

    @lombok.Builder.Default
    String versioning = "";

    @lombok.Builder.Default
    String prefix = "";
}
