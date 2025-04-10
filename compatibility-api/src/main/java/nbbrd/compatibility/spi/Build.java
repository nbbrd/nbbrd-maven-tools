package nbbrd.compatibility.spi;

import internal.compatibility.spi.LoggingBuild;
import internal.compatibility.spi.ResourceDefinition;
import lombok.NonNull;

import java.io.Closeable;
import java.util.function.Consumer;

@ResourceDefinition
public interface Build extends Closeable, Maven, Git {

    static @NonNull Build logging(@NonNull Consumer<? super String> onEvent, @NonNull Build build) {
        return new LoggingBuild(onEvent, build);
    }
}
