package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.spi.Build;
import nbbrd.compatibility.spi.Builder;

import java.io.IOException;
import java.util.function.Consumer;

@lombok.RequiredArgsConstructor
public final class LoggingBuilder implements Builder {

    private final @NonNull Consumer<? super String> onEvent;
    private final @NonNull Builder delegate;

    @Override
    public @NonNull String getBuilderId() {
        return delegate.getBuilderId();
    }

    @Override
    public @NonNull String getBuilderName() {
        return delegate.getBuilderName();
    }

    @Override
    public boolean isBuilderAvailable() {
        return delegate.isBuilderAvailable();
    }

    @Override
    public @NonNull Build getBuild() throws IOException {
        return Build.logging(onEvent, delegate.getBuild());
    }
}
