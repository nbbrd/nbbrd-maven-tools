package internal.actionlint;

import lombok.NonNull;
import lombok.Value;

/** A problem reported by actionlint. */
@Value
@lombok.Builder
public class Problem {

    @NonNull String file;

    int line;

    int column;

    @NonNull String kind;

    @NonNull String message;

    @Override
    public String toString() {
        return file + ":" + line + ":" + column + ": " + message + " [" + kind + "]";
    }
}
