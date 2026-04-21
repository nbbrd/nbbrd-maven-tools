package internal.compatibility.spi;

import nbbrd.compatibility.Artifact;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Interceptor;
import nbbrd.compatibility.spi.Maven;
import nbbrd.service.ServiceProvider;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

@ServiceProvider
public enum BaseInterceptors implements Interceptor {

    LOMBOK {
        @Override
        public @Nullable String onErrorMessage(@NonNull Maven maven, @NonNull Path project, @NonNull String errorMessage,
                                               @NonNull Consumer<? super String> onEvent, @NonNull Consumer<? super String> onDebug) throws IOException {
            if (isLombokError(errorMessage)) {
                onEvent.accept("Lombok annotation handler failed, likely due to outdated Lombok dependency.");
                Version latestLombok = maven.getArtifactLatestRelease(Artifact.parse("org.projectlombok:lombok:::1.0.0"));
                if (latestLombok != null) {
                    onEvent.accept("Retrying with Lombok version " + latestLombok);
                    maven.setArtifactVersion(project, Artifact.parse("org.projectlombok:lombok"), latestLombok);
                    return maven.verify(project);
                } else {
                    onEvent.accept("Failed to retrieve latest Lombok version, cannot recover.");
                }
            }
            return errorMessage;
        }

        private boolean isLombokError(String errorMessage) {
            return errorMessage.contains("Lombok annotation handler class lombok.javac.handlers.HandleBuilder failed");
        }
    }
}
