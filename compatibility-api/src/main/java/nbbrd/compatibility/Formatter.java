package nbbrd.compatibility;

import lombok.NonNull;

import java.io.IOException;
import java.io.Writer;

@FunctionalInterface
public interface Formatter<T> {

    /**
     * Formats the given object into a string.
     *
     * @param value  the object to format
     * @param writer the writer to write the formatted string to
     * @throws IOException if an I/O error occurs
     */
    void format(@NonNull T value, @NonNull Writer writer) throws IOException;
}
