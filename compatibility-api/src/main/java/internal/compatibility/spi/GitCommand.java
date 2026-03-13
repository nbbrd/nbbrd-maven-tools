package internal.compatibility.spi;

import lombok.NonNull;
import nbbrd.design.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@lombok.Value
@lombok.Builder(toBuilder = true)
class GitCommand implements TextCommand {

    @Nullable
    Path binary;

    @lombok.Builder.Default
    boolean quiet = false;

    @Nullable
    Path workingDir;

    @Nullable
    String command;

    @lombok.Singular
    List<String> parameters;

    @lombok.Singular
    Map<String, String> options;

    @Override
    public @NonNull List<String> toProcessCommand() {
        List<String> result = new ArrayList<>();
        result.add(binary != null ? binary.toString() : getDefaultBinary());
        if (workingDir != null) {
            result.add("-C");
            result.add(workingDir.toString());
        }
        result.add(command);
        result.addAll(parameters);
        if (quiet) result.add("-q");
        options.forEach((k, v) -> result.add(k + "=" + v));
        return result;
    }

    @VisibleForTesting
    static String getDefaultBinary() {
        return "git";
    }
}
