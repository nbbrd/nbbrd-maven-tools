package internal.bundle.spi;

import internal.bundle.Launchers;
import java.util.Arrays;
import java.util.List;
import nbbrd.bundle.spi.Launcher;
import nbbrd.bundle.spi.LauncherFile;
import nbbrd.service.ServiceProvider;
import org.jspecify.annotations.Nullable;

@ServiceProvider
public final class AppassemblerLauncher implements Launcher {

    @Override
    public String getLauncherId() {
        return "appassembler";
    }

    @Override
    public List<LauncherFile> getFiles(@Nullable String appName) {
        String program = Launchers.component(appName, "appName");
        return Arrays.asList(
                new LauncherFile("bin/" + program, "unix", true),
                new LauncherFile("bin/" + program + ".bat", "windows", false));
    }

    @Override
    public String patch(LauncherFile file, String content, String javaHome) {
        String home = javaHome.isEmpty() ? "" : javaHome + "/";
        if ("windows".equals(file.getRole())) {
            return Launchers.replace(
                    content,
                    "(?m)^if \"%JAVACMD%\"==\"\" set JAVACMD=java(?:\\.exe)?\\r?$",
                    "set JAVACMD=\"%BASEDIR%\\" + home.replace('/', '\\') + "bin\\java.exe\"");
        }
        if (!"unix".equals(file.getRole())) {
            throw new IllegalArgumentException("Unknown Appassembler launcher role: " + file.getRole());
        }
        String newline = content.contains("\r\n") ? "\r\n" : "\n";
        String marker = "if [ -z \"$JAVACMD\" ] ; then";
        String replacement = "JAVA_HOME=\"$BASEDIR" + (javaHome.isEmpty() ? "" : "/" + javaHome) + "\"" + newline
                + "JAVACMD=\"$JAVA_HOME/bin/java\"" + newline + marker;
        if (content.indexOf(marker) < 0 || content.indexOf(marker) != content.lastIndexOf(marker)) {
            throw new IllegalArgumentException(
                    "Unrecognized Appassembler Unix launcher: expected one Java selection block");
        }
        return content.replace(marker, replacement);
    }
}
