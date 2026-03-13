package internal.compatibility.spi;

import nbbrd.io.Resource;
import nbbrd.io.sys.EndOfProcessException;
import nbbrd.io.sys.OS;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static internal.compatibility.Collectors2.consuming;
import static internal.compatibility.Collectors2.peeking;
import static java.lang.String.join;
import static java.lang.System.lineSeparator;

class Disco {

    public static Path setup(Path workingDir, String jdkVersion, Consumer<? super String> onEvent) throws IOException {
        Path setupDisco = workingDir.resolve("setupDisco.xml");

        extractPom(setupDisco, onEvent);
        updatePom(setupDisco, onEvent);
        executePom(setupDisco, jdkVersion, onEvent);

        return getUnpackedJdkPath(workingDir);
    }

    private static Path getUnpackedJdkPath(Path workingDir) throws IOException {
        Path outputDir = workingDir.resolve("target").resolve("jdks").resolve("jdk");
        try (Stream<Path> files = Files.list(outputDir)) {
            return files.findFirst().orElseThrow(() -> new IOException("Could not find any JDK files in " + outputDir));
        }
    }

    private static void executePom(Path setupDisco, String jdkVersion, Consumer<? super String> onEvent) throws IOException {
        MvnCommand command = MvnCommand
                .builder()
                .binary(null).batchMode(true).file(setupDisco)
                .goal("clean").goal("package")
                .property("jdkVersion", jdkVersion)
                .build();

        Watcher watcher = new Watcher();

        try {
            command
                    .toTextProcessor()
                    .withListener(onEvent)
                    .process(watcher.asCollector());
        } catch (EndOfProcessException ex) {
            if (watcher.isPkixProblem()) {
                onEvent.accept("Execution failed due to PKIX path building issue");
                for (String types : getSystemTrustStoreTypes()) {
                    onEvent.accept("Retrying with javax.net.ssl.trustStoreType=" + types);
                    try {
                        command
                                .withProperty("javax.net.ssl.trustStoreType", types)
                                .toTextProcessor()
                                .withListener(onEvent)
                                .process(watcher.reset().asCollector());
                        return;
                    } catch (EndOfProcessException ignore) {
                        if (!watcher.isPkixProblem()) {
                            break;
                        }
                    }
                }
                onEvent.accept("Retries failed, PKIX path building issue could not be resolved");
            } else {
                onEvent.accept("Execution failed with an unknown error");
            }

            throw new IOException("Execution failed with the following errors: " + lineSeparator() + join(lineSeparator(), watcher.getErrors()), ex);
        }
    }

    private static final class Watcher {

        @lombok.Getter
        private boolean pkixProblem = false;

        @lombok.Getter
        private final List<String> errors = new ArrayList<>();

        public Watcher reset() {
            errors.clear();
            pkixProblem = false;
            return this;
        }

        public void check(String msg) {
            if (!pkixProblem) {
                pkixProblem = msg.contains("PKIX path building failed");
            }
            errors.add(msg);
        }

        public Collector<? super String, ?, ?> asCollector() {
            return peeking(this::check, consuming());
        }
    }

    private static void updatePom(Path setupDisco, Consumer<? super String> onEvent) throws IOException {
        MvnCommand
                .builder()
                .binary(null).quiet(true).batchMode(true).file(setupDisco)
                .goal("versions:update-properties")
                .build()
                .toTextProcessor()
                .withListener(onEvent)
                .process();
    }

    private static void extractPom(Path setupDisco, Consumer<? super String> onEvent) throws IOException {
        onEvent.accept("Extracting pom file to " + setupDisco);
        try (InputStream stream = Resource.newInputStream(Disco.class, "setupDisco.xml")) {
            Files.copy(stream, setupDisco);
        }
    }

    private static List<String> getSystemTrustStoreTypes() {
        return OS.NAME == OS.Name.WINDOWS
                ? Arrays.asList("Windows-ROOT", "Windows-ROOT-LOCALMACHINE", "Windows-ROOT-CURRENTUSER", "Windows-MY", "Windows-MY-CURRENTUSER", "Windows-MY-LOCALMACHINE")
                : Collections.emptyList();
    }
}
