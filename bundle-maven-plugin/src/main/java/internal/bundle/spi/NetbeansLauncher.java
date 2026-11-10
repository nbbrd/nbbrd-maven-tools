package internal.bundle.spi;

import internal.bundle.Launchers;
import java.util.Collections;
import java.util.List;
import nbbrd.bundle.spi.Launcher;
import nbbrd.bundle.spi.LauncherFile;
import nbbrd.service.ServiceProvider;
import org.jspecify.annotations.Nullable;

@ServiceProvider
public final class NetbeansLauncher implements Launcher {

    @Override
    public String getLauncherId() {
        return "netbeans";
    }

    @Override
    public List<LauncherFile> getFiles(@Nullable String appName) {
        return Collections.singletonList(
                new LauncherFile("etc/" + Launchers.component(appName, "appName") + ".conf", "config", false));
    }

    @Override
    public String patch(LauncherFile file, String content, String javaHome) {
        return Launchers.replace(
                content,
                "(?m)^#jdkhome=\"/path/to/jdk\"\\r?$",
                "jdkhome=\"" + (javaHome.isEmpty() ? "." : javaHome) + "\"");
    }
}
