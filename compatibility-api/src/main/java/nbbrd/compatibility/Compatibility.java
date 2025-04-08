package nbbrd.compatibility;

import internal.compatibility.Broker;
import internal.compatibility.NoOpBuilder;
import lombok.NonNull;
import nbbrd.compatibility.spi.*;
import nbbrd.design.MightBePromoted;
import nbbrd.design.StaticFactoryMethod;
import nbbrd.io.function.IOFunction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.Consumer;

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

    @lombok.NonNull
    @lombok.Builder.Default
    Consumer<? super String> onEvent = ignore -> {
    };

    public @NonNull Report check(@NonNull Job job) throws IOException {
        onEvent.accept("Using builder " + builder.getBuilderId());
        try (Build build = builder.getBuild()) {
            Report result = check(
                    build,
                    map(job.getSources(), source -> initSource(source, job.getWorkingDir(), build)),
                    map(job.getTargets(), target -> initTarget(target, job.getWorkingDir(), build))
            );
            onEvent.accept("Report created with " + result.getItems().size() + " items");
            return result;
        }
    }

    private SourceContext initSource(Source source, Path workingDir, Build build) throws IOException {
        SourceContext.Builder result = SourceContext
                .builder()
                .uri(source.getUri())
                .versioning(SourceContext.resolveVersioning(source, versionings));

        if (isFileScheme(source.getUri())) {
            Path directory = Paths.get(source.getUri());
            onEvent.accept("Initializing local source from " + directory);
            result.directory(directory).deleteOnExit(false);
            result.version(VersionContext.local(build.getVersion(directory)));
        } else {
            Path directory = Files.createTempDirectory(workingDir, "source");
            onEvent.accept("Initializing remote source to " + directory);
            result.directory(directory).deleteOnExit(true);
            build.clone(source.getUri(), directory);
            for (Tag tag : build.getTags(directory)) {
                if (source.getFilter().contains(tag)) {
                    build.checkoutTag(directory, tag);
                    result.version(VersionContext.remote(tag, build.getVersion(directory)));
                    build.clean(directory);
                    build.restore(directory);
                }
            }
        }

        return result.build();
    }

    private TargetContext initTarget(Target target, Path workingDir, Build build) throws IOException {
        TargetContext.Builder result = TargetContext
                .builder()
                .uri(target.getUri())
                .building(target.getBuilding())
                .broker(TargetContext.resolveBroker(target));

        if (isFileScheme(target.getUri())) {
            Path directory = Paths.get(target.getUri());
            onEvent.accept("Initializing local target from " + directory);
            result.directory(directory).deleteOnExit(false);
            result.version(VersionContext.local(build.getVersion(directory)));
        } else {
            Path directory = Files.createTempDirectory(workingDir, "target");
            onEvent.accept("Initializing remote target to " + directory);
            result.directory(directory).deleteOnExit(true);
            build.clone(target.getUri(), directory);
            for (Tag tag : build.getTags(directory)) {
                if (target.getFilter().contains(tag)) {
                    build.checkoutTag(directory, tag);
                    result.version(VersionContext.remote(tag, build.getVersion(directory)));
                    build.clean(directory);
                    build.restore(directory);
                }
            }
        }

        return result.build();
    }

    private Report check(Build build, List<SourceContext> sources, List<TargetContext> targets) throws IOException {
        long count = sources.stream().mapToLong(o -> o.getVersions().size()).sum()
                * targets.stream().mapToLong(o -> o.getVersions().size()).sum();
        int index = 0;
        Report.Builder result = Report.builder();
        for (SourceContext source : sources) {
            for (VersionContext sourceVersion : source.getVersions()) {
                for (TargetContext target : targets) {
                    for (VersionContext targetVersion : target.getVersions()) {
                        onEvent.accept("Checking " + ++index + "/" + count + " " + source.getUri() + "@" + sourceVersion.getVersion() + " -> " + target.getUri() + "@" + targetVersion.getVersion());
                        result.item(check(build, source, sourceVersion, target, targetVersion));
                    }
                }
            }
        }
        deleteTemporaryDirectories(sources, targets);
        return result.build();
    }

    private ReportItem check(Build build, SourceContext source, VersionContext sourceVersion, TargetContext target, VersionContext targetVersion) throws IOException {
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

    private void deleteTemporaryDirectories(List<SourceContext> sources, List<TargetContext> targets) throws IOException {
        for (SourceContext source : sources) {
            if (source.isDeleteOnExit()) {
                deleteRecursively(source.getDirectory());
            }
        }
        for (TargetContext target : targets) {
            if (target.isDeleteOnExit()) {
                deleteRecursively(target.getDirectory());
            }
        }
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

        boolean deleteOnExit;

        @lombok.Singular
        List<VersionContext> versions;

        @NonNull
        Versioning versioning;

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

        boolean deleteOnExit;

        @lombok.Singular
        List<VersionContext> versions;

        @NonNull
        Broker broker;

        @NonNull
        Building building;

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

    @MightBePromoted
    private static boolean isFileScheme(URI uri) {
        return "file".equals(uri.getScheme());
    }

    @MightBePromoted
    private static <X, Y> List<Y> map(List<X> sources, IOFunction<X, Y> mapping) throws IOException {
        try {
            return sources.stream().map(mapping.asUnchecked()).collect(toList());
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    @MightBePromoted
    private static void deleteRecursively(Path source) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return super.visitFile(file, attrs);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return super.postVisitDirectory(dir, exc);
            }
        });
    }
}
