package nbbrd.compatibility.spi;

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
    Build getBuild(@NonNull Consumer<? super String> onEvent) throws IOException;

    Consumer<? super String> IGNORE_EVENT = ignore -> {
    };
}
