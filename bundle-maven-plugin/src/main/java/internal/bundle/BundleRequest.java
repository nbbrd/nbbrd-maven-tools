package internal.bundle;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

@Value
@lombok.Builder(toBuilder = true)
public class BundleRequest {
    @NonNull Path application;

    @NonNull String baseDirectory;

    @NonNull String launcher;

    @Nullable String appName;

    @NonNull Instant timestamp;

    @Singular
    List<RuntimeBundle> runtimes;

    @Value
    @lombok.Builder(toBuilder = true)
    public static class RuntimeBundle {
        @NonNull String id;

        @NonNull Path archive;

        @NonNull Path output;

        @Nullable String javaHome;

        @Nullable String format;
    }
}
