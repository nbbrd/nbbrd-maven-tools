package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.RefVersion;
import nbbrd.compatibility.spi.Versioning;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

@lombok.Value
@lombok.Builder
public class SourceContext implements ProjectContext {

    @NonNull
    URI uri;

    @NonNull
    Path directory;

    @lombok.Singular
    List<RefVersion> versions;

    @NonNull
    Versioning versioning;

    @NonNull
    Broker broker;

    public static final class Builder implements ProjectContext.Builder<Builder> {
    }
}
