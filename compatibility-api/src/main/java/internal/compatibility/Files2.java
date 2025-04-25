package internal.compatibility;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

public final class Files2 {

    private Files2() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void deleteRecursively(@NonNull Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return super.visitFile(file, attrs);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return super.postVisitDirectory(dir, exc);
            }
        });
    }

    public static boolean hasExtension(@NonNull Path file, @NonNull String extension) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension);
    }

    public static List<Path> getSortedFiles(@NonNull Path dir, @NonNull DirectoryStream.Filter<? super Path> filter, @NonNull Comparator<? super Path> comparator) throws IOException {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(dir, filter)) {
            return StreamSupport.stream(directoryStream.spliterator(), false)
                    .sorted(comparator)
                    .collect(toList());
        }
    }

}
