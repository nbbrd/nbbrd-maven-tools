package nbbrd.compatibility;

import lombok.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
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

    public boolean containsDate(@NonNull Tag tag) {
        LocalDate date = tag.getDate();
        if (date == null) date = LocalDate.MAX;
        return (from == null || !from.isAfter(date)) && (to == null || !date.isAfter(to));
    }

    private boolean containsRef(@NonNull Tag tag) {
        return ref == null || tag.getRefName().contains(ref);
    }

    public boolean contains(@NonNull Tag tag) {
        return containsRef(tag) && containsDate(tag);
    }

    public List<Tag> apply(@NonNull List<Tag> tags) {
        List<Tag> result = IntStream.range(0, tags.size())
                .map(i -> tags.size() - 1 - i)
                .mapToObj(tags::get)
                .filter(this::contains)
                .limit(limit >= 0 ? limit : Long.MAX_VALUE)
                .collect(toList());
        Collections.reverse(result);
        return result;
    }

    public static @NonNull LocalDate parseLocalDate(@NonNull CharSequence input) {
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
