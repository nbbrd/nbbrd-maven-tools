package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Versioning;

import java.util.Comparator;

public enum NoOpVersioning implements Versioning {

    INSTANCE;

    @Override
    public @NonNull String getVersioningId() {
        return "no-op";
    }

    @Override
    public @NonNull String getVersioningName() {
        return "No operation";
    }

    @Override
    public boolean isValidVersion(@NonNull Version version) {
        return true;
    }

    @Override
    public @NonNull Comparator<Version> getVersionComparator() {
        return Comparator.comparing(Version::toString);
    }
}
