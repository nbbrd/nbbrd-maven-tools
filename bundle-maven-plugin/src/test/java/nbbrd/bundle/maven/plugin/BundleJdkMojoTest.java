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
        runtime.setArchivePath(temp.toFile());
        assertThatIllegalArgumentException().isThrownBy(runtime::resolveArchive).withMessageContaining("exactly one");
        Path archive = Files.createFile(temp.resolve("runtime.tar.gz"));
        Files.createFile(temp.resolve("runtime.tar.gz.sha256"));
        assertThat(runtime.resolveArchive()).isEqualTo(archive);
        Files.createFile(temp.resolve("other.zip"));
        assertThatIllegalArgumentException().isThrownBy(runtime::resolveArchive).withMessageContaining("exactly one");
        runtime.setArchivePath(archive.toFile());
        assertThat(runtime.resolveArchive()).isEqualTo(archive);
    }

    @Test
    void reportsInvalidConfigurationAsBuildFailure() throws IOException {
        BundleJdkMojo mojo = configured();
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
        runtime.setArchivePath(temp.resolve("runtime.zip").toFile());
        assertThatIllegalArgumentException().isThrownBy(runtime::resolveArchive).withMessageContaining("Runtime id");
    }

    @Test
    void rejectsOutputsThatWouldOverwriteAnotherRuntimeArchive() throws IOException {
        BundleJdkMojo mojo = configured();
        Runtime second = new Runtime();
        second.setId("windows");
        Path secondArchive = Files.createFile(temp.resolve("application-linux.zip"));
        second.setArchivePath(secondArchive.toFile());
        second.setOutputFile(temp.resolve("application-windows.zip").toFile());
        mojo.runtimes = Arrays.asList(mojo.runtimes.get(0), second);
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("distinct from all input archives");
        assertThat(Files.size(secondArchive)).isZero();
    }

    private BundleJdkMojo configured() throws IOException {
        Runtime runtime = new Runtime();
        runtime.setId("linux");
        runtime.setArchivePath(Files.createFile(temp.resolve("runtime.zip")).toFile());
        runtime.setOutputFile(temp.resolve("application-linux.zip").toFile());
        BundleJdkMojo mojo = new BundleJdkMojo();
        mojo.inputFile = Files.createFile(temp.resolve("application.zip")).toFile();
        mojo.applicationRoot = ".";
        mojo.launcherId = "appassembler";
        mojo.appName = "run";
        mojo.outputTimestamp = "2026-01-02T03:04:06Z";
        mojo.runtimes = Collections.singletonList(runtime);
        return mojo;
    }

    @Test
    void rejectsMissingOrUnsupportedArchivePath() throws IOException {
        Runtime runtime = new Runtime();
        runtime.setId("linux");
        assertThatIllegalArgumentException()
                .isThrownBy(runtime::resolveArchive)
                .withMessageContaining("requires archivePath");
        runtime.setArchivePath(temp.resolve("missing.zip").toFile());
        assertThatIllegalArgumentException()
                .isThrownBy(runtime::resolveArchive)
                .withMessageContaining("not a regular file or directory");
        runtime.setArchivePath(Files.createFile(temp.resolve("runtime.txt")).toFile());
        assertThatIllegalArgumentException()
                .isThrownBy(runtime::resolveArchive)
                .withMessageContaining("unsupported archivePath");
    }

    @Test
    void skipDoesNotReadInputsOrCreateOutputs() throws Exception {
        BundleJdkMojo mojo = new BundleJdkMojo();
        mojo.skip = true;
        Runtime runtime = new Runtime();
        runtime.setOutputFile(temp.resolve("unused/result.zip").toFile());
        mojo.runtimes = Collections.singletonList(runtime);
        mojo.execute();
        assertThat(Files.exists(temp.resolve("unused"))).isFalse();
    }

    @Test
    void rejectsUnsupportedOutputExtensionBeforeReadingArchives() throws IOException {
        BundleJdkMojo mojo = configured();
        mojo.runtimes.get(0).setOutputFile(temp.resolve("application-linux.rar").toFile());
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("linux")
                .hasMessageContaining("outputFile must end in .zip, .tar.gz or .tgz");
        assertThat(Files.exists(temp.resolve("application-linux.rar"))).isFalse();
    }

    @Test
    void requiresAnExplicitOutputFile() throws IOException {
        BundleJdkMojo mojo = configured();
        mojo.runtimes.get(0).setOutputFile(null);
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("linux")
                .hasMessageContaining("outputFile is required");
    }

    @Test
    void rejectsDuplicateOutputFiles() throws IOException {
        BundleJdkMojo mojo = configured();
        Runtime second = new Runtime();
        second.setId("windows");
        second.setArchivePath(mojo.runtimes.get(0).getArchivePath());
        second.setOutputFile(mojo.runtimes.get(0).getOutputFile());
        mojo.runtimes = Arrays.asList(mojo.runtimes.get(0), second);
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Duplicate output archive");
    }
}
