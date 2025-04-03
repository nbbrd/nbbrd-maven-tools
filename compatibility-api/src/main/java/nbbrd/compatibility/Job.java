package nbbrd.compatibility;

import nbbrd.io.sys.SystemProperties;

import java.nio.file.Path;
import java.util.List;

import static java.util.Objects.requireNonNull;

@lombok.Value
@lombok.Builder
public class Job {

    @lombok.Singular
    List<Source> sources;

    @lombok.Singular
    List<Target> targets;

    @lombok.NonNull
    @lombok.Builder.Default
    Path workingDir = requireNonNull(SystemProperties.DEFAULT.getJavaIoTmpdir());
}
