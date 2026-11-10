package example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class BundleSmokeTest {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected the bundled Java home as the only argument");
        }
        String actual = System.getProperty("java.home");
        if (!Files.isSameFile(Paths.get(actual), Paths.get(args[0]))) {
            throw new IllegalStateException("Launcher used " + actual + " instead of " + args[0]);
        }
        System.out.println("BUNDLE_SMOKE_OK java.home=" + actual
                + " java.version=" + System.getProperty("java.version")
                + " os.arch=" + System.getProperty("os.arch"));
    }
}
