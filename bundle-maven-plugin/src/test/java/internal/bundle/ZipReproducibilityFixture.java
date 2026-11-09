package internal.bundle;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

public final class ZipReproducibilityFixture {

    public static void main(String[] args) throws Exception {
        Path directory = Paths.get(args[0]);
        Files.createDirectories(directory);
        for (int index = 1; index < args.length; index++) {
            try (Archive archive = new Archive(directory)) {
                archive.add(new Archive.Entry(
                        "bin/java",
                        EntryType.FILE,
                        0755,
                        archive.store(new ByteArrayInputStream("binary".getBytes(StandardCharsets.UTF_8))),
                        null));
                archive.add(new Archive.Entry("lib/current", EntryType.SYMLINK, 0777, null, "../bin/java"));
                archive.write(directory.resolve(index + ".zip"), "zip", Instant.parse(args[index]));
            }
        }
    }
}
