package nbbrd.compatibility;

import internal.compatibility.NoOpJobEngine;
import internal.compatibility.NoOpVersioning;
import lombok.NonNull;
import nbbrd.compatibility.spi.*;
import nbbrd.design.StaticFactoryMethod;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@lombok.Value
@lombok.Builder(toBuilder = true)
public class Compatibility {

    @StaticFactoryMethod
    public static @NonNull Compatibility ofServiceLoader() {
        return Compatibility
                .builder()
                .engine(JobEngineLoader.load().orElse(NoOpJobEngine.INSTANCE))
                .formats(FormatLoader.load())
                .versionings(VersioningLoader.load())
                .build();
    }

    @lombok.Builder.Default
    JobEngine engine = NoOpJobEngine.INSTANCE;

    @lombok.Singular
    List<Format> formats;

    @lombok.Singular
    List<Versioning> versionings;

    public @NonNull Report execute(@NonNull Job job) throws IOException {
        try (JobExecutor executor = engine.getExecutor()) {
            Report.Builder result = Report.builder();
            for (Source source : job.getSources()) {
                Versioning sourceVersioning = getVersioning(source.getTagging().getVersioning()).orElse(NoOpVersioning.INSTANCE);
                String sourceVersion;
                if (isFileScheme(source.getUri())) {
                    Path sourcePath = Paths.get(source.getUri());
                    sourceVersion = executor.getVersion(sourcePath);
                    if (isSnapshot(sourceVersion)) {
                        executor.install(sourcePath);
                    }
                } else {
                    throw new IOException("WIP");
                }
                for (Target target : job.getTargets()) {
                    if (isFileScheme(target.getUri())) {
                        throw new IOException("WIP");
                    } else {
                        Path targetPath = job.getWorkingDir().resolve("target");
                        executor.clone(target.getUri(), targetPath);
                        List<String> tags = executor.getTags(targetPath);
                        for (String tag : tags) {
                            executor.checkoutTag(targetPath, tag);
                            String targetVersion = executor.getVersion(targetPath);
                            String originalVersion = executor.getProperty(targetPath, target.getBuilding().getProperty());
                            if (!isSkip(sourceVersioning, originalVersion, sourceVersion)) {
                                executor.setProperty(targetPath, target.getBuilding().getProperty(), sourceVersion);
                                int exitCode = executor.verify(targetPath);
                                result.item(ReportItem
                                        .builder()
                                        .exitCode(exitCode)
                                        .targetUri(target.getUri())
                                        .sourceVersion(sourceVersion)
                                        .targetVersion(targetVersion)
                                        .defaultVersion(originalVersion)
                                        .build());
                            }
                            executor.cleanAndRestore(targetPath);
                        }
                    }
                }
            }
            return result.build();
        }
    }

    private boolean isSnapshot(String sourceVersion) {
        return sourceVersion.endsWith("-SNAPSHOT");
    }

    private boolean isSkip(Versioning versioning, String from, String to) {
        return versioning != null && !versioning.isOrdered(from, to);
    }

    private Optional<Versioning> getVersioning(String id) {
        return versionings.stream()
                .filter(versioning -> versioning.getVersioningId().equals(id))
                .findFirst();
    }

    private static boolean isFileScheme(URI uri) {
        return uri.getScheme().equals("file");
    }
}
