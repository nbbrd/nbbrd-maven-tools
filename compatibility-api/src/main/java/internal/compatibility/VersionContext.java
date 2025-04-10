package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Tag;
import nbbrd.compatibility.Version;
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
    public static VersionContext local(Version version) {
        return VersionContext
                .builder()
                .tag(Tag.NO_TAG)
                .version(version)
                .build();
    }

    @StaticFactoryMethod
    public static VersionContext remote(Tag tag, Version version) {
        return VersionContext
                .builder()
                .tag(tag)
                .version(version)
                .build();
    }
}
