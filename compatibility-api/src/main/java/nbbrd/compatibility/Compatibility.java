package nbbrd.compatibility;

import internal.compatibility.*;
import internal.compatibility.spi.NoOpBuilder;
import lombok.NonNull;
import nbbrd.compatibility.spi.*;
import nbbrd.design.MightBePromoted;
import nbbrd.design.StaticFactoryMethod;
import nbbrd.io.sys.SystemProperties;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static internal.compatibility.IOStreams.*;
import static java.lang.String.format;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
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

    @lombok.NonNull
    @lombok.Builder.Default
    Path workingDir = requireNonNull(SystemProperties.DEFAULT.getJavaIoTmpdir());

    public @NonNull Report check(@NonNull Job job) throws IOException {
        if (job.getSources().isEmpty()) {
            onEvent.accept("No source provided");
            return Report.EMPTY;
        }
        if (job.getTargets().isEmpty()) {
            onEvent.accept("No target provided");
            return Report.EMPTY;
        }
        onEvent.accept("Using builder " + builder.getBuilderId());
        try (Build build = builder.getBuild()) {
            Report result = check(
                    build,
                    collectWithIO(job.getSources(), mappingWithIO(source -> initSource(source, build), toList())),
                    collectWithIO(job.getTargets(), mappingWithIO(target -> initTarget(target, build), toList()))
            );
            onEvent.accept("Report created with " + result.getItems().size() + " items");
            return result;
        }
    }

    private SourceContext initSource(Source source, Build build) throws IOException {
        boolean local = isFileScheme(source.getUri());
        onEvent.accept(format(ROOT, "Initializing %s source %s", local ? "local" : "remote", source.getUri()));
        return SourceContext
                .builder()
                .init(source, local, workingDir, build)
                .versioning(resolveVersioning(source))
                .build();
    }

    private Versioning resolveVersioning(Source source) throws IOException {
        return versionings
                .stream()
                .filter(item -> item.getVersioningId().equals(source.getVersioning()))
                .findFirst()
                .orElseThrow(() -> new IOException("Cannot resolve versioning: " + source.getVersioning()));
    }

    private TargetContext initTarget(Target target, Build build) throws IOException {
        boolean local = isFileScheme(target.getUri());
        onEvent.accept(format(ROOT, "Initializing %s target %s", local ? "local" : "remote", target.getUri()));
        return TargetContext
                .builder()
                .init(target, local, workingDir, build)
                .broker(resolveBroker(target))
                .build();
    }

    private Broker resolveBroker(Target target) throws IOException {
        String property = target.getProperty();
        if (property == null || property.isEmpty()) {
            throw new IOException("Cannot resolve broker: target property is empty");
        }
        return new Broker.PropertyBroker(property);
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
                        onEvent.accept("Checking " + ++index + "/" + count + " " + ReportItem.toLabel(source.getUri(), sourceVersion.getVersion()) + " -> " + ReportItem.toLabel(target.getUri(), targetVersion.getVersion()));
                        result.item(check(build, source, sourceVersion, target, targetVersion));
                    }
                }
            }
        }
        forEachWithIO(sources, ProjectContext::clean);
        forEachWithIO(targets, ProjectContext::clean);
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

    private static boolean isSkip(Versioning versioning, Version from, Version to) {
        return versioning.getVersionComparator().compare(from, to) > 0;
    }

    @MightBePromoted
    private static boolean isFileScheme(URI uri) {
        return "file".equals(uri.getScheme());
    }

    private <T> Optional<Formatter<T>> getFormatter(Class<T> type, Predicate<? super Format> filter) {
        return getFormats()
                .stream()
                .filter(filter)
                .filter(format -> format.canFormat(type))
                .map(format -> format.getFormatter(type))
                .findFirst();
    }

    public @NonNull <T> Optional<Formatter<T>> getFormatterById(@NonNull Class<T> type, @NonNull String id) {
        return getFormatter(type, onId(id));
    }

    public @NonNull <T> Optional<Formatter<T>> getFormatterByFile(@NonNull Class<T> type, @NonNull Path file) {
        return getFormatter(type, onFile(file));
    }

    private <T> Optional<Parser<T>> getParser(Class<T> type, Predicate<? super Format> filter) {
        return getFormats()
                .stream()
                .filter(filter)
                .filter(format -> format.canParse(type))
                .map(format -> format.getParser(type))
                .findFirst();
    }

    public @NonNull <T> Optional<Parser<T>> getParserById(@NonNull Class<T> type, @NonNull String id) {
        return getParser(type, onId(id));
    }

    public @NonNull <T> Optional<Parser<T>> getParserByFile(@NonNull Class<T> type, @NonNull Path file) {
        return getParser(type, onFile(file));
    }

    public @NonNull <T> DirectoryStream.Filter<? super Path> getParserFilter(@NonNull Class<T> type) {
        return file -> getFormats().stream().filter(format -> format.canParse(type)).anyMatch(onFile(file));
    }

    public @NonNull Report mergeReports(@NonNull List<Report> list) {
        return Report
                .builder()
                .items(list.stream().flatMap(report -> report.getItems().stream()).collect(toList()))
                .build();
    }

    public @NonNull List<Job> splitJob(@NonNull Job job) {
        return job.getTargets()
                .stream()
                .map(target -> Job.builder().sources(job.getSources()).target(target).build())
                .collect(toList());
    }

    private static Predicate<Format> onId(String id) {
        return format -> format.getFormatId().equals(id);
    }

    private static Predicate<Format> onFile(Path file) {
        return format -> {
            try {
                return format.getFormatFileFilter().accept(file);
            } catch (IOException e) {
                return false;
            }
        };
    }
}
