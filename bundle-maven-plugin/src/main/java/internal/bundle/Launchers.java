package internal.bundle;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import nbbrd.bundle.spi.Launcher;
import nbbrd.bundle.spi.LauncherLoader;
import org.jspecify.annotations.Nullable;

@UtilityClass
public class Launchers {

    public Launcher find(@Nullable String id) {
        Launcher result = null;
        for (Launcher launcher : LauncherLoader.load()) {
            if (launcher.getLauncherId().equals(id)) {
                if (result != null) {
                    throw new IllegalArgumentException("Duplicate launcher id: " + id);
                }
                result = launcher;
            }
        }
        if (result == null) {
            throw new IllegalArgumentException("Unknown launcher: " + id);
        }
        return result;
    }

    public String component(@Nullable String value, String label) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException(label + " must be a non-empty filename component");
        }
        return value;
    }

    public String replace(String content, String regex, String replacement) {
        Matcher matcher = Pattern.compile(regex).matcher(content);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unrecognized launcher: expected one match for " + regex);
        }
        int start = matcher.start();
        int end = matcher.end();
        if (matcher.find()) {
            throw new IllegalArgumentException("Ambiguous launcher: multiple matches for " + regex);
        }
        // The match includes CR but not LF; preserve the original line ending.
        return content.substring(0, start)
                + replacement
                + (content.charAt(end - 1) == '\r' ? "\r" : "")
                + content.substring(end);
    }
}
