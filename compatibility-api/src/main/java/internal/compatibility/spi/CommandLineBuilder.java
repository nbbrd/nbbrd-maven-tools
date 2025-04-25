package internal.compatibility.spi;

import lombok.NonNull;
import nbbrd.compatibility.spi.Build;
import nbbrd.compatibility.spi.Builder;
import nbbrd.design.DirectImpl;
import nbbrd.service.ServiceProvider;

import java.io.IOException;
import java.util.function.Consumer;

@DirectImpl
@ServiceProvider
public final class CommandLineBuilder implements Builder {

    @Override
    public @NonNull String getBuilderId() {
        return "command-line";
    }

    @Override
    public @NonNull String getBuilderName() {
        return "Command Line";
    }

    @Override
    public boolean isBuilderAvailable() {
        return true;
    }

    @Override
    public @NonNull Build getBuild(@NonNull Consumer<? super String> onEvent) {
        return CommandLineBuild.builder().onEvent(onEvent).build();
    }
}
