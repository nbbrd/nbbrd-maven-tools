package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Building;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

@lombok.Value
@lombok.Builder
public class TargetContext implements ProjectContext {

    @NonNull
    URI uri;

    @NonNull
    Path directory;

    boolean deleteOnExit;

    @lombok.Singular
    List<VersionContext> versions;

    @NonNull
    Broker broker;

    @NonNull
    Building building;

    public static final class Builder implements ProjectContext.Builder<Builder> {
    }
}
