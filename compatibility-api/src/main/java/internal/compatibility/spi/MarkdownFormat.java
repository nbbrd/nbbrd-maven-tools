package internal.compatibility.spi;

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
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
        return (value, writer) -> {
            formatReport(writer, (Report) value);
        };
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
        if (report.getItems().isEmpty()) {
            return;
        }

        Map<URI, Map<VersionContext, Map<VersionContext, ExitStatus>>> plugins = report.getItems()
                .stream()
                .collect(
                        groupingBy(ReportItem::getTargetUri, LinkedHashMap::new,
                                groupingBy(ReportItem::getTargetVersion, LinkedHashMap::new,
                                        toMap(ReportItem::getSourceVersion, ReportItem::getExitStatus)))
                );

        Set<VersionContext> versions = report.getItems()
                .stream()
                .map(ReportItem::getSourceVersion)
                .collect(toCollection(LinkedHashSet::new));

        List<Header> columns = versions.stream()
                .map(version -> new Header(FIXME, version))
                .collect(toList());

        List<Header> rows = plugins.entrySet().stream()
                .flatMap(entry -> entry.getValue().keySet().stream().map(x -> new Header(entry.getKey(), x)))
                .collect(toList());

        ExitStatus[][] body = plugins.values().stream()
                .flatMap(reports -> reports.values().stream().map(z -> versions.stream().map(z::get).map(value -> value != null ? value : ExitStatus.SKIPPED).toArray(ExitStatus[]::new)))
                .toArray(ExitStatus[][]::new);

        printMarkdown(appendable, rows, columns, body);
    }

    @Override
    public DirectoryStream.@NonNull Filter<? super Path> getFormatFileFilter() {
        return file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    private static void printMarkdown(Appendable appendable, List<Header> rows, List<Header> columns, ExitStatus[][] body) throws IOException {
        int col0 = rows.stream().map(Header::toProjectLabel).mapToInt(String::length).max().orElse(0);
        int col1 = rows.stream().map(Header::toVersionLabel).mapToInt(s -> s.length() + 4).max().orElse(0);
        int[] sizes = IntStream.concat(
                IntStream.of(col0, col1),
                columns.stream().map(Header::toVersionLabel).mapToInt(String::length)
        ).toArray();

        Collector<CharSequence, ?, String> toRow = joining(" | ", "| ", " |");

        Map<URI, Optional<VersionContext>> max = rows.stream().collect(groupingBy(Header::getUri, mapping(Header::getVersion, reducing((l, r) -> r))));

        appendable.append(lineSeparator()).append(Stream.concat(Stream.of(repeat(" ", sizes[0]), repeat(" ", sizes[1])), columns.stream().map(Header::toVersionLabel)).collect(toRow));
        appendable.append(lineSeparator()).append(IntStream.range(0, 2 + columns.size()).mapToObj(i -> repeat("-", sizes[i])).collect(toRow));
        AtomicReference<String> previous = new AtomicReference<>("");
        int bound = rows.size();
        for (int idx = 0; idx < bound; idx++) {
            int i = idx;
            String projectLabel = rows.get(i).toProjectLabel();
            String label = previous.getAndSet(projectLabel).equals(projectLabel) ? "" : projectLabel;
            String versionLabel = rows.get(i).toVersionLabel();
            boolean important = max.get(rows.get(i).getUri())
                    .map(VersionContext::getVersion)
                    .orElse(Version.parse(""))
                    .equals(Version.parse(versionLabel.substring(1)));
            if (important) {
                versionLabel = "**" + versionLabel + "**";
            }
            appendable.append(lineSeparator()).append(Stream.concat(
                    Stream.of(padRight(label, sizes[0]), padRight(versionLabel, sizes[1])),
                    IntStream.range(0, body[i].length).mapToObj(j -> padRight(emoji(body[i][j], important), sizes[j + 2]))
            ).collect(toRow));
        }
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
        VersionContext version;

        String toProjectLabel() {
            String path = uri.getPath();
            if (path != null) {
                int index = path.lastIndexOf('/');
                return index != -1 ? path.substring(index + 1) : path;
            }
            return uri.toString();
        }

        String toVersionLabel() {
            Tag tag = version.getTag();
            return Tag.NO_TAG.equals(tag) ? "HEAD" : tag.getRefName();
        }
    }

    private static String padRight(String text, int size) {
        return text.length() >= size ? text : text + repeat(" ", size - text.length());
    }

    private static String repeat(String text, int count) {
        return IntStream.range(0, count).mapToObj(i -> text).collect(joining());
    }

    private static final URI FIXME = URI.create("");
}
