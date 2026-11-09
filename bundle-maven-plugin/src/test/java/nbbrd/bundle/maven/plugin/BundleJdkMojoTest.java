package nbbrd.bundle.maven.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundleJdkMojoTest {

    @TempDir
    Path temp;

    @Test
    void selectsExactlyOneArchiveIgnoringChecksumFiles() throws IOException {
        Runtime runtime = new Runtime();
        runtime.setId("linux-x86_64");
        runtime.setDirectory(temp.toFile());
        assertThatIllegalArgumentException().isThrownBy(runtime::resolveArchive).withMessageContaining("exactly one");
        Path archive = Files.createFile(temp.resolve("runtime.tar.gz"));
        Files.createFile(temp.resolve("runtime.tar.gz.sha256"));
        assertThat(runtime.resolveArchive()).isEqualTo(archive);
        Files.createFile(temp.resolve("other.zip"));
        assertThatIllegalArgumentException().isThrownBy(runtime::resolveArchive).withMessageContaining("exactly one");
        runtime.setArchive(archive.toFile());
        assertThatIllegalArgumentException().isThrownBy(runtime::resolveArchive).withMessageContaining("exactly one");
    }

    @Test
    void reportsInvalidConfigurationAsBuildFailure() throws IOException {
        BundleJdkMojo mojo = configured();
        mojo.finalName = "../outside";
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("finalName");
        mojo.finalName = "application";
        mojo.outputTimestamp = "invalid";
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("outputTimestamp");
        mojo.outputTimestamp = "1767323046";
        mojo.runtimes = Arrays.asList(mojo.runtimes.get(0), mojo.runtimes.get(0));
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsRuntimeIdPathTraversal() {
        Runtime runtime = new Runtime();
        runtime.setId("../linux");
        runtime.setArchive(temp.resolve("runtime.zip").toFile());
        assertThatIllegalArgumentException().isThrownBy(runtime::resolveArchive).withMessageContaining("Runtime id");
    }

    @Test
    void rejectsOutputsThatWouldOverwriteAnotherRuntimeArchive() throws IOException {
        BundleJdkMojo mojo = configured();
        Runtime second = new Runtime();
        second.setId("windows");
        Path secondArchive = Files.createFile(temp.resolve("application-linux.zip"));
        second.setArchive(secondArchive.toFile());
        mojo.runtimes = Arrays.asList(mojo.runtimes.get(0), second);
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("distinct from all input archives");
        assertThat(Files.size(secondArchive)).isZero();
    }

    private BundleJdkMojo configured() throws IOException {
        Runtime runtime = new Runtime();
        runtime.setId("linux");
        runtime.setArchive(Files.createFile(temp.resolve("runtime.zip")).toFile());
        BundleJdkMojo mojo = new BundleJdkMojo();
        mojo.inputFile = Files.createFile(temp.resolve("application.zip")).toFile();
        mojo.outputDirectory = temp.toFile();
        mojo.finalName = "application";
        mojo.baseDirectory = ".";
        mojo.launcher = "appassembler";
        mojo.appName = "run";
        mojo.format = "zip";
        mojo.outputTimestamp = "2026-01-02T03:04:06Z";
        mojo.runtimes = Collections.singletonList(runtime);
        return mojo;
    }
}
