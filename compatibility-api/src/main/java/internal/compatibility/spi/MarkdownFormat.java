package internal.compatibility.spi;

import internal.compatibility.Files2;
import lombok.NonNull;
import nbbrd.compatibility.*;
import nbbrd.compatibility.Formatter;
import nbbrd.compatibility.spi.Format;
import nbbrd.design.DirectImpl;
import nbbrd.design.VisibleForTesting;
import nbbrd.service.ServiceProvider;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static internal.compatibility.IOStreams.forEachWithIO;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.*;

@DirectImpl
@ServiceProvider
public final class MarkdownFormat implements Format {

    @Override
    public @NonNull String getFormatId() {
        return "markdown";
    }

    @Override
    public @NonNull String getFormatName() {
        return "Markdown";
    }

    @Override
    public boolean canFormat(@NonNull Class<?> type) {
        return Report.class.equals(type);
    }

    @Override
    public <T> Formatter<T> getFormatter(@NonNull Class<T> type) {
        if (!Report.class.equals(type)) {
            throw new IllegalArgumentException("Not supported");
        }
        return (value, writer) -> formatReport(writer, (Report) value);
    }

    @Override
    public boolean canParse(@NonNull Class<?> type) {
        return false;
    }

    @Override
    public <T> Parser<T> getParser(@NonNull Class<T> type) {
        throw new IllegalArgumentException("Not supported");
    }

    private void formatReport(@NonNull Appendable appendable, @NonNull Report report) throws IOException {
        forEachWithIO(groupBySourceUri(report), (k, v) -> formatReport(appendable, k, v));
    }

    private void formatReport(Appendable appendable, URI sourceUri, List<ReportItem> items) throws IOException {
        printMarkdown(appendable, Matrix.of(sourceUri, items));
    }

    @Override
    public DirectoryStream.@NonNull Filter<? super Path> getFormatFileFilter() {
        return file -> (!Files.exists(file) || Files.isRegularFile(file)) && Files2.hasExtension(file, ".md");
    }

    private static void printMarkdown(Appendable appendable, Matrix matrix) throws IOException {
        int col0 = matrix.rows.stream().map(Header::toProjectLabel).mapToInt(String::length).max().orElse(0);
        int col1 = matrix.rows.stream().map(Header::toVersionLabel).mapToInt(s -> s.length() + 4).max().orElse(0);
        int[] sizes = IntStream.concat(
                IntStream.of(col0, col1),
                matrix.columns.stream().map(Header::toVersionLabel).mapToInt(String::length)
        ).toArray();

        Collector<CharSequence, ?, String> toRow = joining(" | ", "| ", " |");

        Map<URI, Optional<RefVersion>> max = matrix.rows.stream().collect(groupingBy(Header::getUri, mapping(Header::getVersion, reducing((l, r) -> r))));

        appendable.append("Compatibility matrix for **").append(matrix.columns.get(0).toProjectLabel()).append("**").append(lineSeparator());
        appendable.append(lineSeparator()).append(Stream.concat(Stream.of(repeat(" ", sizes[0]), repeat(" ", sizes[1])), matrix.columns.stream().map(Header::toVersionLabel)).collect(toRow));
        appendable.append(lineSeparator()).append(IntStream.range(0, 2 + matrix.columns.size()).mapToObj(i -> repeat("-", sizes[i])).collect(joining("-|-", "|-", "-|")));
        AtomicReference<String> previous = new AtomicReference<>("");
        int bound = matrix.rows.size();
        for (int idx = 0; idx < bound; idx++) {
            int i = idx;
            String projectLabel = matrix.rows.get(i).toProjectLabel();
            String label = previous.getAndSet(projectLabel).equals(projectLabel) ? "" : projectLabel;
            String versionLabel = matrix.rows.get(i).toVersionLabel();
            boolean important = max.get(matrix.rows.get(i).getUri())
                    .map(RefVersion::getVersion)
                    .orElse(Version.parse(""))
                    .equals(Version.parse(versionLabel.substring(1)));
            if (important) {
                versionLabel = "**" + versionLabel + "**";
            }
            appendable.append(lineSeparator()).append(Stream.concat(
                    Stream.of(padRight(label, sizes[0]), padRight(versionLabel, sizes[1])),
                    IntStream.range(0, matrix.body[i].length).mapToObj(j -> padRight(emoji(matrix.body[i][j], important), sizes[j + 2]))
            ).collect(toRow));
        }
        appendable.append(lineSeparator()).append(lineSeparator());
    }

    private static String emoji(ExitStatus exitStatus, boolean important) {
        switch (exitStatus) {
            case VERIFIED:
            case VALIDATED:
            case TESTED:
                return "✅";
            case BROKEN:
                return important ? "🔥" : "❌";
            case SKIPPED:
                return "";
            default:
                return "❓";
        }
    }

    @VisibleForTesting
    @lombok.Value
    static class Header {

        @lombok.NonNull
        URI uri;

        @lombok.NonNull
        RefVersion version;

        String toProjectLabel() {
            String path = uri.normalize().getPath();
            if (path != null) {
                return Stream.of(path.split("/", -1))
                        .filter(item -> !item.isEmpty())
                        .collect(CommandLineBuild.toLast())
                        .orElse(path);
            }
            return uri.toString();
        }

        String toVersionLabel() {
            Ref ref = version.getRef();
            return Ref.NO_REF.equals(ref) ? "HEAD" : ref.getName();
        }
    }

    private static String padRight(String text, int size) {
        return text.length() >= size ? text : text + repeat(" ", size - text.length());
    }

    private static String repeat(String text, int count) {
        return IntStream.range(0, count).mapToObj(i -> text).collect(joining());
    }

    private static Map<URI, List<ReportItem>> groupBySourceUri(Report report) {
        return report.getItems().stream().collect(Collectors.groupingBy(ReportItem::getSourceUri));
    }

    @lombok.Value
    @lombok.Builder
    private static class Matrix {

        @NonNull
        List<Header> rows;

        @NonNull
        List<Header> columns;

        @NonNull
        ExitStatus[][] body;

        public static Matrix of(URI sourceUri, List<ReportItem> items) {
            Map<URI, Map<RefVersion, Map<RefVersion, ExitStatus>>> plugins = items
                    .stream()
                    .collect(
                            groupingBy(ReportItem::getTargetUri, LinkedHashMap::new,
                                    groupingBy(ReportItem::getTargetVersion, LinkedHashMap::new,
                                            toMap(ReportItem::getSourceVersion, ReportItem::getExitStatus)))
                    );

            Set<RefVersion> versions = items
                    .stream()
                    .map(ReportItem::getSourceVersion)
                    .collect(toCollection(LinkedHashSet::new));

            return Matrix
                    .builder()
                    .rows(plugins.entrySet().stream()
                            .flatMap(entry -> entry.getValue().keySet().stream().map(x -> new Header(entry.getKey(), x)))
                            .collect(toList()))
                    .columns(versions.stream()
                            .map(version -> new Header(sourceUri, version))
                            .collect(toList()))
                    .body(plugins.values().stream()
                            .flatMap(reports -> reports.values().stream().map(z -> versions.stream().map(z::get).map(value -> value != null ? value : ExitStatus.SKIPPED).toArray(ExitStatus[]::new)))
                            .toArray(ExitStatus[][]::new))
                    .build();
        }
    }
}
