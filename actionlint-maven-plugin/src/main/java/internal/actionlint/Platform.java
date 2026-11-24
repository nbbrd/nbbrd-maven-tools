package internal.actionlint;

import java.util.Locale;
import lombok.NonNull;
import lombok.Value;

/** Operating system and architecture, named as in actionlint release archives. */
@Value
public class Platform {

    @NonNull String os;

    @NonNull String arch;

    public static @NonNull Platform current() {
        return of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    public static @NonNull Platform of(@NonNull String osName, @NonNull String osArch) {
        return new Platform(parseOs(osName), parseArch(osArch));
    }

    public boolean isWindows() {
        return os.equals("windows");
    }

    public @NonNull String getArchiveName(@NonNull String version) {
        return "actionlint_" + version + "_" + os + "_" + arch + (isWindows() ? ".zip" : ".tar.gz");
    }

    public @NonNull String getExecutableName() {
        return isWindows() ? "actionlint.exe" : "actionlint";
    }

    private static String parseOs(String osName) {
        String value = osName.toLowerCase(Locale.ROOT);
        if (value.startsWith("windows")) {
            return "windows";
        }
        if (value.startsWith("linux")) {
            return "linux";
        }
        if (value.startsWith("mac") || value.startsWith("darwin")) {
            return "darwin";
        }
        if (value.startsWith("freebsd")) {
            return "freebsd";
        }
        throw new IllegalArgumentException("Unsupported operating system: " + osName);
    }

    private static String parseArch(String osArch) {
        String value = osArch.toLowerCase(Locale.ROOT);
        switch (value) {
            case "amd64":
            case "x86_64":
                return "amd64";
            case "aarch64":
            case "arm64":
                return "arm64";
            case "x86":
            case "i386":
            case "i486":
            case "i586":
            case "i686":
                return "386";
            default:
                if (value.startsWith("arm")) {
                    return "armv6";
                }
                throw new IllegalArgumentException("Unsupported architecture: " + osArch);
        }
    }
}
