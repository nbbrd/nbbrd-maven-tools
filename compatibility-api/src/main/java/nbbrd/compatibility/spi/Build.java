package nbbrd.compatibility.spi;

import internal.compatibility.ResourceDefinition;
import lombok.NonNull;
import nbbrd.compatibility.Tag;
import nbbrd.compatibility.Version;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

@ResourceDefinition
public interface Build extends Closeable {

    void cleanAndRestore(@NonNull Path project) throws IOException;

    int verify(@NonNull Path project) throws IOException;

    void setProperty(@NonNull Path project, @NonNull String propertyName, @Nullable String propertyValue) throws IOException;

    @Nullable
    String getProperty(@NonNull Path project, @NonNull String propertyName) throws IOException;

    @NonNull
    Version getVersion(@NonNull Path project) throws IOException;

    void checkoutTag(@NonNull Path project, @NonNull Tag tag) throws IOException;

    @NonNull
    List<Tag> getTags(@NonNull Path project) throws IOException;

    void clone(@NonNull URI from, @NonNull Path to) throws IOException;
}
