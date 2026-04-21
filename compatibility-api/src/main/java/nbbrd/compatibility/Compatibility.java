package nbbrd.compatibility;

import internal.compatibility.Broker;
import internal.compatibility.ProjectContext;
import internal.compatibility.SourceContext;
import internal.compatibility.TargetContext;
import internal.compatibility.spi.NoOpBuilder;
import lombok.NonNull;
import nbbrd.compatibility.spi.*;
import nbbrd.design.MightBePromoted;
import nbbrd.design.StaticFactoryMethod;
import nbbrd.io.sys.SystemProperties;
import org.jspecify.annotations.Nullable;

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
import static nbbrd.compatibility.ExitStatus.*;

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
                .interceptors(InterceptorLoader.load())
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
    Consumer<? super String> onDebug = ignore -> {
    };

    @lombok.NonNull
    @lombok.Builder.Default
    Path workingDir = requireNonNull(SystemProperties.DEFAULT.getJavaIoTmpdir());

    @lombok.Singular
    List<Interceptor> interceptors;

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
        try (Build build = builder.getBuild(onDebug)) {
            Report result = checkAll(
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
                .broker(resolveBroker(source))
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
                .logErrors(target.isLogErrors())
                .build();
    }

    private Broker resolveBroker(Source source) throws IOException {
        String binding = source.getBinding();
        if (binding != null) return new Broker.ByArtifact(Artifact.parse(binding));
        throw new IOException("Cannot resolve broker");
    }

    private Report checkAll(Build build, List<SourceContext> sources, List<TargetContext> targets) throws IOException {
        long count = sources.stream().mapToLong(o -> o.getVersions().size()).sum()
                * targets.stream().mapToLong(o -> o.getVersions().size()).sum();
        int index = 0;
        Report.Builder result = Report.builder();
        for (SourceContext source : sources) {
            for (RefVersion sourceVersion : source.getVersions()) {
                for (TargetContext target : targets) {
                    for (RefVersion targetVersion : target.getVersions()) {
                        onEvent.accept("Checking " + ++index + "/" + count + " " + ReportItem.toLabel(source.getUri(), sourceVersion) + " -> " + ReportItem.toLabel(target.getUri(), targetVersion));
                        result.item(checkItem(build, source, sourceVersion, target, targetVersion));
                    }
                }
            }
        }
        forEachWithIO(sources, ProjectContext::clean);
        forEachWithIO(targets, ProjectContext::clean);
        return result.build();
    }

    private ReportItem checkItem(Build build, SourceContext source, RefVersion sourceVersion, TargetContext target, RefVersion targetVersion) throws IOException {
        ReportItem.Builder result = ReportItem
                .builder()
                .sourceUri(source.getUri())
                .sourceVersion(sourceVersion)
                .targetUri(target.getUri())
                .targetVersion(targetVersion);

        Path project = target.getDirectory();

        if (targetVersion.requiresCheckout()) {
            build.checkoutTag(project, targetVersion.getRef());
        }

        Version from = source.getBroker().getVersion(build, project);
        Version to = sourceVersion.getVersion();
        if (!isSkip(source.getVersioning(), from, to)) {
            source.getBroker().setVersion(build, project, to);
            String errorMessage = verifyProject(build, project);
            if (errorMessage == null) {
                result.exitStatus(VERIFIED);
            } else {
                result.exitStatus(BROKEN).exitMessage(target.isLogErrors() ? errorMessage : null);
            }
            build.clean(project);
        } else {
            result.exitStatus(SKIPPED).exitMessage(format(ROOT, "Skipping check: source version %s is newer than target version %s", from, to));
        }

        if (targetVersion.requiresCheckout()) {
            build.restore(project);
        }

        return result.build();
    }

    private @Nullable String verifyProject(Maven maven, Path project) throws IOException {
        String errorMessage = maven.verify(project);
        if (errorMessage != null) {
            for (Interceptor interceptor : interceptors) {
                if (interceptor.onErrorMessage(maven, project, errorMessage, getOnEvent(), getOnDebug()) == null) {
                    return null;
                }
            }
        }
        return errorMessage;
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
