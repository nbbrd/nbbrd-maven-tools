package nbbrd.compatibility;

import lombok.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

@lombok.Value
@lombok.Builder(toBuilder = true)
public class Filter {

    public static final Filter DEFAULT = Filter.builder().build();

    @Nullable
    String ref;

    @Nullable
    LocalDate from;

    @Nullable
    LocalDate to;

    @lombok.Builder.Default
    int limit = -1;

    public boolean containsDate(@NonNull Ref ref) {
        LocalDate date = ref.getDate();
        if (date == null) date = LocalDate.MAX;
        return (from == null || !from.isAfter(date)) && (to == null || !date.isAfter(to));
    }

    private boolean containsRef(@NonNull Ref ref) {
        return this.ref == null || ref.getName().contains(this.ref);
    }

    public boolean contains(@NonNull Ref ref) {
        return containsRef(ref) && containsDate(ref);
    }

    public List<Ref> apply(@NonNull List<Ref> refs) {
        List<Ref> result = IntStream.range(0, refs.size())
                .map(i -> refs.size() - 1 - i)
                .mapToObj(refs::get)
                .filter(this::contains)
                .limit(limit >= 0 ? limit : Long.MAX_VALUE)
                .collect(toList());
        Collections.reverse(result);
        return result;
    }

    public static @NonNull LocalDate parseLocalDate(@NonNull CharSequence input) throws DateTimeParseException {
        try {
            return Year.parse(input).atDay(1);
        } catch (Exception ex1) {
            try {
                return YearMonth.parse(input).atDay(1);
            } catch (Exception ex2) {
                return LocalDate.parse(input);
            }
        }
    }
}
