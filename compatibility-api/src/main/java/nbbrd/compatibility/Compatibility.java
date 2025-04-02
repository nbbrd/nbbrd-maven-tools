package nbbrd.compatibility;

import internal.compatibility.NoOpJobEngine;
import internal.compatibility.NoOpVersioning;
import lombok.NonNull;
import nbbrd.compatibility.spi.*;
import nbbrd.design.StaticFactoryMethod;
import nbbrd.io.function.IOFunction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

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
            return execute(
                    map(job.getSources(), source -> of(source, job.getWorkingDir(), executor)),
                    map(job.getTargets(), source1 -> of(source1, job.getWorkingDir(), executor)),
                    executor
            );
        }
    }

    private Report execute(List<SourceContext> sources, List<TargetContext> targets, JobExecutor executor) throws IOException {
        Report.Builder result = Report.builder();
        for (SourceContext source : sources) {
            for (VersionContext sourceVersion : source.getVersions()) {
                for (TargetContext target : targets) {
                    for (VersionContext targetVersion : target.getVersions()) {
                        executor.checkoutTag(target.getDirectory(), targetVersion.getTag());
                        Version originalVersion = Version.parse(executor.getProperty(target.getDirectory(), target.getBuilding().getProperty()));
                        if (!isSkip(source.getVersioning(), originalVersion, sourceVersion.getVersion())) {
                            executor.setProperty(target.getDirectory(), target.getBuilding().getProperty(), sourceVersion.getVersion().toString());
                            result.item(ReportItem
                                    .builder()
                                    .exitCode(executor.verify(target.getDirectory()))
                                    .sourceUri(source.getUri())
                                    .sourceVersion(sourceVersion.getVersion())
                                    .targetUri(target.getUri())
                                    .targetVersion(targetVersion.getVersion())
                                    .originalVersion(originalVersion)
                                    .build());
                        }
                        executor.cleanAndRestore(target.getDirectory());
                    }
                }
            }
        }
        return result.build();
    }

    @lombok.Value
    @lombok.Builder
    private static class VersionContext {

        @NonNull
        Tag tag;

        @NonNull
        Version version;
    }

    @lombok.Value
    @lombok.Builder
    private static class SourceContext {

        @NonNull
        URI uri;

        @NonNull
        Path directory;

        @lombok.Singular
        List<VersionContext> versions;

        @NonNull
        Versioning versioning;
    }

    @lombok.Value
    @lombok.Builder
    private static class TargetContext {

        @NonNull
        URI uri;

        @NonNull
        Path directory;

        @lombok.Singular
        List<VersionContext> versions;

        @NonNull
        Building building;
    }

    private SourceContext of(Source source, Path workingDir, JobExecutor executor) throws IOException {
        Versioning versioning = getVersioning(source.getTagging().getVersioning()).orElse(NoOpVersioning.INSTANCE);
        if (isFileScheme(source.getUri())) {
            Path directory = Paths.get(source.getUri());

            return SourceContext
                    .builder()
                    .uri(source.getUri())
                    .directory(directory)
                    .versioning(versioning)
                    .version(VersionContext
                            .builder()
                            .tag(Tag.NO_TAG)
                            .version(executor.getVersion(directory))
                            .build())
                    .build();
        } else {
            Path directory = workingDir.resolve(getDirectoryName(source.getUri()));
            executor.clone(source.getUri(), directory);

            SourceContext.Builder result = SourceContext
                    .builder()
                    .uri(source.getUri())
                    .directory(directory)
                    .versioning(versioning);
            for (Tag tag : executor.getTags(directory)) {
                executor.checkoutTag(directory, tag);
                result.version(VersionContext
                        .builder()
                        .tag(tag)
                        .version(executor.getVersion(directory))
                        .build());
                executor.cleanAndRestore(directory);
            }
            return result.build();
        }
    }

    private TargetContext of(Target target, Path workingDir, JobExecutor executor) throws IOException {
        if (isFileScheme(target.getUri())) {
            Path directory = Paths.get(target.getUri());

            return TargetContext
                    .builder()
                    .uri(target.getUri())
                    .directory(directory)
                    .building(target.getBuilding())
                    .version(VersionContext
                            .builder()
                            .tag(Tag.NO_TAG)
                            .version(executor.getVersion(directory))
                            .build())
                    .build();
        } else {
            Path directory = workingDir.resolve(getDirectoryName(target.getUri()));
            executor.clone(target.getUri(), directory);

            TargetContext.Builder result = TargetContext
                    .builder()
                    .uri(target.getUri())
                    .directory(directory)
                    .building(target.getBuilding());
            for (Tag tag : executor.getTags(directory)) {
                executor.checkoutTag(directory, tag);
                result.version(VersionContext
                        .builder()
                        .tag(tag)
                        .version(executor.getVersion(directory))
                        .build());
                executor.cleanAndRestore(directory);
            }
            return result.build();
        }
    }

    private boolean isSkip(Versioning versioning, Version from, Version to) {
        return versioning.getVersionComparator().compare(from, to) > 0;
    }

    private Optional<Versioning> getVersioning(String id) {
        return versionings.stream()
                .filter(versioning -> versioning.getVersioningId().equals(id))
                .findFirst();
    }

    private static boolean isFileScheme(URI uri) {
        return uri.getScheme().equals("file");
    }

    private static String getDirectoryName(URI uri) {
        return "dir_" + uri.toString().hashCode();
    }

    private static <X, Y> List<Y> map(List<X> sources, IOFunction<X, Y> mapping) throws IOException {
        try {
            return sources.stream().map(mapping.asUnchecked()).collect(toList());
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }
}
