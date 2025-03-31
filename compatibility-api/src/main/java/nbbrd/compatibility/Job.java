package nbbrd.compatibility;

import java.io.File;
import java.util.List;

@lombok.Value
@lombok.Builder
public class Job {

    @lombok.Singular
    List<Source> sources;

    @lombok.Singular
    List<Target> targets;

    File workingDir;

    String reportFilename;
}
