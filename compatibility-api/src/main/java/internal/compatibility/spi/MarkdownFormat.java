package internal.compatibility.spi;

import lombok.NonNull;
import nbbrd.compatibility.*;
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
    public boolean canFormatJob() {
        return false;
    }

    @Override
    public void formatJob(@NonNull Appendable appendable, @NonNull Job job) throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public boolean canFormatReport() {
        return true;
    }

    @Override
    public void formatReport(@NonNull Appendable appendable, @NonNull Report report) throws IOException {
        if (report.getItems().isEmpty()) {
            return;
        }

        Map<URI, Map<Version, Map<Version, ExitStatus>>> plugins = report.getItems()
                .stream()
                .collect(
                        groupingBy(ReportItem::getTargetUri, LinkedHashMap::new,
                                groupingBy(ReportItem::getTargetVersion, LinkedHashMap::new,
                                        toMap(ReportItem::getSourceVersion, ReportItem::getExitStatus)))
                );

        Set<Version> versions = report.getItems()
                .stream()
                .map(ReportItem::getSourceVersion)
                .collect(toCollection(LinkedHashSet::new));

        List<Header> columns = versions.stream()
                .map(version -> new Header(null, version))
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
        int shortNameIndex = Header.getShortNameIndex(rows);
        int col0 = rows.stream().map(header -> header.toShortPluginName(shortNameIndex)).mapToInt(String::length).max().orElse(0);
        int col1 = rows.stream().map(Header::toVersionString).mapToInt(s -> s.length() + 4).max().orElse(0);
        int[] sizes = IntStream.concat(
                IntStream.of(col0, col1),
                columns.stream().map(Header::toVersionString).mapToInt(String::length)
        ).toArray();

        Collector<CharSequence, ?, String> toRow = joining(" | ", "| ", " |");

        Map<URI, Optional<Version>> max = rows.stream().collect(groupingBy(Header::getUri, mapping(Header::getVersion, reducing((l, r) -> r))));

        appendable.append(lineSeparator()).append(Stream.concat(Stream.of(repeat(" ", sizes[0]), repeat(" ", sizes[1])), columns.stream().map(Header::toVersionString)).collect(toRow));
        appendable.append(lineSeparator()).append(IntStream.range(0, 2 + columns.size()).mapToObj(i -> repeat("-", sizes[i])).collect(toRow));
        AtomicReference<String> previous = new AtomicReference<>("");
        int bound = rows.size();
        for (int idx = 0; idx < bound; idx++) {
            int i = idx;
            String shortPluginName = rows.get(i).toShortPluginName(shortNameIndex);
            String label = previous.getAndSet(shortPluginName).equals(shortPluginName) ? "" : shortPluginName;
            String versionString = rows.get(i).toVersionString();
            boolean important = max.get(rows.get(i).getUri()).orElse(Version.parse("")).equals(Version.parse(versionString.substring(1)));
            if (important) {
                versionString = "**" + versionString + "**";
            }
            appendable.append(lineSeparator()).append(Stream.concat(
                    Stream.of(padRight(label, sizes[0]), padRight(versionString, sizes[1])),
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

        URI uri;
        Version version;

        String toVersionString() {
            return "v" + version.toString();
        }

        String toUriString() {
            return uri.toString();
        }

        String toShortPluginName(int index) {
            return toUriString().substring(index);
        }

        static int getShortNameIndex(List<Header> headers) {
            switch (headers.size()) {
                case 0:
                case 1:
                    return 0;
                default:
                    String first = headers.get(0).toUriString();
                    List<String> rest = headers.stream().skip(1).map(Header::toUriString).collect(toList());
                    for (int i = first.length(); i >= 0; i--) {
                        String prefix = first.substring(0, i);
                        if (rest.stream().allMatch(header -> header.startsWith(prefix))) {
                            return i == first.length() ? 0 : i;
                        }
                    }
                    return 0;
            }
        }
    }

    private static String padRight(String text, int size) {
        return text.length() >= size ? text : text + repeat(" ", size - text.length());
    }

    private static String repeat(String text, int count) {
        return IntStream.range(0, count).mapToObj(i -> text).collect(joining());
    }
}
