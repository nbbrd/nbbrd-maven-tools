package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Project;
import nbbrd.compatibility.Tag;
import nbbrd.compatibility.spi.Build;
import nbbrd.design.SealedType;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SealedType({SourceContext.class, TargetContext.class})
public interface ProjectContext {

    @NonNull
    URI getUri();

    @NonNull
    Path getDirectory();

    boolean isDeleteOnExit();

    @NonNull
    List<VersionContext> getVersions();

    default void clean() throws IOException {
        if (isDeleteOnExit()) {
            Files2.deleteRecursively(getDirectory());
        }
    }

    interface Builder<T extends Builder<T>> {

        T uri(@NonNull URI uri);

        T directory(@NonNull Path directory);

        T deleteOnExit(boolean deleteOnExit);

        T version(@NonNull VersionContext version);

        default T init(Project project, boolean local, Path workingDir, Build build) throws IOException {
            if (local) {
                Path directory = Paths.get(project.getUri());
                directory(directory).deleteOnExit(false);
                version(VersionContext.local(build.getVersion(directory)));
            } else {
                Path directory = Files.createTempDirectory(workingDir, "project");
                directory(directory).deleteOnExit(true);
                build.clone(project.getUri(), directory);
                for (Tag tag : build.getTags(directory)) {
                    if (project.getFilter().contains(tag)) {
                        build.checkoutTag(directory, tag);
                        version(VersionContext.remote(tag, build.getVersion(directory)));
                        build.clean(directory);
                        build.restore(directory);
                    }
                }
            }
            return uri(project.getUri());
        }
    }
}
