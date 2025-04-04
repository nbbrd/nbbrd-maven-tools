package nbbrd.compatibility;

import internal.compatibility.Broker;
import internal.compatibility.NoOpBuilder;
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
                    map(job.getSources(), source -> SourceContext.init(source, job.getWorkingDir(), build, versionings)),
                    map(job.getTargets(), target -> TargetContext.init(target, job.getWorkingDir(), build))
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
        if (targetVersion.requiresCheckout()) {
            build.checkoutTag(project, targetVersion.getTag());
        }
        if (!isSkip(source.getVersioning(), target.getBroker().getVersion(build, project), sourceVersion.getVersion())) {
            target.getBroker().setVersion(build, project, sourceVersion.getVersion());
            result.exitStatus(build.verify(project) == 0 ? ExitStatus.VERIFIED : ExitStatus.BROKEN);
            build.clean(project);
        } else {
            result.exitStatus(ExitStatus.SKIPPED);
        }
        if (targetVersion.requiresCheckout()) {
            build.restore(project);
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

        public boolean requiresCheckout() {
            return !tag.equals(Tag.NO_TAG);
        }

        @StaticFactoryMethod
        public static VersionContext local(Version version) {
            return VersionContext
                    .builder()
                    .tag(Tag.NO_TAG)
                    .version(version)
                    .build();
        }

        @StaticFactoryMethod
        public static VersionContext remote(Tag tag, Version version) {
            return VersionContext
                    .builder()
                    .tag(tag)
                    .version(version)
                    .build();
        }
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

        @StaticFactoryMethod
        public static SourceContext init(Source source, Path workingDir, Build build, List<Versioning> versioningList) throws IOException {
            SourceContext.Builder result = SourceContext
                    .builder()
                    .uri(source.getUri())
                    .versioning(resolveVersioning(source, versioningList));

            if (isFileScheme(source.getUri())) {
                Path directory = Paths.get(source.getUri());
                result.directory(directory);
                result.version(VersionContext.local(build.getVersion(directory)));
            } else {
                Path directory = workingDir.resolve(getDirectoryName(source.getUri()));
                result.directory(directory);
                build.clone(source.getUri(), directory);
                for (Tag tag : build.getTags(directory)) {
                    build.checkoutTag(directory, tag);
                    result.version(VersionContext.remote(tag, build.getVersion(directory)));
                    build.clean(directory);
                    build.restore(directory);
                }
            }

            return result.build();
        }

        private static Versioning resolveVersioning(Source source, List<Versioning> list) throws IOException {
            return list
                    .stream()
                    .filter(item -> item.getVersioningId().equals(source.getVersioning()))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Cannot resolve versioning: " + source.getVersioning()));
        }
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
        Broker broker;

        @NonNull
        Building building;

        @StaticFactoryMethod
        public static TargetContext init(Target target, Path workingDir, Build build) throws IOException {
            TargetContext.Builder result = TargetContext
                    .builder()
                    .uri(target.getUri())
                    .building(target.getBuilding())
                    .broker(resolveBroker(target));

            if (isFileScheme(target.getUri())) {
                Path directory = Paths.get(target.getUri());
                result.directory(directory);
                result.version(VersionContext.local(build.getVersion(directory)));
            } else {
                Path directory = workingDir.resolve(getDirectoryName(target.getUri()));
                result.directory(directory);
                build.clone(target.getUri(), directory);
                for (Tag tag : build.getTags(directory)) {
                    build.checkoutTag(directory, tag);
                    result.version(VersionContext.remote(tag, build.getVersion(directory)));
                    build.clean(directory);
                    build.restore(directory);
                }
            }

            return result.build();
        }

        private static Broker resolveBroker(Target target) throws IOException {
            String property = target.getProperty();
            if (property.isEmpty()) {
                throw new IOException("Cannot resolve broker: target property is empty");
            }
            return new Broker.PropertyBroker(property);
        }
    }

    private static boolean isSkip(Versioning versioning, Version from, Version to) {
        return versioning.getVersionComparator().compare(from, to) > 0;
    }

    private static boolean isFileScheme(URI uri) {
        return "file".equals(uri.getScheme());
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
