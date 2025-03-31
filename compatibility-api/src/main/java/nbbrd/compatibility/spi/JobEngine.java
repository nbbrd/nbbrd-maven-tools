package nbbrd.compatibility.spi;

import lombok.NonNull;
import nbbrd.service.ServiceDefinition;
import nbbrd.service.ServiceFilter;
import nbbrd.service.ServiceId;

import java.io.IOException;

@ServiceDefinition
public interface JobEngine {

    @ServiceId(pattern = ServiceId.SNAKE_CASE)
    @NonNull
    String getId();

    @ServiceFilter
    boolean isAvailable();

    @NonNull
    JobExecutor getExecutor() throws IOException;
}
