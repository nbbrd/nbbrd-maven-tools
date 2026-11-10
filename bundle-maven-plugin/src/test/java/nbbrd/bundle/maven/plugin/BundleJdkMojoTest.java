package nbbrd.bundle.maven.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BundleJdkMojoTest {

    @TempDir
    Path temp;

    @Test
    void runtimeIdsTrimWhitespaceAndPreserveOrder() {
        BundleJdkMojo mojo = new BundleJdkMojo();
        mojo.runtimeIds = " windows-x64, linux-x64 , macos-aarch64 ";
        mojo.archivePathTemplate = "jdks/{id}";
        mojo.outputFileTemplate = "bundles/{id}.zip";
        assertThat(mojo.selectRuntimes())
                .extracting(Runtime::getId)
                .containsExactly("windows-x64", "linux-x64", "macos-aarch64");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ",linux", "linux,", "linux,,macos"})
    void rejectsEmptyRuntimeIds(String ids) {
        BundleJdkMojo mojo = new BundleJdkMojo();
        mojo.runtimeIds = ids;
        assertThatIllegalArgumentException()
                .isThrownBy(mojo::selectRuntimes)
                .withMessageContaining("non-empty comma-separated ids");
    }

    @Test
    void rejectsDuplicateAndUnsafeRuntimeIds() {
        BundleJdkMojo mojo = new BundleJdkMojo();
        mojo.runtimeIds = "linux,LINUX";
        assertThatIllegalArgumentException()
                .isThrownBy(mojo::selectRuntimes)
                .withMessageContaining("Duplicate runtime id");
        mojo.runtimeIds = "../linux";
        assertThatIllegalArgumentException().isThrownBy(mojo::selectRuntimes).withMessageContaining("Runtime id");
    }

    @Test
    void requiresExactlyOneRuntimeConfigurationModeAndBothTemplatesForIds() throws IOException {
        BundleJdkMojo mojo = configured();
        mojo.runtimeIds = "linux";
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("mutually exclusive");
        mojo.runtimes = null;
        assertThatIllegalArgumentException()
                .isThrownBy(mojo::selectRuntimes)
                .withMessageContaining("requires both archivePathTemplate and outputFileTemplate");
        mojo.archivePathTemplate = "jdks/{id}";
        assertThatIllegalArgumentException().isThrownBy(mojo::selectRuntimes).withMessageContaining("requires both");
        mojo.archivePathTemplate = null;
        mojo.outputFileTemplate = "bundles/{id}.zip";
        assertThatIllegalArgumentException().isThrownBy(mojo::selectRuntimes).withMessageContaining("requires both");
        mojo.runtimeIds = null;
        assertThatIllegalArgumentException()
                .isThrownBy(mojo::selectRuntimes)
                .withMessageContaining("Configure either runtimeIds");
    }

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
        mojo.projectDirectory = temp.toFile();
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

    @Test
    void expandsTemplatesRelativeToProjectWithoutOverridingExplicitPaths() {
        BundleJdkMojo mojo = new BundleJdkMojo();
        mojo.projectDirectory = temp.toFile();
        assertThat(mojo.resolvePath(null, "jdks/{id}/{id}.zip", "archivePath", "linux"))
                .isEqualTo(temp.resolve("jdks/linux/linux.zip").toFile());
        assertThat(mojo.resolvePath(null, temp.resolve("{id}.tgz").toString(), "outputFile", "linux"))
                .isEqualTo(temp.resolve("linux.tgz").toFile());
        assertThat(mojo.resolvePath(Paths.get("custom.zip").toFile(), "{unknown}", "outputFile", "linux"))
                .isEqualTo(temp.resolve("custom.zip").toFile());
        assertThat(mojo.resolvePath(temp.resolve("custom.zip").toFile(), null, "outputFile", "linux"))
                .isEqualTo(temp.resolve("custom.zip").toFile());
    }

    @Test
    void rejectsUnknownOrMalformedTemplatePlaceholders() {
        BundleJdkMojo mojo = new BundleJdkMojo();
        for (String template : Arrays.asList("{os}.zip", "{{id}}.zip", "{id.zip")) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> mojo.resolvePath(null, template, "outputFile", "linux"))
                    .withMessageContaining("outputFileTemplate supports only the {id} placeholder");
        }
    }

    @Test
    void validatesExpandedOutputsAndKeepsRuntimeConfigurationUnchanged() throws IOException {
        BundleJdkMojo mojo = configured();
        Runtime runtime = mojo.runtimes.get(0);
        runtime.setArchivePath(null);
        runtime.setOutputFile(null);
        mojo.archivePathTemplate = "runtime.zip";
        mojo.outputFileTemplate = "application-{id}.rar";
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("linux")
                .hasMessageContaining("outputFile must end");
        assertThat(runtime.getArchivePath()).isNull();
        assertThat(runtime.getOutputFile()).isNull();
        mojo.outputFileTemplate = "application-{id}.zip";
        Runtime second = new Runtime();
        second.setId("windows");
        mojo.runtimes = Arrays.asList(runtime, second);
        mojo.outputFileTemplate = "shared.zip";
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Duplicate output archive");
    }
}
