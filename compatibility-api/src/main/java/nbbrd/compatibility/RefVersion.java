package nbbrd.compatibility;

import lombok.NonNull;
import nbbrd.design.StaticFactoryMethod;

@lombok.Value
@lombok.Builder
public class RefVersion {

    @NonNull
    Ref ref;

    @NonNull
    Version version;

    public boolean requiresCheckout() {
        return !ref.equals(Ref.NO_REF);
    }

    @StaticFactoryMethod
    public static @NonNull RefVersion local(@NonNull Version version) {
        return RefVersion
                .builder()
                .ref(Ref.NO_REF)
                .version(version)
                .build();
    }

    @StaticFactoryMethod
    public static @NonNull RefVersion localOf(@NonNull CharSequence version) {
        return local(Version.parse(version));
    }

    @StaticFactoryMethod
    public static @NonNull RefVersion remote(@NonNull Version version, @NonNull Ref ref) {
        return RefVersion
                .builder()
                .ref(ref)
                .version(version)
                .build();
    }

    @StaticFactoryMethod
    public static @NonNull RefVersion remoteOf(@NonNull CharSequence version) {
        return remote(Version.parse(version), Ref.ofVersion(version));
    }
}
