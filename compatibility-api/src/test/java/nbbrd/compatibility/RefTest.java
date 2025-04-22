package nbbrd.compatibility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static nbbrd.compatibility.Ref.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RefTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testFactories() {
        assertThatNullPointerException()
                .isThrownBy(() -> parse(null));

        assertThat(parse("2023-05-02/v3.0.0"))
                .returns(LocalDate.parse("2023-05-02"), Ref::getDate)
                .returns("v3.0.0", Ref::getName);

        assertThatNullPointerException()
                .isThrownBy(() -> of(null, null));

        assertThat(of(LocalDate.parse("2023-05-02"), "v3.0.0"))
                .returns(LocalDate.parse("2023-05-02"), Ref::getDate)
                .returns("v3.0.0", Ref::getName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2023-05-02/v3.0.0", "/v3.0.0"})
    void testRepresentableAsString(String input) {
        assertThat(parse(input)).hasToString(input);
    }

    @Test
    void testWithoutDate() {
        assertThat(parse("2023-05-02/v3.0.0").withoutDate())
                .isEqualTo(parse("/v3.0.0"))
                .isEqualTo(ofVersion("3.0.0"));

        assertThat(parse("/v3.0.0").withoutDate())
                .isEqualTo(parse("/v3.0.0"))
                .isEqualTo(ofVersion("3.0.0"));
    }
}