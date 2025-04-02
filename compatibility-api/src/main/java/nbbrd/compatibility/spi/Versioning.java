package nbbrd.compatibility.spi;

import lombok.NonNull;
import nbbrd.compatibility.Version;
import nbbrd.service.Quantifier;
import nbbrd.service.ServiceDefinition;
import nbbrd.service.ServiceId;

import java.util.Comparator;

@ServiceDefinition(
        quantifier = Quantifier.MULTIPLE
)
public interface Versioning {

    @ServiceId(pattern = ServiceId.KEBAB_CASE)
    @NonNull
    String getVersioningId();

    @NonNull
    String getVersioningName();

    boolean isValidVersion(@NonNull Version version);

    @NonNull
    Comparator<Version> getVersionComparator();
}
