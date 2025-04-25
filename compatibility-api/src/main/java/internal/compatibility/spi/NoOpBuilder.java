package internal.compatibility.spi;

import lombok.NonNull;
import nbbrd.compatibility.spi.Builder;
import nbbrd.compatibility.spi.Build;

import java.io.IOException;
import java.util.function.Consumer;

public enum NoOpBuilder implements Builder {

    INSTANCE;

    @Override
    public @NonNull String getBuilderId() {
        return "no-op";
    }

    @Override
    public @NonNull String getBuilderName() {
        return "No operation";
    }

    @Override
    public boolean isBuilderAvailable() {
        return false;
    }

    @Override
    public @NonNull Build getBuild(@NonNull Consumer<? super String> onEvent) throws IOException {
        throw new IOException(getBuilderName());
    }
}
