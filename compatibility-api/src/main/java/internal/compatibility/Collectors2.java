package internal.compatibility;

import nbbrd.design.MightBePromoted;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collector;

import static java.util.stream.Collectors.*;

@MightBePromoted
public final class Collectors2 {

    private Collectors2() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static <T> Collector<T, ?, Optional<T>> toFirst() {
        return reducing((first, ignore) -> first);
    }

    public static <T> Collector<T, ?, Optional<T>> toLast() {
        return reducing((ignore, second) -> second);
    }

    public static <T> Collector<T, ?, Optional<T>> toSingle() {
        return collectingAndThen(toList(), list -> list.size() == 1 ? Optional.of(list.get(0)) : Optional.empty());
    }

    public static <T> Collector<T, ?, String> consuming() {
        return reducing("", ignore -> "", (ignoreFirst, ignoreSecond) -> "");
    }

    public static <T, A, R> Collector<T, ?, R> peeking(Consumer<? super T> action, Collector<? super T, A, R> downstream) {
        return filtering(msg -> {
            action.accept(msg);
            return true;
        }, downstream);
    }
}
