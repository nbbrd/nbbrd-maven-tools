package nbbrd.compatibility;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static nbbrd.compatibility.Filter.parseLocalDate;
import static org.assertj.core.api.Assertions.*;

public class FilterTest {

    @Test
    public void testRef() {
        assertThat(Filter.builder().build())
                .describedAs("Empty reference")
                .is(containing(unreleased))
                .is(containing(v1_1_0))
                .is(containing(v1_0_0));

        assertThat(Filter.builder().ref("Unreleased").build())
                .describedAs("Full reference")
                .is(containing(unreleased))
                .isNot(containing(v1_1_0))
                .isNot(containing(v1_0_0));

        assertThat(Filter.builder().ref("1.1.0").build())
                .describedAs("Full reference")
                .isNot(containing(unreleased))
                .is(containing(v1_1_0))
                .isNot(containing(v1_0_0));

        assertThat(Filter.builder().ref("rel").build())
                .describedAs("Partial reference")
                .is(containing(unreleased))
                .isNot(containing(v1_1_0))
                .isNot(containing(v1_0_0));

        assertThat(Filter.builder().ref("1.").build())
                .describedAs("Partial reference")
                .isNot(containing(unreleased))
                .is(containing(v1_1_0))
                .is(containing(v1_0_0));

        assertThat(Filter.builder().ref("other").build())
                .describedAs("Unknown reference")
                .isNot(containing(unreleased))
                .isNot(containing(v1_1_0))
                .isNot(containing(v1_0_0));

//        assertThat(Filter.builder().ref("other-SNAPSHOT").build())
//                .describedAs("Matching unreleased pattern reference")
//                .is(containing(unreleased))
//                .isNot(containing(v1_1_0))
//                .isNot(containing(v1_0_0));
    }

    @Test
    public void testTimeRange() {
        assertThat(Filter.builder().build())
                .is(containing(unreleased))
                .is(containing(v1_1_0))
                .is(containing(v1_0_0));

        assertThat(Filter.builder().from(v1_0_0.getDate()).to(v1_1_0.getDate()).build())
                .isNot(containing(unreleased))
                .is(containing(v1_1_0))
                .is(containing(v1_0_0));

        assertThat(Filter.builder().from(v1_0_0.getDate()).to(v1_0_0.getDate()).build())
                .isNot(containing(unreleased))
                .isNot(containing(v1_1_0))
                .is(containing(v1_0_0));

        assertThat(Filter.builder().from(LocalDate.MIN).to(v1_0_0.getDate()).build())
                .isNot(containing(unreleased))
                .isNot(containing(v1_1_0))
                .is(containing(v1_0_0));

        assertThat(Filter.builder().from(v1_1_0.getDate()).to(v1_1_0.getDate()).build())
                .isNot(containing(unreleased))
                .is(containing(v1_1_0))
                .isNot(containing(v1_0_0));

        assertThat(Filter.builder().from(v1_1_0.getDate()).to(LocalDate.MAX).build())
                .is(containing(unreleased))
                .is(containing(v1_1_0))
                .isNot(containing(v1_0_0));
    }

    @Test
    public void testParseLocalDate() {
        assertThatNullPointerException()
                .isThrownBy(() -> parseLocalDate(null));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> parseLocalDate(""));

        assertThat(parseLocalDate("2010"))
                .isEqualTo("2010-01-01");

        assertThat(parseLocalDate("2010-02"))
                .isEqualTo("2010-02-01");

        assertThat(parseLocalDate("2010-02-03"))
                .isEqualTo("2010-02-03");
    }

    @Test
    void testApply() {
        assertThat(Filter.builder().build().apply(emptyList()))
                .containsExactly();

        assertThat(Filter.builder().build().apply(asList(v1_1_0, v1_0_0)))
                .containsExactly(v1_1_0, v1_0_0);

        assertThat(Filter.builder().limit(1).build().apply(asList(v1_1_0, v1_0_0)))
                .containsExactly(v1_0_0);

        assertThat(Filter.builder().limit(0).build().apply(asList(v1_1_0, v1_0_0)))
                .containsExactly();

        assertThat(Filter.builder().from(parseLocalDate("2019")).build().apply(asList(v1_1_0, v1_0_0)))
                .containsExactly(v1_1_0);

        assertThat(Filter.builder().to(parseLocalDate("2018")).build().apply(asList(v1_1_0, v1_0_0)))
                .containsExactly(v1_0_0);
    }

    private static Condition<Filter> containing(Tag version) {
        return new Condition<>(parent -> parent.contains(version), "Must contain %s", version);
    }

    private final Tag unreleased = Tag.of(LocalDate.MAX, "Unreleased");
    private final Tag v1_1_0 = Tag.of(LocalDate.parse("2019-02-15"), "1.1.0");
    private final Tag v1_0_0 = Tag.of(LocalDate.parse("2017-06-20"), "1.0.0");
}
