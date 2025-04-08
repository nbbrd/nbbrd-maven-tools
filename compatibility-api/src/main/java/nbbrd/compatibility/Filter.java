package nbbrd.compatibility;

import lombok.NonNull;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;

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

    public boolean containsDate(@NonNull LocalDate date) {
        return !from.isAfter(date) && !date.isAfter(to);
    }

    private boolean containsRef(@NonNull Tag tag) {
        return tag.getRef().contains(ref);
    }

    public boolean contains(@NonNull Tag tag) {
        return containsRef(tag) && containsDate(tag.getDate());
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
