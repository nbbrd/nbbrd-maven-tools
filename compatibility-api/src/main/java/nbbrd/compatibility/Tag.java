package nbbrd.compatibility;

import lombok.NonNull;
import nbbrd.design.RepresentableAsString;
import nbbrd.design.StaticFactoryMethod;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.LocalDate;

@RepresentableAsString
@lombok.Value(staticConstructor = "of")
public class Tag {

    private static final char SEPARATOR = '/';

    public static final Tag NO_TAG = new Tag(null, "");

    @StaticFactoryMethod
    public static @NonNull Tag parse(@NonNull CharSequence text) throws IllegalArgumentException {
        String textString = text.toString();
        int separatorIndex = textString.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            throw new IllegalArgumentException("Invalid tag: cannot find separator '" + SEPARATOR + "'");
        }
        return new Tag(
                separatorIndex == 0 ? null : LocalDate.parse(textString.substring(0, separatorIndex)),
                textString.substring(separatorIndex + 1)
        );
    }

    @StaticFactoryMethod
    public static @NonNull Tag ofVersion(@NonNull CharSequence version) {
        return new Tag(null, DEFAULT_TAG_PREFIX + version);
    }

    private static final String DEFAULT_TAG_PREFIX = "v";

    @Nullable
    LocalDate date;

    @NonNull
    String refName;

    @Override
    public String toString() {
        return (date == null ? "" : date.toString()) + SEPARATOR + refName;
    }

    public @NonNull Tag withoutDate() {
        return new Tag(null, refName);
    }
}
