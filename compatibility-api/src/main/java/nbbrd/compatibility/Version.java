package nbbrd.compatibility;

import lombok.AccessLevel;
import lombok.NonNull;
import nbbrd.design.RepresentableAsString;
import nbbrd.design.StaticFactoryMethod;

@RepresentableAsString
@lombok.AllArgsConstructor(access = AccessLevel.PRIVATE)
@lombok.EqualsAndHashCode
public class Version implements CharSequence {

    public static final Version NO_VERSION = new Version("");

    @StaticFactoryMethod
    public static @NonNull Version parse(@NonNull CharSequence text) {
        return new Version(text.toString());
    }

    @NonNull
    String text;

    @Override
    public int length() {
        return text.length();
    }

    @Override
    public char charAt(int index) {
        return text.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return text.subSequence(start, end);
    }

    @Override
    public String toString() {
        return text;
    }
}
