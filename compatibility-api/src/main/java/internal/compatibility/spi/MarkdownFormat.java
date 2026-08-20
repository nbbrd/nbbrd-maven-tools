package internal.compatibility.spi;

import internal.compatibility.Collectors2;
import internal.compatibility.Files2;
import lombok.NonNull;
import nbbrd.compatibility.*;
import nbbrd.compatibility.Formatter;
import nbbrd.compatibility.spi.Format;
import nbbrd.design.DirectImpl;
import nbbrd.design.VisibleForTesting;
import nbbrd.service.ServiceProvider;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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
        LinkRegistry links = new LinkRegistry();
        forEachWithIO(groupBySourceUri(report), (k, v) -> formatReport(appendable, k, v, links));
        printLinkDefinitions(appendable, links);
    }

    private void formatReport(Appendable appendable, URI sourceUri, List<ReportItem> items, LinkRegistry links) throws IOException {
        printMarkdown(appendable, Matrix.of(sourceUri, items), links);
    }

    @Override
    public DirectoryStream.@NonNull Filter<? super Path> getFormatFileFilter() {
        return file -> (!Files.exists(file) || Files.isRegularFile(file)) && Files2.hasExtension(file, ".md");
    }

    private static void printMarkdown(Appendable appendable, Matrix matrix, LinkRegistry links) throws IOException {
        Map<URI, Optional<RefVersion>> max = matrix.rows.stream().collect(groupingBy(Header::getUri, mapping(Header::getVersion, reducing((l, r) -> r))));

        // Build the display texts in document order so that reference-style links are numbered consistently
        Header source = matrix.columns.get(0);
        String title = projectLink(links, source.getUri(), source.toProjectLabel());

        List<String> columnTexts = matrix.columns.stream()
                .map(header -> versionLink(links, header.getUri(), header.getVersion(), header.toVersionLabel()))
                .collect(toList());

        int bound = matrix.rows.size();
        String[] rowProjectTexts = new String[bound];
        String[] rowVersionTexts = new String[bound];
        boolean[] important = new boolean[bound];
        String previous = "";
        for (int i = 0; i < bound; i++) {
            Header row = matrix.rows.get(i);
            String projectLabel = row.toProjectLabel();
            String shownLabel = previous.equals(projectLabel) ? "" : projectLabel;
            previous = projectLabel;
            rowProjectTexts[i] = projectLink(links, row.getUri(), shownLabel);

            String versionLabel = row.toVersionLabel();
            important[i] = max.get(row.getUri())
                    .map(RefVersion::getVersion)
                    .orElse(Version.parse(""))
                    .equals(Version.parse(versionLabel.substring(1)));
            String versionText = versionLink(links, row.getUri(), row.getVersion(), versionLabel);
            rowVersionTexts[i] = important[i] ? "**" + versionText + "**" : versionText;
        }

        int[] sizes = IntStream.concat(
                IntStream.of(maxLength(rowProjectTexts), maxLength(rowVersionTexts)),
                columnTexts.stream().mapToInt(String::length)
        ).toArray();

        Collector<CharSequence, ?, String> toRow = joining(" | ", "| ", " |");

        appendable.append("Compatibility matrix for **").append(title).append("**").append(lineSeparator());
        appendable.append(lineSeparator()).append(Stream.concat(Stream.of(repeat(" ", sizes[0]), repeat(" ", sizes[1])), columnTexts.stream()).collect(toRow));
        appendable.append(lineSeparator()).append(IntStream.range(0, 2 + matrix.columns.size()).mapToObj(i -> repeat("-", sizes[i])).collect(joining("-|-", "|-", "-|")));
        for (int idx = 0; idx < bound; idx++) {
            int i = idx;
            appendable.append(lineSeparator()).append(Stream.concat(
                    Stream.of(padRight(rowProjectTexts[i], sizes[0]), padRight(rowVersionTexts[i], sizes[1])),
                    IntStream.range(0, matrix.body[i].length).mapToObj(j -> padRight(emoji(matrix.body[i][j].status, important[i]), sizes[j + 2]))
            ).collect(toRow));
        }
        appendable.append(lineSeparator()).append(lineSeparator());
        for (int i = 0; i < bound; i++) {
            for (int j = 0; j < matrix.body[i].length; j++) {
                String msg = matrix.body[i][j].message;
                if (msg != null && !msg.isEmpty()) {
                    appendable.append("<details><summary>")
                            .append(matrix.rows.get(i).toProjectLabel())
                            .append(" @ ")
                            .append(matrix.rows.get(i).toVersionLabel())
                            .append(" -> ")
                            .append(matrix.columns.get(j).toVersionLabel())
                            .append("</summary>")
                            .append(lineSeparator())
                            .append(lineSeparator()).append("```").append(lineSeparator())
                            .append(msg)
                            .append(lineSeparator()).append("```").append(lineSeparator())
                            .append("</details>")
                            .append(lineSeparator());
                }
            }
        }
    }

    private static void printLinkDefinitions(Appendable appendable, LinkRegistry links) throws IOException {
        if (links.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : links.entries()) {
            appendable.append(lineSeparator()).append("[").append(entry.getValue().toString()).append("]: ").append(entry.getKey());
        }
        appendable.append(lineSeparator());
    }

    private static int maxLength(String[] texts) {
        int result = 0;
        for (String text : texts) {
            result = Math.max(result, text.length());
        }
        return result;
    }

    private static boolean isHttp(URI uri) {
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static @Nullable String projectUrl(URI uri) {
        if (!isHttp(uri)) {
            return null;
        }
        String result = uri.normalize().toString();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.endsWith(".git")) {
            result = result.substring(0, result.length() - ".git".length());
        }
        return result;
    }

    private static @Nullable String versionUrl(URI uri, RefVersion version) {
        String base = projectUrl(uri);
        if (base == null) {
            return null;
        }
        Ref ref = version.getRef();
        return Ref.NO_REF.equals(ref) ? base : base + "/tree/" + ref.getName();
    }

    private static String projectLink(LinkRegistry links, URI uri, String label) {
        String url = projectUrl(uri);
        return url == null || label.isEmpty() ? label : "[" + label + "][" + links.ref(url) + "]";
    }

    private static String versionLink(LinkRegistry links, URI uri, RefVersion version, String label) {
        String url = versionUrl(uri, version);
        return url == null ? label : "[" + label + "][" + links.ref(url) + "]";
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
                        .collect(Collectors2.toLast())
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
    private static class Cell {
        ExitStatus status;
        String message;
    }

    // Collects reference-style link definitions and assigns a stable numeric key per URL
    private static final class LinkRegistry {

        private final Map<String, Integer> refs = new LinkedHashMap<>();

        int ref(String url) {
            Integer existing = refs.get(url);
            if (existing != null) {
                return existing;
            }
            int key = refs.size() + 1;
            refs.put(url, key);
            return key;
        }

        boolean isEmpty() {
            return refs.isEmpty();
        }

        Set<Map.Entry<String, Integer>> entries() {
            return refs.entrySet();
        }
    }

    @lombok.Value
    @lombok.Builder
    private static class Matrix {

        @NonNull
        List<Header> rows;

        @NonNull
        List<Header> columns;

        @NonNull
        Cell[][] body;

        public static Matrix of(URI sourceUri, List<ReportItem> items) {
            Map<URI, Map<RefVersion, Map<RefVersion, Cell>>> plugins = items
                    .stream()
                    .collect(
                            groupingBy(ReportItem::getTargetUri, LinkedHashMap::new,
                                    groupingBy(ReportItem::getTargetVersion, LinkedHashMap::new,
                                            toMap(ReportItem::getSourceVersion, item -> new Cell(item.getExitStatus(), item.getExitMessage()))))
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
                            .flatMap(reports -> reports.values().stream().map(z -> versions.stream().map(z::get).map(value -> value != null ? value : new Cell(ExitStatus.SKIPPED, null)).toArray(Cell[]::new)))
                            .toArray(Cell[][]::new))
                    .build();
        }
    }
}
