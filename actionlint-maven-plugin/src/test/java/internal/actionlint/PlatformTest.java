package internal.actionlint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlatformTest {

    @ParameterizedTest
    @CsvSource({
        "Windows 11, amd64, windows, amd64",
        "Linux, x86_64, linux, amd64",
        "Linux, aarch64, linux, arm64",
        "Linux, arm, linux, armv6",
        "Mac OS X, aarch64, darwin, arm64",
        "FreeBSD, i386, freebsd, 386"
    })
    void mapsJavaPropertiesToReleaseNames(String osName, String osArch, String os, String arch) {
        assertThat(Platform.of(osName, osArch)).isEqualTo(new Platform(os, arch));
    }

    @Test
    void namesArchiveAndExecutableByOs() {
        Platform windows = Platform.of("Windows 11", "amd64");
        assertThat(windows.getArchiveName("1.7.12")).isEqualTo("actionlint_1.7.12_windows_amd64.zip");
        assertThat(windows.getExecutableName()).isEqualTo("actionlint.exe");
        Platform linux = Platform.of("Linux", "amd64");
        assertThat(linux.getArchiveName("1.7.12")).isEqualTo("actionlint_1.7.12_linux_amd64.tar.gz");
        assertThat(linux.getExecutableName()).isEqualTo("actionlint");
    }

    @Test
    void rejectsUnsupportedPlatforms() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Platform.of("SunOS", "amd64"))
                .withMessageContaining("operating system");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Platform.of("Linux", "ppc64le"))
                .withMessageContaining("architecture");
    }
}
