package nbbrd.compatibility.spi;

import lombok.NonNull;
import nbbrd.service.Quantifier;
import nbbrd.service.ServiceDefinition;
import nbbrd.service.ServiceId;

@ServiceDefinition(
        quantifier = Quantifier.MULTIPLE
)
public interface Versioning {

    @ServiceId(pattern = ServiceId.KEBAB_CASE)
    @NonNull
    String getVersioningId();

    @NonNull
    String getVersioningName();

    boolean isValidVersion(@NonNull CharSequence text);

    boolean isOrdered(@NonNull String from, @NonNull String to);
}
