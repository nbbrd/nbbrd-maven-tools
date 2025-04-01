package nbbrd.compatibility.spi;

import lombok.NonNull;
import nbbrd.compatibility.Job;
import nbbrd.service.Quantifier;
import nbbrd.service.ServiceDefinition;
import nbbrd.service.ServiceId;

import java.io.IOException;

@ServiceDefinition(
        quantifier = Quantifier.MULTIPLE
)
public interface Format {

    @ServiceId(pattern = ServiceId.KEBAB_CASE)
    @NonNull
    String getFormatId();

    @NonNull
    String getFormatName();

    void formatJob(@NonNull Appendable appendable, @NonNull Job job) throws IOException;
}
