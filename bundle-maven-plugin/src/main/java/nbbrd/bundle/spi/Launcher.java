package nbbrd.bundle.spi;

import java.util.List;
import nbbrd.service.Quantifier;
import nbbrd.service.ServiceDefinition;
import nbbrd.service.ServiceId;
import org.jspecify.annotations.Nullable;

/** Adapts application launchers to a bundled Java home. */
@ServiceDefinition(quantifier = Quantifier.MULTIPLE)
public interface Launcher {

    @ServiceId(pattern = ServiceId.KEBAB_CASE)
    String getLauncherId();

    /** Required files relative to the application root. Paths must be non-empty and unique. */
    List<LauncherFile> getFiles(@Nullable String appName);

    /** Patches UTF-8 launcher text, preserving its line endings. Java home is relative to the application root. */
    String patch(LauncherFile file, String content, String javaHome);
}
