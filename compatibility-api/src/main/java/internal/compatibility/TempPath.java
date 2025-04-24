package internal.compatibility;

import lombok.NonNull;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

@lombok.RequiredArgsConstructor(staticName = "of")
public final class TempPath implements Closeable {

    @lombok.Getter
    private final @NonNull Path path;

    @Override
    public @NonNull String toString() {
        return path.toString();
    }

    @Override
    public void close() throws IOException {
        Files2.deleteRecursively(path);
    }
}
