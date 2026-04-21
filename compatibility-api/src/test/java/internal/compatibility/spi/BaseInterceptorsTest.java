package internal.compatibility.spi;

import nbbrd.compatibility.Artifact;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Maven;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaseInterceptorsTest {

    @Test
    void lombokInterceptorRecoversFromLombokErrorWhenLatestVersionAvailable() throws IOException {
        List<String> events = new ArrayList<>();
        MockMaven maven = new MockMaven();
        maven.latestLombokVersion = Version.parse("1.18.30");

        String errorMessage = "Lombok annotation handler class lombok.javac.handlers.HandleBuilder failed on Sample.java";
        String result = BaseInterceptors.LOMBOK.onErrorMessage(
                maven,
                Paths.get("project"),
                errorMessage,
                events::add,
                ignore -> {}
        );

        assertThat(result).isNull();
        assertThat(events).containsExactly(
                "Lombok annotation handler failed, likely due to outdated Lombok dependency.",
                "Retrying with Lombok version 1.18.30"
        );
        assertThat(maven.updatedArtifacts).containsExactly("org.projectlombok:lombok:::");
        assertThat(maven.updatedVersions).containsExactly("1.18.30");
        assertThat(maven.verifyCallCount).isEqualTo(1);
    }

    @Test
    void lombokInterceptorReturnsErrorWhenLatestVersionNotAvailable() throws IOException {
        List<String> events = new ArrayList<>();
        MockMaven maven = new MockMaven();
        maven.latestLombokVersion = null;

        String errorMessage = "Lombok annotation handler class lombok.javac.handlers.HandleBuilder failed on Sample.java";
        String result = BaseInterceptors.LOMBOK.onErrorMessage(
                maven,
                Paths.get("project"),
                errorMessage,
                events::add,
                ignore -> {}
        );

        assertThat(result).isEqualTo(errorMessage);
        assertThat(events).containsExactly(
                "Lombok annotation handler failed, likely due to outdated Lombok dependency.",
                "Failed to retrieve latest Lombok version, cannot recover."
        );
        assertThat(maven.updatedArtifacts).isEmpty();
        assertThat(maven.verifyCallCount).isEqualTo(0);
    }

    @Test
    void lombokInterceptorReturnsErrorWhenRetryFails() throws IOException {
        List<String> events = new ArrayList<>();
        MockMaven maven = new MockMaven();
        maven.latestLombokVersion = Version.parse("1.18.30");
        maven.verifyResult = "Build still fails";

        String errorMessage = "Lombok annotation handler class lombok.javac.handlers.HandleBuilder failed on Sample.java";
        String result = BaseInterceptors.LOMBOK.onErrorMessage(
                maven,
                Paths.get("project"),
                errorMessage,
                events::add,
                ignore -> {}
        );

        assertThat(result).isEqualTo("Build still fails");
        assertThat(events).containsExactly(
                "Lombok annotation handler failed, likely due to outdated Lombok dependency.",
                "Retrying with Lombok version 1.18.30"
        );
        assertThat(maven.verifyCallCount).isEqualTo(1);
    }

    @Test
    void lombokInterceptorIgnoresNonLombokErrors() throws IOException {
        List<String> events = new ArrayList<>();
        MockMaven maven = new MockMaven();

        String errorMessage = "Compilation failure: cannot find symbol";
        String result = BaseInterceptors.LOMBOK.onErrorMessage(
                maven,
                Paths.get("project"),
                errorMessage,
                events::add,
                ignore -> {}
        );

        assertThat(result).isEqualTo(errorMessage);
        assertThat(events).isEmpty();
        assertThat(maven.verifyCallCount).isEqualTo(0);
    }

    @Test
    void lombokInterceptorHandlesPartialLombokErrorMessage() throws IOException {
        List<String> events = new ArrayList<>();
        MockMaven maven = new MockMaven();
        maven.latestLombokVersion = Version.parse("1.18.30");

        String errorMessage = "[ERROR] Some context Lombok annotation handler class lombok.javac.handlers.HandleBuilder failed more context";
        String result = BaseInterceptors.LOMBOK.onErrorMessage(
                maven,
                Paths.get("project"),
                errorMessage,
                events::add,
                ignore -> {}
        );

        assertThat(result).isNull();
        assertThat(events).hasSize(2);
    }

    @Test
    void lombokInterceptorPropagatesIOException() {
        MockMaven maven = new MockMaven();
        maven.throwOnGetLatestRelease = true;

        String errorMessage = "Lombok annotation handler class lombok.javac.handlers.HandleBuilder failed on Sample.java";

        assertThat(maven).satisfies(m -> {
            try {
                BaseInterceptors.LOMBOK.onErrorMessage(
                        m,
                        Paths.get("project"),
                        errorMessage,
                        ignore -> {},
                        ignore -> {}
                );
            } catch (IOException e) {
                assertThat(e).hasMessage("Simulated IO error");
            }
        });
    }

    private static class MockMaven implements Maven {
        Version latestLombokVersion;
        String verifyResult = null;
        boolean throwOnGetLatestRelease = false;
        int verifyCallCount = 0;
        List<String> updatedArtifacts = new ArrayList<>();
        List<String> updatedVersions = new ArrayList<>();

        @Override
        public void clean(@NonNull Path project) {
        }

        @Override
        public String verify(@NonNull Path project) {
            verifyCallCount++;
            return verifyResult;
        }

        @Override
        public @NonNull Version getProjectVersion(@NonNull Path project) {
            throw new RuntimeException();
        }

        @Override
        public Version getArtifactVersion(@NonNull Path project, @NonNull Artifact artifact) {
            return null;
        }

        @Override
        public void setArtifactVersion(@NonNull Path project, @NonNull Artifact artifact, @NonNull Version version) {
            updatedArtifacts.add(artifact.toString());
            updatedVersions.add(version.toString());
        }

        @Override
        public Version getArtifactLatestRelease(@NonNull Artifact artifact) throws IOException {
            if (throwOnGetLatestRelease) {
                throw new IOException("Simulated IO error");
            }
            return latestLombokVersion;
        }
    }
}


