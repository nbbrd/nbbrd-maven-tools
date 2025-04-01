package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.spi.Versioning;

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
    public boolean isValidVersion(@NonNull CharSequence text) {
        return true;
    }

    @Override
    public boolean isOrdered(@NonNull String from, @NonNull String to) {
        return true;
    }
}
