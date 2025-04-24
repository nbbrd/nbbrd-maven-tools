package nbbrd.compatibility.spi;

import lombok.NonNull;
import nbbrd.compatibility.Artifact;
import nbbrd.compatibility.Version;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public interface Maven {

    void clean(@NonNull Path project) throws IOException;

    int verify(@NonNull Path project) throws IOException;

    void setProperty(@NonNull Path project, @NonNull String propertyName, @Nullable String propertyValue) throws IOException;

    @Nullable
    String getProperty(@NonNull Path project, @NonNull String propertyName) throws IOException;

    @NonNull
    Version getVersion(@NonNull Path project) throws IOException;

    @Nullable
    Version getArtifactVersion(@NonNull Path project, @NonNull Artifact artifact) throws IOException;

    void setArtifactVersion(@NonNull Path project, @NonNull Artifact artifact, @NonNull Version version) throws IOException;
}
