package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Project;
import nbbrd.compatibility.Ref;
import nbbrd.compatibility.RefVersion;
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
    List<RefVersion> getVersions();

    default void clean() throws IOException {
        if (isDeleteOnExit()) {
            Files2.deleteRecursively(getDirectory());
        }
    }

    interface Builder<T extends Builder<T>> {

        T uri(@NonNull URI uri);

        T directory(@NonNull Path directory);

        T deleteOnExit(boolean deleteOnExit);

        T version(@NonNull RefVersion version);

        default T init(Project project, boolean local, Path workingDir, Build build) throws IOException {
            if (local) {
                Path directory = Paths.get(project.getUri());
                directory(directory).deleteOnExit(false);
                version(RefVersion.local(build.getVersion(directory)));
            } else {
                Path directory = Files.createTempDirectory(workingDir, "project");
                directory(directory).deleteOnExit(true);
                build.clone(project.getUri(), directory);
                for (Ref ref : project.getFilter().apply(build.getTags(directory))) {
                    build.checkoutTag(directory, ref);
                    version(RefVersion.remote(build.getVersion(directory), ref));
                }
            }
            return uri(project.getUri());
        }
    }
}
