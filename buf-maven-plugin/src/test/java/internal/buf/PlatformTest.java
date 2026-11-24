package internal.buf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PlatformTest {

    @Test
    void testClassifier() {
        assertThat(Platform.of("Windows 11", "amd64").getClassifier()).isEqualTo("windows-x86_64");
        assertThat(Platform.of("Windows 11", "aarch64").getClassifier()).isEqualTo("windows-aarch_64");
        assertThat(Platform.of("Linux", "x86_64").getClassifier()).isEqualTo("linux-x86_64");
        assertThat(Platform.of("Linux", "arm").getClassifier()).isEqualTo("linux-arm_32");
        assertThat(Platform.of("Mac OS X", "aarch64").getClassifier()).isEqualTo("osx-aarch_64");
    }

    @Test
    void testExecutableName() {
        assertThat(Platform.of("Windows 11", "amd64").getExecutableName("1.75.0"))
                .isEqualTo("buf-1.75.0.exe");
        assertThat(Platform.of("Linux", "amd64").getExecutableName("1.75.0")).isEqualTo("buf-1.75.0");
    }

    @Test
    void testUnsupported() {
        assertThatIllegalArgumentException().isThrownBy(() -> Platform.of("FreeBSD", "amd64"));
        assertThatIllegalArgumentException().isThrownBy(() -> Platform.of("Linux", "x86"));
    }
}
