package internal.buf;

import lombok.NonNull;
import lombok.Value;

/** A problem reported by buf or by a custom rule. */
@Value
@lombok.Builder
public class Problem {

    @NonNull String file;

    int line;

    int column;

    @NonNull String rule;

    @NonNull String message;

    @Override
    public String toString() {
        return file + ":" + line + ":" + column + ": " + message + " [" + rule + "]";
    }
}
