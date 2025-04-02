package nbbrd.compatibility;

import lombok.AccessLevel;
import lombok.NonNull;
import nbbrd.design.RepresentableAsString;
import nbbrd.design.StaticFactoryMethod;

@RepresentableAsString
@lombok.AllArgsConstructor(access = AccessLevel.PRIVATE)
@lombok.EqualsAndHashCode
public class Tag implements CharSequence {

    public static final Tag NO_TAG = new Tag("");

    @StaticFactoryMethod
    public static @NonNull Tag parse(@NonNull CharSequence text) {
        return new Tag(text.toString());
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
        return text.toString();
    }
}
