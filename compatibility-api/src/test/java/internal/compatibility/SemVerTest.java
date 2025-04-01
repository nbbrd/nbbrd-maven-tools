package internal.compatibility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static tests.compatibility.VersioningAssert.assertVersioningCompliance;

class SemVerTest {

    @Test
    void testCompliance() {
        assertVersioningCompliance(new SemVer());
    }

    @Test
    void testIsValid() {
        SemVer x = new SemVer();
        assertThat(x.isValidVersion("1.1.0")).isTrue();
        assertThat(x.isValidVersion(".1.0")).isFalse();
    }

    @Test
    void testIsOrdered() {
        SemVer x = new SemVer();
        assertThat(x.isOrdered("1.0.0", "1.0.1")).isTrue();
        assertThat(x.isOrdered("1.0.1", "1.0.0")).isFalse();
        assertThat(x.isOrdered("1.0.0", "1.0.0")).isTrue();
    }
}