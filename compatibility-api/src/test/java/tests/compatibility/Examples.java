package tests.compatibility;

import internal.compatibility.Files2;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static nbbrd.io.sys.ProcessReader.readToString;

public final class Examples {

    private Examples() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void main(String[] args) throws IOException {
        Path project = createTempDirectory("source-project");
        Files.deleteIfExists(project);
        System.out.println(generateProject(resolveResource("/source-project"), project, System.out::println));
    }

    public static Path generateProject(Path resources, Path project, Consumer<? super String> logger) throws IOException {
        createDirectory(project);
        logger.accept(readToString(UTF_8, "git", "-C", project.toString(), "init"));
        logger.accept(readToString(UTF_8, "git", "-C", project.toString(), "config", "--local", "user.name", "Test"));
        logger.accept(readToString(UTF_8, "git", "-C", project.toString(), "config", "--local", "user.email", "test@example.com"));
        for (String version : getVersions(resources)) {
            Path workingDir = resources.resolve(version);
            Files2.copyRecursively(workingDir, createDirectories(project), REPLACE_EXISTING);
            logger.accept(readToString(UTF_8, "git", "-C", project.toString(), "add", "*"));
            logger.accept(readToString(UTF_8, "git", "-C", project.toString(), "commit", "-am", version));
            logger.accept(readToString(UTF_8, "git", "-C", project.toString(), "tag", version));
        }
        return project;
    }

    private static List<String> getVersions(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isDirectory)
                    .map(path -> path.getName(path.getNameCount() - 1).toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    public static Path resolveResource(String name) throws IOException {
        return resolveResource(Examples.class, name);
    }

    public static Path resolveResource(Class<?> anchor, String name) throws IOException {
        URL resource = anchor.getResource(name);
        if (resource == null) {
            throw new IOException("Resource not found: " + name);
        }
        try {
            return Paths.get(resource.toURI());
        } catch (URISyntaxException ex) {
            throw new IOException(ex);
        }
    }
}
