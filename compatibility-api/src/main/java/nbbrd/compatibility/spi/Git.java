package nbbrd.compatibility.spi;

import lombok.NonNull;
import nbbrd.compatibility.Tag;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

public interface Git {

    void restore(@NonNull Path project) throws IOException;

    void checkoutTag(@NonNull Path project, @NonNull Tag tag) throws IOException;

    @NonNull
    List<Tag> getTags(@NonNull Path project) throws IOException;

    void clone(@NonNull URI from, @NonNull Path to) throws IOException;
}
