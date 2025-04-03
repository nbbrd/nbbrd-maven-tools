package nbbrd.compatibility.spi;

import lombok.NonNull;
import nbbrd.service.ServiceDefinition;
import nbbrd.service.ServiceFilter;
import nbbrd.service.ServiceId;

import java.io.IOException;

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
}
