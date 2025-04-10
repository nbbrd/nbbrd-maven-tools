package internal.compatibility;

import nbbrd.io.function.IOConsumer;
import nbbrd.io.function.IOFunction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.stream.Collectors.mapping;

public final class IOStreams {

    private IOStreams() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static <T, R, A> R collectWithIO(Collection<T> collection, Collector<? super T, A, R> collector) throws IOException {
        return collectWithIO(collection.stream(), collector);
    }

    public static <T> void forEachWithIO(Collection<T> collection, IOConsumer<? super T> action) throws IOException {
        forEachWithIO(collection.stream(), action);
    }

    public static <T, R, A> R collectWithIO(Stream<T> stream, Collector<? super T, A, R> collector) throws IOException {
        try {
            return stream.collect(collector);
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    public static <T> void forEachWithIO(Stream<T> stream, IOConsumer<? super T> action) throws IOException {
        try {
            stream.forEach(action.asUnchecked());
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    public static <T, U, A, R> Collector<T, ?, R> mappingWithIO(IOFunction<? super T, ? extends U> mapper, Collector<? super U, A, R> downstream) {
        return mapping(mapper.asUnchecked(), downstream);
    }
}
