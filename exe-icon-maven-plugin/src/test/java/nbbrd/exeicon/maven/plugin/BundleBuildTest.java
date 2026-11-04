package nbbrd.exeicon.maven.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BundleBuildTest {

    @Test
    void rebuildsIdenticalBundleWithProjectLocalNode() throws Exception {
        Path target = Path.of("target").toAbsolutePath();
        Path script = target.resolve("generated-resources/internal/exeicon/icons.js");
        byte[] before = Files.readAllBytes(script);
        Path notices = script.resolveSibling("THIRD-PARTY-NOTICES.txt");
        byte[] beforeNotices = Files.readAllBytes(notices);
        Path node = target.resolve(System.getProperty("os.name").startsWith("Windows") ? "node/node.exe" : "node/node");
        ProcessBuilder builder = new ProcessBuilder(node.toString(), "build.mjs")
                .directory(target.resolve("js-build").toFile())
                .redirectErrorStream(true)
                .redirectOutput(target.resolve("bundle-rebuild.log").toFile());
        for (String variable : new String[] {"TMP", "TEMP", "TMPDIR"}) {
            builder.environment().put(variable, target.resolve("js-build").toString());
        }
        Process process = builder.start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue())
                    .as(Files.readString(target.resolve("bundle-rebuild.log")))
                    .isZero();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        assertThat(Files.readAllBytes(script)).isEqualTo(before);
        assertThat(Files.readAllBytes(notices)).isEqualTo(beforeNotices);
        assertThat(Path.of(System.getProperty("java.io.tmpdir")).toRealPath()).isEqualTo(target.toRealPath());
    }
}
