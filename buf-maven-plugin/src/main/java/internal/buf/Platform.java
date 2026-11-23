package internal.buf;

import java.util.Locale;
import lombok.NonNull;
import lombok.Value;

/** Operating system and architecture, named as in the classifiers of the buf artifacts on Maven Central. */
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

    public @NonNull String getClassifier() {
        return os + "-" + arch;
    }

    public @NonNull String getExecutableName(@NonNull String version) {
        return "buf-" + version + (isWindows() ? ".exe" : "");
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
            return "osx";
        }
        throw new IllegalArgumentException("Unsupported operating system: " + osName);
    }

    private static String parseArch(String osArch) {
        String value = osArch.toLowerCase(Locale.ROOT);
        switch (value) {
            case "amd64":
            case "x86_64":
                return "x86_64";
            case "aarch64":
            case "arm64":
                return "aarch_64";
            default:
                if (value.startsWith("arm")) {
                    return "arm_32";
                }
                throw new IllegalArgumentException("Unsupported architecture: " + osArch);
        }
    }
}
