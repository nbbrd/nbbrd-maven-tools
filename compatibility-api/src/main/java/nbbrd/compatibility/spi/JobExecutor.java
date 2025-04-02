package nbbrd.compatibility.spi;

import nbbrd.compatibility.Tag;
import nbbrd.compatibility.Version;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

public interface JobExecutor extends Closeable {

    void cleanAndRestore(Path project) throws IOException;

    int verify(Path project) throws IOException;

    void setProperty(Path project, String propertyName, String propertyValue) throws IOException;

    String getProperty(Path project, String propertyName) throws IOException;

    Version getVersion(Path project) throws IOException;

    void checkoutTag(Path project, Tag tag) throws IOException;

    List<Tag> getTags(Path project) throws IOException;

    void clone(URI from, Path to) throws IOException;

    void install(Path project) throws IOException;
}
