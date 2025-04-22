package nbbrd.compatibility;

import lombok.NonNull;
import nbbrd.design.RepresentableAsString;
import nbbrd.design.StaticFactoryMethod;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.LocalDate;

@RepresentableAsString
@lombok.Value(staticConstructor = "of")
public class Ref {

    private static final char SEPARATOR = '/';

    public static final Ref NO_REF = new Ref(null, "");

    @StaticFactoryMethod
    public static @NonNull Ref parse(@NonNull CharSequence text) throws IllegalArgumentException {
        String textString = text.toString();
        int separatorIndex = textString.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            throw new IllegalArgumentException("Invalid tag: cannot find separator '" + SEPARATOR + "'");
        }
        return new Ref(
                separatorIndex == 0 ? null : LocalDate.parse(textString.substring(0, separatorIndex)),
                textString.substring(separatorIndex + 1)
        );
    }

    @StaticFactoryMethod
    public static @NonNull Ref ofVersion(@NonNull CharSequence version) {
        return new Ref(null, DEFAULT_TAG_PREFIX + version);
    }

    private static final String DEFAULT_TAG_PREFIX = "v";

    @Nullable
    LocalDate date;

    @NonNull
    String name;

    @Override
    public String toString() {
        return (date == null ? "" : date.toString()) + SEPARATOR + name;
    }

    public @NonNull Ref withoutDate() {
        return new Ref(null, name);
    }
}
