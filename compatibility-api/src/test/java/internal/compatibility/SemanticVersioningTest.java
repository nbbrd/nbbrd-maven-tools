package internal.compatibility;

import nbbrd.compatibility.Version;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static tests.compatibility.VersioningAssert.assertVersioningCompliance;

class SemanticVersioningTest {

    @Test
    void testCompliance() {
        assertVersioningCompliance(new SemanticVersioning());
    }

    @Test
    void testIsValid() {
        SemanticVersioning x = new SemanticVersioning();
        assertThat(x.isValidVersion(Version.parse("1.1.0"))).isTrue();
        assertThat(x.isValidVersion(Version.parse(".1.0"))).isFalse();
    }

    @Test
    void testGetVersionComparator() {
        SemanticVersioning x = new SemanticVersioning();
        assertThat(x.getVersionComparator().compare(Version.parse("1.0.0"), Version.parse("1.0.1"))).isEqualTo(-1);
        assertThat(x.getVersionComparator().compare(Version.parse("1.0.1"), Version.parse("1.0.0"))).isEqualTo(1);
        assertThat(x.getVersionComparator().compare(Version.parse("1.0.0"), Version.parse("1.0.0"))).isEqualTo(0);
    }
}