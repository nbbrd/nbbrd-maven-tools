package nbbrd.compatibility;

import lombok.NonNull;

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

    @NonNull
    @lombok.Builder.Default
    String ref = "";

    @lombok.NonNull
    @lombok.Builder.Default
    LocalDate from = LocalDate.MIN;

    @lombok.NonNull
    @lombok.Builder.Default
    LocalDate to = LocalDate.MAX;

    @lombok.Builder.Default
    int limit = Integer.MAX_VALUE;

    public boolean containsDate(@NonNull LocalDate date) {
        return !from.isAfter(date) && !date.isAfter(to);
    }

    private boolean containsRef(@NonNull Tag tag) {
        return tag.getRef().contains(ref);
    }

    public boolean contains(@NonNull Tag tag) {
        return containsRef(tag) && containsDate(tag.getDate());
    }

    public List<Tag> apply(@NonNull List<Tag> tags) {
        List<Tag> result = IntStream.range(0, tags.size())
                .map(i -> tags.size() - 1 - i)
                .mapToObj(tags::get)
                .filter(this::contains)
                .limit(limit)
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
