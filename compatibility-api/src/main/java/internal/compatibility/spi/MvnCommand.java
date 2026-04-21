package internal.compatibility.spi;

import lombok.NonNull;
import nbbrd.design.VisibleForTesting;
import nbbrd.io.sys.OS;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@lombok.Value
@lombok.Builder(toBuilder = true)
class MvnCommand implements TextCommand {

    public enum FailStrategy {FAIL_AT_END, FAIL_FAST, FAIL_NEVER}

    @Nullable
    Path binary;

    @lombok.Builder.Default
    boolean quiet = false;

    @lombok.Builder.Default
    boolean batchMode = false;

    @lombok.Builder.Default
    boolean updateSnapshots = false;

    @Nullable
    Path file;

    @NonNull
    @lombok.Builder.Default
    FailStrategy failStrategy = FailStrategy.FAIL_FAST;

    @lombok.Singular
    List<String> goals;

    @lombok.Singular
    Map<String, String> properties;

    public @NonNull MvnCommand withProperty(@NonNull String key, @Nullable String value) {
        return toBuilder().property(key, value).build();
    }

    @Override
    public @NonNull List<String> toProcessCommand() {
        List<String> result = new ArrayList<>();
        result.add((binary != null ? binary : getDefaultBinary()).toString());
        if (quiet) result.add("-q");
        if (batchMode) result.add("-B");
        if (updateSnapshots) result.add("-U");
        if (file != null) {
            result.add("-f");
            result.add(file.toString());
        }
        switch (failStrategy) {
            case FAIL_AT_END:
                result.add("-fae");
                break;
            case FAIL_FAST:
                result.add("-ff");
                break;
            case FAIL_NEVER:
                result.add("-fn");
                break;
        }
        result.addAll(goals);
        properties.forEach((k, v) -> {
            result.add("-D");
            result.add(formatUserProperty(k, v));
        });
        return result;
    }

    private static String formatUserProperty(String key, String value) {
        return value != null
                ? "\"" + key + '=' + value + "\""
                : "\"" + key + "\"";
    }

    @VisibleForTesting
    static Path getDefaultBinary() {
        String mavenHome = System.getenv("MAVEN_HOME");
        Path binaryName = Paths.get(OS.NAME.equals(OS.Name.WINDOWS) ? "mvn.cmd" : "mvn");
        return mavenHome != null ? Paths.get(mavenHome).resolve("bin").resolve(binaryName) : binaryName;
    }
}
