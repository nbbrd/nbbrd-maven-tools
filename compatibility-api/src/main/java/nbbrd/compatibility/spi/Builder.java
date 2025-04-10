package nbbrd.compatibility.spi;

import internal.compatibility.spi.LoggingBuilder;
import lombok.NonNull;
import nbbrd.service.ServiceDefinition;
import nbbrd.service.ServiceFilter;
import nbbrd.service.ServiceId;

import java.io.IOException;
import java.util.function.Consumer;

@ServiceDefinition
public interface Builder {

    @ServiceId(pattern = ServiceId.KEBAB_CASE)
    @NonNull
    String getBuilderId();

    @NonNull
    String getBuilderName();

    @ServiceFilter
    boolean isBuilderAvailable();

    @NonNull
    Build getBuild() throws IOException;

    static @NonNull Builder logging(@NonNull Consumer<? super String> onEvent, @NonNull Builder builder) {
        return new LoggingBuilder(onEvent, builder);
    }
}
