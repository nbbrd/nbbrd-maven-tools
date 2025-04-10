package internal.compatibility.spi;

import nbbrd.compatibility.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tests.compatibility.Examples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectory;
import static java.nio.file.StandardOpenOption.APPEND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static tests.compatibility.Examples.generateProject;
import static tests.compatibility.Examples.resolveResource;

@EnabledForJreRange(min = JRE.JAVA_17, disabledReason = "Use Java 17 in example projects")
@Execution(ExecutionMode.CONCURRENT)
class CommandLineBuildTest {

    private static Path sourceProject;
    private static Path targetProject;

    @BeforeAll
    static void beforeAll(@TempDir Path tmp) throws IOException {
        sourceProject = generateProject(resolveResource("/source-project"), tmp.resolve("source-project"), CommandLineBuildTest::doNothing);
        targetProject = generateProject(resolveResource("/target-project"), tmp.resolve("target-project"), CommandLineBuildTest::doNothing);
    }

    @Test
    void cleanAndRestore(@TempDir Path tmp) throws IOException {
        Path project = copy(tmp);
        try (CommandLineBuild x = getJobExecutor()) {
            Path target = project.resolve("target");
            Path pom = project.resolve("pom.xml");

            assertThat(target).doesNotExist();
            byte[] originalContent = Files.readAllBytes(pom);

            createDirectory(target);
            Files.write(pom, "<!-- hello -->".getBytes(UTF_8), APPEND);

            assertThat(target).exists();
            assertThat(pom).content(UTF_8).isNotEqualTo(new String(originalContent, UTF_8));

            assertThatCode(() -> x.clean(project))
                    .doesNotThrowAnyException();

            assertThatCode(() -> x.restore(project))
                    .doesNotThrowAnyException();

            assertThat(target).doesNotExist();
            assertThat(pom).content(UTF_8).isEqualToIgnoringNewLines(new String(originalContent, UTF_8));
        }
    }

    @Test
    void verify(@TempDir Path tmp) throws IOException {
        Path project = copy(tmp);
        try (CommandLineBuild x = getJobExecutor()) {
            assertThat(x.verify(project))
                    .isEqualTo(0);

            Files.delete(project.resolve("pom.xml"));
            assertThat(x.verify(project))
                    .isEqualTo(1);
        }
    }

    @Test
    void setProperty(@TempDir Path tmp) throws IOException {
        Path project = copy(tmp);
        try (CommandLineBuild x = getJobExecutor()) {
            Path pom = project.resolve("pom.xml");
            assertThat(pom).content().contains("<maven.compiler.target>11</maven.compiler.target>");
            assertThatCode(() -> x.setProperty(project, "maven.compiler.target", "stuff"))
                    .doesNotThrowAnyException();
            assertThat(pom).content().contains("<maven.compiler.target>stuff</maven.compiler.target>");
        }
    }

    @Test
    void getProperty(@TempDir Path tmp) throws IOException {
        Path project = copy(tmp);
        try (CommandLineBuild x = getJobExecutor()) {
            assertThat(x.getProperty(project, "maven.compiler.target"))
                    .isEqualTo("11");
        }
    }

    @Test
    void getVersion(@TempDir Path tmp) throws IOException {
        Path project = copy(tmp);
        try (CommandLineBuild x = getJobExecutor()) {
            assertThat(x.getVersion(project))
                    .hasToString("3.0.0");
        }
    }

    @Test
    void checkoutTag(@TempDir Path tmp) throws IOException {
        Path project = copy(tmp);
        try (CommandLineBuild x = getJobExecutor()) {
            x.checkoutTag(project, Tag.parse("/v2.4.0"));
            assertThat(project.resolve("pom.xml"))
                    .content().contains("<version>2.4.0</version>");
        }
    }

    @Test
    void getTags(@TempDir Path tmp) throws IOException {
        Path project = copy(tmp);
        try (CommandLineBuild x = getJobExecutor()) {
            assertThat(x.getTags(project))
                    .map(Tag::withoutDate)
                    .containsExactly(
                            Tag.parse("/v2.3.4"),
                            Tag.parse("/v2.4.0"),
                            Tag.parse("/v3.0.0")
                    );
        }
    }

    @Test
    void clone(@TempDir Path tmp) throws IOException {
        Path project = copy(tmp);
        Path clonedProject = tmp.resolve("clonedProject");
        createDirectory(clonedProject);
        try (CommandLineBuild x = getJobExecutor()) {
            x.clone(project.toUri(), clonedProject);
        }
        assertThat(clonedProject.resolve("pom.xml")).exists();
    }

    @Test
    void install(@TempDir Path tmp) {
    }

    @Test
    void close(@TempDir Path tmp) {
    }

    private static Path copy(Path tmp) throws IOException {
        Path target = tmp.resolve("project");
        Examples.copyFolder(sourceProject, target);
        return target;
    }

    private static CommandLineBuild getJobExecutor() throws IOException {
        return CommandLineBuild.getDefault();
    }

    private static void doNothing(Object ignore) {
    }
}