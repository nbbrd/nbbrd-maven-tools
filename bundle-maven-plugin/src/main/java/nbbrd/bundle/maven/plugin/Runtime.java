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
    /** Runtime archive file or directory containing exactly one supported runtime archive. */
    private File archivePath;
    /** Java home relative to the runtime archive, not the build machine; dot selects the archive root. */
    private String javaHome;
    /** Required destination archive; .zip, .tar.gz or .tgz determines the format. */
    private File outputFile;

    public Path resolveArchive() throws IOException {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Runtime id must be a non-empty filename component");
        }
        if (archivePath == null) {
            throw new IllegalArgumentException("Runtime " + id + " requires archivePath");
        }
        Path path = archivePath.toPath();
        if (Files.isRegularFile(path)) {
            if (!isArchive(path.getFileName().toString())) {
                throw new IllegalArgumentException("Runtime " + id + " has an unsupported archivePath: " + archivePath);
            }
            return path;
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                    "Runtime " + id + " archivePath is not a regular file or directory: " + archivePath);
        }
        try (Stream<Path> files = Files.list(path)) {
            List<Path> matches = files.filter(Files::isRegularFile)
                    .filter(file -> isArchive(file.getFileName().toString()))
                    .collect(Collectors.toList());
            if (matches.size() != 1) {
                throw new IllegalArgumentException(
                        "Runtime " + id + " requires exactly one supported archive in " + archivePath + ": " + matches);
            }
            return matches.get(0);
        }
    }

    private static boolean isArchive(String name) {
        return name.endsWith(".zip") || name.endsWith(".tar.gz") || name.endsWith(".tgz");
    }
}
