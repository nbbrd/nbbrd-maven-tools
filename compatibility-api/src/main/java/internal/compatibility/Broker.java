package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Artifact;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Maven;
import nbbrd.design.SealedType;

import java.io.IOException;
import java.nio.file.Path;

@SealedType({Broker.ByArtifact.class})
public abstract class Broker {

    private Broker() {
        // sealed class
    }

    public abstract @NonNull Version getVersion(@NonNull Maven maven, @NonNull Path project) throws IOException;

    public abstract void setVersion(@NonNull Maven maven, @NonNull Path project, @NonNull Version version) throws IOException;

    @lombok.Value
    @lombok.EqualsAndHashCode(callSuper = false)
    public static class ByArtifact extends Broker {

        @NonNull
        Artifact artifact;

        @Override
        public @NonNull Version getVersion(@NonNull Maven maven, @NonNull Path project) throws IOException {
            Version result = maven.getArtifactVersion(project, artifact);
            return result != null ? result : Version.NO_VERSION;
        }

        @Override
        public void setVersion(@NonNull Maven maven, @NonNull Path project, @NonNull Version version) throws IOException {
            maven.setArtifactVersion(project, artifact, version);
        }
    }
}
