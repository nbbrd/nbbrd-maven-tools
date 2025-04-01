package nbbrd.compatibility;

import internal.compatibility.NoOpJobEngine;
import lombok.NonNull;
import nbbrd.compatibility.spi.JobEngine;
import nbbrd.compatibility.spi.JobEngineLoader;
import nbbrd.compatibility.spi.JobExecutor;
import nbbrd.design.StaticFactoryMethod;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@lombok.Value
@lombok.Builder
public class Compatibility {

    @StaticFactoryMethod
    public static @NonNull Compatibility ofServiceLoader() {
        return Compatibility
                .builder()
                .engine(JobEngineLoader.load().orElse(NoOpJobEngine.INSTANCE))
                .build();
    }

    @lombok.Builder.Default
    JobEngine engine = NoOpJobEngine.INSTANCE;

    public @NonNull Report execute(@NonNull Job job) throws IOException {
        try (JobExecutor executor = engine.getExecutor()) {
            Report.Builder result = Report.builder();
            for (Source source : job.getSources()) {
                String sourceVersion;
                if (isFileScheme(source.getUri())) {
                    Path sourcePath = Paths.get(source.getUri());
                    sourceVersion = executor.getVersion(sourcePath);
                    executor.install(sourcePath);
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
                            String defaultVersion = executor.getProperty(targetPath, target.getBuilding().getProperty());
                            executor.setProperty(targetPath, target.getBuilding().getProperty(), sourceVersion);
                            int exitCode = executor.verify(targetPath);
                            result.item(ReportItem
                                    .builder()
                                    .exitCode(exitCode)
                                    .targetUri(target.getUri())
                                    .sourceVersion(sourceVersion)
                                    .targetVersion(targetVersion)
                                    .defaultVersion(defaultVersion)
                                    .build());
                            executor.cleanAndRestore(targetPath);
                        }
                    }
                }
            }
            return result.build();
        }
    }

    private static boolean isFileScheme(URI uri) {
        return uri.getScheme().equals("file");
    }
}
