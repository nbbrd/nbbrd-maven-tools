package nbbrd.actionlint.maven.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import internal.actionlint.HttpProxy;
import internal.actionlint.Platform;
import internal.actionlint.Problem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LintMojoTest {

    @TempDir
    Path temp;

    @Test
    void selectsSortedYamlFilesFromWorkflowDirectory() throws Exception {
        Path workflows = Files.createDirectories(temp.resolve(LintMojo.WORKFLOWS));
        Files.createFile(workflows.resolve("release.yaml"));
        Files.createFile(workflows.resolve("ci.yml"));
        Files.createFile(workflows.resolve("README.md"));
        Files.createDirectories(workflows.resolve("nested.yml"));
        LintMojo mojo = new LintMojo();
        mojo.projectDirectory = temp.toFile();
        assertThat(mojo.selectFiles()).containsExactly(".github/workflows/ci.yml", ".github/workflows/release.yaml");
    }

    @Test
    void explicitFilesReplaceDiscovery() throws Exception {
        Files.createDirectories(temp.resolve(LintMojo.WORKFLOWS));
        Files.createFile(temp.resolve(LintMojo.WORKFLOWS).resolve("ci.yml"));
        LintMojo mojo = new LintMojo();
        mojo.projectDirectory = temp.toFile();
        mojo.files = Arrays.asList(
                temp.resolve("a.yml").toFile(), temp.resolve("b.yml").toFile());
        assertThat(mojo.selectFiles())
                .containsExactly(
                        temp.resolve("a.yml").toString(), temp.resolve("b.yml").toString());
    }

    @Test
    void doesNothingWithoutWorkflows() throws Exception {
        LintMojo mojo = new LintMojo();
        mojo.projectDirectory = temp.toFile();
        mojo.executable = temp.resolve("missing").toFile();
        mojo.execute();
        assertThat(temp).isEmptyDirectory();
    }

    @Test
    void skipsModulesOtherThanTopLevelProject() throws Exception {
        Path module = temp.resolve("module");
        Files.createDirectories(module.resolve(LintMojo.WORKFLOWS));
        Files.createFile(module.resolve(LintMojo.WORKFLOWS).resolve("ci.yml"));
        LintMojo mojo = new LintMojo();
        mojo.runOnlyAtRoot = true;
        mojo.topLevelDirectory = temp.toFile();
        mojo.projectDirectory = module.toFile();
        mojo.executable = temp.resolve("missing").toFile();
        mojo.execute();
    }

    @Test
    void fallsBackToExecutionRootWithoutProject() throws Exception {
        Files.createDirectories(temp.resolve(LintMojo.WORKFLOWS));
        Files.createFile(temp.resolve(LintMojo.WORKFLOWS).resolve("ci.yml"));
        LintMojo mojo = new LintMojo();
        mojo.runOnlyAtRoot = true;
        mojo.executionRootDirectory = temp.toFile();
        mojo.executable = temp.resolve("missing").toFile();
        assertThatExceptionOfType(MojoExecutionException.class)
                .isThrownBy(mojo::execute)
                .withMessageContaining("Cannot run actionlint");
        assertThat(mojo.selectFiles()).containsExactly(".github/workflows/ci.yml");
    }

    @Test
    void pinsChecksumsOfDefaultVersionForAllPlatforms() throws Exception {
        String resource = "/internal/actionlint/actionlint_" + LintMojo.DEFAULT_VERSION + "_checksums.txt";
        try (InputStream stream = LintMojo.class.getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            String content = new String(readAllBytes(stream), StandardCharsets.UTF_8);
            for (String os : new String[] {"Windows 11", "Linux", "Mac OS X"}) {
                for (String arch : new String[] {"amd64", "aarch64"}) {
                    assertThat(content)
                            .containsPattern("(?m)^[0-9a-f]{64}\\s+"
                                    + Pattern.quote(Platform.of(os, arch).getArchiveName(LintMojo.DEFAULT_VERSION))
                                    + "$");
                }
            }
        }
    }

    private static byte[] readAllBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            result.write(buffer, 0, count);
        }
        return result.toByteArray();
    }

    @Test
    void skipDoesNotReadProject() throws Exception {
        LintMojo mojo = new LintMojo();
        mojo.skip = true;
        mojo.execute();
    }

    @Test
    void formatsGitHubWorkflowCommandRelativeToWorkspace() {
        LintMojo mojo = new LintMojo();
        mojo.githubWorkspace = temp.toFile();
        mojo.projectDirectory = temp.resolve("module").toFile();
        Problem problem = Problem.builder()
                .file(".github/workflows/ci.yml")
                .line(6)
                .column(3)
                .kind("job-needs")
                .message("100% wrong:\nreally, wrong")
                .build();
        assertThat(mojo.toWorkflowCommand("error", problem))
                .isEqualTo("::error file=module/.github/workflows/ci.yml,line=6,col=3,"
                        + "title=actionlint [job-needs]::100%25 wrong:%0Areally, wrong");
    }

    @Test
    void selectsActiveProxyMatchingBaseUrlScheme() {
        LintMojo mojo = new LintMojo();
        mojo.baseUrl = "https://github.com/rhysd/actionlint/releases/download";
        mojo.settings = new Settings();
        mojo.settings.addProxy(proxy("inactive", "https", false));
        mojo.settings.addProxy(proxy("http", "http", true));
        mojo.settings.addProxy(proxy("https", "https", true));

        assertThat(mojo.selectProxy())
                .isEqualTo(HttpProxy.builder()
                        .host("https.example.com")
                        .port(3128)
                        .nonProxyHost("localhost")
                        .nonProxyHost("*.internal")
                        .build());

        mojo.settings.getProxies().remove(2);
        assertThat(mojo.selectProxy()).extracting(HttpProxy::getHost).isEqualTo("http.example.com");

        mojo.settings.getProxies().clear();
        assertThat(mojo.selectProxy()).isNull();
    }

    private static Proxy proxy(String id, String protocol, boolean active) {
        Proxy result = new Proxy();
        result.setId(id);
        result.setProtocol(protocol);
        result.setActive(active);
        result.setHost(id + ".example.com");
        result.setPort(3128);
        result.setNonProxyHosts("localhost|*.internal");
        return result;
    }
}
