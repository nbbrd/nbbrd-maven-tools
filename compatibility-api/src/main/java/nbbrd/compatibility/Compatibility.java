package nbbrd.compatibility;

import internal.compatibility.NoOpBuilder;
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
                .builder(BuilderLoader.load().orElse(NoOpBuilder.INSTANCE))
                .formats(FormatLoader.load())
                .versionings(VersioningLoader.load())
                .build();
    }

    @lombok.Builder.Default
    nbbrd.compatibility.spi.Builder builder = NoOpBuilder.INSTANCE;

    @lombok.Singular
    List<Format> formats;

    @lombok.Singular
    List<Versioning> versionings;

    public @NonNull Report execute(@NonNull Job job) throws IOException {
        try (Build build = builder.getBuild()) {
            return execute(
                    build,
                    map(job.getSources(), source -> of(source, job.getWorkingDir(), build)),
                    map(job.getTargets(), target -> of(target, job.getWorkingDir(), build))
            );
        }
    }

    private Report execute(Build build, List<SourceContext> sources, List<TargetContext> targets) throws IOException {
        Report.Builder result = Report.builder();
        for (SourceContext source : sources) {
            for (VersionContext sourceVersion : source.getVersions()) {
                for (TargetContext target : targets) {
                    for (VersionContext targetVersion : target.getVersions()) {
                        result.item(execute(build, source, sourceVersion, target, targetVersion));
                    }
                }
            }
        }
        return result.build();
    }

    private ReportItem execute(Build build, SourceContext source, VersionContext sourceVersion, TargetContext target, VersionContext targetVersion) throws IOException {
        ReportItem.Builder result = ReportItem
                .builder()
                .sourceUri(source.getUri())
                .sourceVersion(sourceVersion.getVersion())
                .targetUri(target.getUri())
                .targetVersion(targetVersion.getVersion());
        Path project = target.getDirectory();
        Tag tag = targetVersion.getTag();
        if (!Tag.NO_TAG.equals(tag)) {
            build.checkoutTag(project, tag);
        }
        String propertyValue = build.getProperty(project, target.getBuilding().getProperty());
        Version originalVersion = propertyValue != null ? Version.parse(propertyValue) : Version.NO_VERSION;
        if (!isSkip(source.getVersioning(), originalVersion, sourceVersion.getVersion())) {
            build.setProperty(project, target.getBuilding().getProperty(), sourceVersion.getVersion().toString());
            result.exitStatus(build.verify(project) == 0 ? ExitStatus.VERIFIED : ExitStatus.BROKEN);
        } else {
            result.exitStatus(ExitStatus.SKIPPED);
        }
        if (!Tag.NO_TAG.equals(tag)) {
            build.cleanAndRestore(project);
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

    private SourceContext of(Source source, Path workingDir, Build build) throws IOException {
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
                            .version(build.getVersion(directory))
                            .build())
                    .build();
        } else {
            Path directory = workingDir.resolve(getDirectoryName(source.getUri()));
            build.clone(source.getUri(), directory);

            SourceContext.Builder result = SourceContext
                    .builder()
                    .uri(source.getUri())
                    .directory(directory)
                    .versioning(versioning);
            for (Tag tag : build.getTags(directory)) {
                build.checkoutTag(directory, tag);
                result.version(VersionContext
                        .builder()
                        .tag(tag)
                        .version(build.getVersion(directory))
                        .build());
                build.cleanAndRestore(directory);
            }
            return result.build();
        }
    }

    private TargetContext of(Target target, Path workingDir, Build build) throws IOException {
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
                            .version(build.getVersion(directory))
                            .build())
                    .build();
        } else {
            Path directory = workingDir.resolve(getDirectoryName(target.getUri()));
            build.clone(target.getUri(), directory);

            TargetContext.Builder result = TargetContext
                    .builder()
                    .uri(target.getUri())
                    .directory(directory)
                    .building(target.getBuilding());
            for (Tag tag : build.getTags(directory)) {
                build.checkoutTag(directory, tag);
                result.version(VersionContext
                        .builder()
                        .tag(tag)
                        .version(build.getVersion(directory))
                        .build());
                build.cleanAndRestore(directory);
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
