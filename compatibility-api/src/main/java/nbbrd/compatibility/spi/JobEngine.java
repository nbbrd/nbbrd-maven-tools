package nbbrd.compatibility.spi;

import lombok.NonNull;
import nbbrd.service.ServiceDefinition;
import nbbrd.service.ServiceFilter;
import nbbrd.service.ServiceId;

import java.io.IOException;

@ServiceDefinition
public interface JobEngine {

    @ServiceId(pattern = ServiceId.KEBAB_CASE)
    @NonNull
    String getJobEngineId();

    @NonNull
    String getJobEngineName();

    @ServiceFilter
    boolean isJobEngineAvailable();

    @NonNull
    JobExecutor getExecutor() throws IOException;
}
