package nbbrd.compatibility;

import lombok.NonNull;
import nbbrd.design.StaticFactoryMethod;

@lombok.Value
@lombok.Builder
public class VersionContext {

    @NonNull
    Tag tag;

    @NonNull
    Version version;

    public boolean requiresCheckout() {
        return !tag.equals(Tag.NO_TAG);
    }

    @StaticFactoryMethod
    public static @NonNull VersionContext local(@NonNull Version version) {
        return VersionContext
                .builder()
                .tag(Tag.NO_TAG)
                .version(version)
                .build();
    }

    @StaticFactoryMethod
    public static @NonNull VersionContext localOf(@NonNull CharSequence version) {
        return local(Version.parse(version));
    }

    @StaticFactoryMethod
    public static @NonNull VersionContext remote(@NonNull Version version, @NonNull Tag tag) {
        return VersionContext
                .builder()
                .tag(tag)
                .version(version)
                .build();
    }

    @StaticFactoryMethod
    public static @NonNull VersionContext remoteOf(@NonNull CharSequence version) {
        return remote(Version.parse(version), Tag.ofVersion(version));
    }
}
