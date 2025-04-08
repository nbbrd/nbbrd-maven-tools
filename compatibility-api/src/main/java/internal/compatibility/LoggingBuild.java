package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Tag;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Build;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

@lombok.RequiredArgsConstructor
public final class LoggingBuild implements Build {

    private final @NonNull Consumer<? super String> onEvent;
    private final @NonNull Build delegate;

    @Override
    public void close() throws IOException {
        onEvent.accept("closing build");
        delegate.close();
    }

    @Override
    public void restore(@NonNull Path project) throws IOException {
        onEvent.accept("restoring " + project);
        delegate.restore(project);
    }

    @Override
    public void checkoutTag(@NonNull Path project, @NonNull Tag tag) throws IOException {
        onEvent.accept("checking out tag " + tag + " in " + project);
        delegate.checkoutTag(project, tag);
    }

    @Override
    public @NonNull List<Tag> getTags(@NonNull Path project) throws IOException {
        onEvent.accept("getting tags for " + project);
        return delegate.getTags(project);
    }

    @Override
    public void clone(@NonNull URI from, @NonNull Path to) throws IOException {
        onEvent.accept("cloning " + from + " to " + to);
        delegate.clone(from, to);
    }

    @Override
    public void clean(@NonNull Path project) throws IOException {
        onEvent.accept("cleaning " + project);
        delegate.clean(project);
    }

    @Override
    public int verify(@NonNull Path project) throws IOException {
        onEvent.accept("verifying " + project);
        return delegate.verify(project);
    }

    @Override
    public void setProperty(@NonNull Path project, @NonNull String propertyName, @Nullable String propertyValue) throws IOException {
        onEvent.accept("setting property " + propertyName + " to " + propertyValue + " in " + project);
        delegate.setProperty(project, propertyName, propertyValue);
    }

    @Override
    public @Nullable String getProperty(@NonNull Path project, @NonNull String propertyName) throws IOException {
        onEvent.accept("getting property " + propertyName + " from " + project);
        return delegate.getProperty(project, propertyName);
    }

    @Override
    public @NonNull Version getVersion(@NonNull Path project) throws IOException {
        onEvent.accept("getting version from " + project);
        return delegate.getVersion(project);
    }
}
