package nbbrd.compatibility;

import java.nio.file.Path;
import java.util.List;

@lombok.Value
@lombok.Builder
public class Job {

    @lombok.Singular
    List<Source> sources;

    @lombok.Singular
    List<Target> targets;

    Path workingDir;
}
