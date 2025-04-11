package nbbrd.compatibility;

import lombok.NonNull;

import java.io.IOException;
import java.io.Reader;

@FunctionalInterface
public interface Parser<T> {

    /**
     * Parses the given reader into an object of type T.
     *
     * @param reader the reader to parse
     * @return the parsed object
     * @throws IOException if an I/O error occurs
     */
    @NonNull
    T parse(@NonNull Reader reader) throws IOException;
}
