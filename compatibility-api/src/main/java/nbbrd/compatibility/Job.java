package nbbrd.compatibility;

import java.util.List;

@lombok.Value
@lombok.Builder
public class Job {

    public static final Job EMPTY = Job.builder().build();

    @lombok.Singular
    List<Source> sources;

    @lombok.Singular
    List<Target> targets;
}
