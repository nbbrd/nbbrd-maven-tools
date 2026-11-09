package internal.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class LaunchersTest {

    @TempDir
    Path temp;

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void batchLauncherRunsBundledCommandDespiteEnvironmentOverridesAndSpaces() throws Exception {
        Path app = temp.resolve("application with spaces");
        Files.createDirectories(app.resolve("bin"));
        Files.createDirectories(app.resolve("jdk with spaces/bin"));
        // A renamed command interpreter lets us exercise selection without downloading a JDK.
        Files.copy(Paths.get(System.getenv("ComSpec")), app.resolve("jdk with spaces/bin/java.exe"));
        String original = "@echo off\r\nfor %%i in (\"%~dp0..\") do set \"BASEDIR=%%~fi\"\r\n"
                + "if \"%JAVACMD%\"==\"\" set JAVACMD=java\r\n%JAVACMD% %*\r\n";
        Path script = app.resolve("bin/run.bat");
        Files.write(
                script,
                Launchers.find("appassembler")
                        .patch(Launchers.find("appassembler").getFiles("run").get(1), original, "jdk with spaces")
                        .getBytes(StandardCharsets.UTF_8));
        ProcessBuilder builder = new ProcessBuilder(
                System.getenv("ComSpec"), "/d", "/c", script.toString(), "/d", "/c", "echo", "bundled-runtime");
        builder.environment().put("JAVACMD", "missing-java");
        builder.environment().put("JAVA_HOME", "missing-home");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            try (InputStream input = process.getInputStream()) {
                assertThat(read(input)).contains("bundled-runtime");
            }
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void unixLauncherOverridesExternalJavaCommandAndKeepsCrLf() {
        String script = "#!/bin/sh\r\nif [ -z \"$JAVACMD\" ] ; then\r\n  JAVACMD=java\r\nfi\r\n";
        assertThat(Launchers.find("appassembler")
                        .patch(
                                Launchers.find("appassembler").getFiles("run").get(0),
                                script,
                                "jdk with spaces/Contents/Home"))
                .contains("JAVA_HOME=\"$BASEDIR/jdk with spaces/Contents/Home\"\r\n"
                        + "JAVACMD=\"$JAVA_HOME/bin/java\"\r\nif [ -z \"$JAVACMD\" ] ; then\r\n");
    }

    @Test
    void refusesAmbiguousNetbeansSettings() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Launchers.find("netbeans")
                        .patch(
                                Launchers.find("netbeans").getFiles("brand").get(0),
                                "#jdkhome=\"/path/to/jdk\"\n#jdkhome=\"/path/to/jdk\"\n",
                                "jdk"));
    }

    @Test
    void handlesUnixProgramNamesEndingInBatUsingExplicitRoles() {
        nbbrd.bundle.spi.Launcher launcher = Launchers.find("appassembler");
        java.util.List<nbbrd.bundle.spi.LauncherFile> files = launcher.getFiles("run.bat");
        assertThat(files.get(0).getPath()).isEqualTo("bin/run.bat");
        assertThat(files.get(0).isExecutable()).isTrue();
        assertThat(launcher.patch(files.get(0), "if [ -z \"$JAVACMD\" ] ; then\nfi\n", "jdk"))
                .contains("JAVA_HOME=\"$BASEDIR/jdk\"");
        assertThat(files.get(1).getPath()).isEqualTo("bin/run.bat.bat");
        assertThat(files.get(1).isExecutable()).isFalse();
    }

    @Test
    void discoversBuiltInLaunchersThroughGeneratedLoader() {
        assertThat(nbbrd.bundle.spi.LauncherLoader.load())
                .extracting(nbbrd.bundle.spi.Launcher::getLauncherId)
                .contains("appassembler", "netbeans");
        assertThat(Launchers.find("appassembler").getFiles("run"))
                .extracting(nbbrd.bundle.spi.LauncherFile::getPath)
                .containsExactly("bin/run", "bin/run.bat");
        assertThat(Launchers.find("netbeans").getFiles("brand"))
                .extracting(nbbrd.bundle.spi.LauncherFile::getPath)
                .containsExactly("etc/brand.conf");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Launchers.find("unknown"))
                .withMessageContaining("Unknown launcher");
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
