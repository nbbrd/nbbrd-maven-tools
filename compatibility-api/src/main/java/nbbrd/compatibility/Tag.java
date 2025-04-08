package nbbrd.compatibility;

import lombok.NonNull;
import nbbrd.design.RepresentableAsString;
import nbbrd.design.StaticFactoryMethod;

import java.time.LocalDate;

@RepresentableAsString
@lombok.Value(staticConstructor = "of")
public class Tag {

    public static final Tag NO_TAG = new Tag(LocalDate.MAX, "");

    private static final String SEPARATOR = "/";

    @StaticFactoryMethod
    public static @NonNull Tag parse(@NonNull CharSequence text) throws IllegalArgumentException {
        String textString = text.toString();
        int separatorIndex = textString.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            throw new IllegalArgumentException("Invalid tag");
        }
        return new Tag(
                separatorIndex == 0 ? LocalDate.MAX : LocalDate.parse(textString.substring(0, separatorIndex)),
                textString.substring(separatorIndex + SEPARATOR.length())
        );
    }

    @NonNull
    LocalDate date;

    @NonNull
    String ref;

    @Override
    public String toString() {
        return (date.equals(LocalDate.MAX) ? "" : date.toString()) + SEPARATOR + ref;
    }
}
