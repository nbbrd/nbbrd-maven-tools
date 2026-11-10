package internal.bundle.spi;

import java.util.Collections;
import java.util.List;
import nbbrd.bundle.spi.Launcher;
import nbbrd.bundle.spi.LauncherFile;
import nbbrd.service.ServiceProvider;
import org.jspecify.annotations.Nullable;

@ServiceProvider
public final class TestLauncher implements Launcher {

    @Override
    public String getLauncherId() {
        return "test-launcher";
    }

    @Override
    public List<LauncherFile> getFiles(@Nullable String appName) {
        if ("empty".equals(appName)) {
            return Collections.singletonList(new LauncherFile(".", "test", false));
        }
        if ("duplicate".equals(appName)) {
            return java.util.Arrays.asList(
                    new LauncherFile("data.txt", "test", false), new LauncherFile("./data.txt", "test", false));
        }
        if ("traversal".equals(appName)) {
            return Collections.singletonList(new LauncherFile("../data.txt", "test", false));
        }
        if ("no-files".equals(appName)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new LauncherFile("data.txt", "test", false));
    }

    @Override
    public String patch(LauncherFile file, String content, String javaHome) {
        return content.replace("untouched", javaHome);
    }
}
