package nbbrd.bundle.spi;

import lombok.NonNull;
import lombok.Value;

/** A required launcher file, with its role and executable-permission requirement. */
@Value
public class LauncherFile {
    @NonNull String path;

    @NonNull String role;

    boolean executable;
}
