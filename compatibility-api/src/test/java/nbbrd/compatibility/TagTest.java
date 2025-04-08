package nbbrd.compatibility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TagTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testFactories() {
        assertThatNullPointerException()
                .isThrownBy(() -> Tag.parse(null));

        assertThat(Tag.parse("2023-05-02/v3.0.0"))
                .returns(LocalDate.parse("2023-05-02"), Tag::getDate)
                .returns("v3.0.0", Tag::getRef);

        assertThatNullPointerException()
                .isThrownBy(() -> Tag.of(null, null));

        assertThat(Tag.of(LocalDate.parse("2023-05-02"), "v3.0.0"))
                .returns(LocalDate.parse("2023-05-02"), Tag::getDate)
                .returns("v3.0.0", Tag::getRef);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2023-05-02/v3.0.0"})
    void testRepresentableAsString(String input) {
        assertThat(Tag.parse(input)).hasToString(input);
    }
}