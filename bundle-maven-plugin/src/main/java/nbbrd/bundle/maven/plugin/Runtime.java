package nbbrd.bundle.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;

/** A local runtime archive and its destination platform. */
@Data
public final class Runtime {

    private String id;
    private File archive;
    private File directory;
    private String javaHome;

    public Path resolveArchive() throws IOException {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Runtime id must be a non-empty filename component");
        }
        if ((archive == null) == (directory == null)) {
            throw new IllegalArgumentException("Runtime " + id + " requires exactly one of archive or directory");
        }
        if (archive != null) {
            return archive.toPath();
        }
        try (Stream<Path> files = Files.list(directory.toPath())) {
            List<Path> matches = files.filter(Files::isRegularFile)
                    .filter(path -> isArchive(path.getFileName().toString()))
                    .collect(Collectors.toList());
            if (matches.size() != 1) {
                throw new IllegalArgumentException(
                        "Runtime " + id + " requires exactly one supported archive in " + directory + ": " + matches);
            }
            return matches.get(0);
        }
    }

    private static boolean isArchive(String name) {
        return name.endsWith(".zip") || name.endsWith(".tar.gz") || name.endsWith(".tgz");
    }
}
