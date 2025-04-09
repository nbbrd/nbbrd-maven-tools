package nbbrd.compatibility;

import lombok.NonNull;
import nbbrd.design.RepresentableAsString;
import nbbrd.design.StaticFactoryMethod;

import java.time.LocalDate;

@RepresentableAsString
@lombok.Value(staticConstructor = "of")
public class Tag {

    private static final char SEPARATOR = '/';
    private static final LocalDate NO_DATE_VALUE = LocalDate.MAX;

    public static final Tag NO_TAG = new Tag(NO_DATE_VALUE, "");

    @StaticFactoryMethod
    public static @NonNull Tag parse(@NonNull CharSequence text) throws IllegalArgumentException {
        String textString = text.toString();
        int separatorIndex = textString.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            throw new IllegalArgumentException("Invalid tag");
        }
        return new Tag(
                separatorIndex == 0 ? NO_DATE_VALUE : LocalDate.parse(textString.substring(0, separatorIndex)),
                textString.substring(separatorIndex + 1)
        );
    }

    @NonNull
    LocalDate date;

    @NonNull
    String ref;

    @Override
    public String toString() {
        return (date.equals(NO_DATE_VALUE) ? "" : date.toString()) + SEPARATOR + ref;
    }

    public @NonNull Tag withoutDate() {
        return new Tag(NO_DATE_VALUE, ref);
    }
}
