package internal.bundle;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;

final class Archive implements Closeable {

    final Map<String, Entry> entries = new TreeMap<>();
    private final Path staging;
    private final List<Path> contents = new ArrayList<>();

    Archive(Path parent) throws IOException {
        staging = Files.createTempDirectory(parent, ".bundle-content-");
    }

    Archive copy(Path parent) throws IOException {
        // Payloads are borrowed; the source must outlive the copy. Only new patches belong to the copy.
        Archive copy = new Archive(parent);
        copy.entries.putAll(entries);
        return copy;
    }

    void read(Path file) throws IOException {
        String filename = file.getFileName().toString();
        if (filename.endsWith(".zip")) {
            try (ZipFile zip = ZipFile.builder().setPath(file).get()) {
                Enumeration<ZipArchiveEntry> items = zip.getEntries();
                while (items.hasMoreElements()) {
                    ZipArchiveEntry item = items.nextElement();
                    if (!zip.canReadEntryData(item)) {
                        throw new IllegalArgumentException("Unsupported ZIP entry: " + item.getName());
                    }
                    try (InputStream input = zip.getInputStream(item)) {
                        String name = name(item.getName());
                        if (name.isEmpty() && item.isDirectory()) {
                            continue;
                        }
                        EntryType type = item.isDirectory()
                                ? EntryType.DIRECTORY
                                : item.isUnixSymlink() ? EntryType.SYMLINK : EntryType.FILE;
                        int unixType = item.getUnixMode() & 0170000;
                        if (unixType != 0 && unixType != 0100000 && unixType != 0040000 && unixType != 0120000) {
                            throw new IllegalArgumentException("Unsupported ZIP entry type: " + name);
                        }
                        Path content = type == EntryType.DIRECTORY ? null : storeEntry(input, name);
                        String link = type == EntryType.SYMLINK
                                ? new String(Files.readAllBytes(content), StandardCharsets.UTF_8)
                                : null;
                        int mode = item.getUnixMode() & 0777;
                        add(new Entry(
                                name, type, item.getUnixMode() == 0 ? defaultMode(name, type) : mode, content, link));
                    }
                }
            }
        } else if (filename.endsWith(".tar.gz") || filename.endsWith(".tgz")) {
            try (InputStream input = Files.newInputStream(file);
                    GzipCompressorInputStream gzip = GzipCompressorInputStream.builder()
                            .setInputStream(input)
                            .get();
                    TarArchiveInputStream tar = new TarArchiveInputStream(gzip, StandardCharsets.UTF_8.name())) {
                TarArchiveEntry item;
                while ((item = tar.getNextEntry()) != null) {
                    if (!tar.canReadEntryData(item)) {
                        throw new IllegalArgumentException("Unsupported TAR entry: " + item.getName());
                    }
                    String name = name(item.getName());
                    if (name.isEmpty() && item.isDirectory()) {
                        continue;
                    }
                    EntryType type;
                    if (item.isDirectory()) {
                        type = EntryType.DIRECTORY;
                    } else if (item.isSymbolicLink()) {
                        type = EntryType.SYMLINK;
                    } else if (item.isLink()) {
                        type = EntryType.HARDLINK;
                    } else if ((item.getLinkFlag() == TarConstants.LF_NORMAL
                                    || item.getLinkFlag() == TarConstants.LF_OLDNORM)
                            && !item.isSparse()) {
                        type = EntryType.FILE;
                    } else {
                        throw new IllegalArgumentException("Unsupported TAR entry type: " + name);
                    }
                    add(new Entry(
                            name,
                            type,
                            item.getMode() & 0777,
                            type == EntryType.FILE ? storeEntry(tar, name) : null,
                            type == EntryType.SYMLINK || type == EntryType.HARDLINK ? item.getLinkName() : null));
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported archive: " + file);
        }
        validate();
    }

    private static int defaultMode(String name, EntryType type) {
        return type == EntryType.DIRECTORY
                        || type == EntryType.SYMLINK
                        || name.equals("bin/java")
                        || name.endsWith("/bin/java")
                ? 0755
                : 0644;
    }

    Path store(InputStream input) throws IOException {
        Path content = Files.createTempFile(staging, "entry-", ".bin");
        contents.add(content);
        Files.copy(input, content, StandardCopyOption.REPLACE_EXISTING);
        return content;
    }

    private Path storeEntry(InputStream input, String name) throws IOException {
        try {
            return store(input);
        } catch (IOException ex) {
            throw new IOException("Cannot read archive entry " + name + ": " + ex.getMessage(), ex);
        }
    }

    void add(Entry entry) {
        if (entry.name.isEmpty()) {
            throw new IllegalArgumentException("Empty archive entry name");
        }
        Entry previous = entries.putIfAbsent(entry.name, entry);
        if (previous != null && !(previous.type == EntryType.DIRECTORY && entry.type == EntryType.DIRECTORY)) {
            throw new IllegalArgumentException("Conflicting archive entry: " + entry.name);
        }
    }

    void validate() {
        for (Entry entry : entries.values()) {
            String parent = parent(entry.name);
            while (!parent.isEmpty()) {
                Entry ancestor = entries.get(parent);
                if (ancestor != null && ancestor.type != EntryType.DIRECTORY) {
                    throw new IllegalArgumentException("Non-directory archive ancestor: " + parent);
                }
                parent = parent(parent);
            }
            if (entry.type == EntryType.SYMLINK || entry.type == EntryType.HARDLINK) {
                String target = linkTarget(entry);
                if (!entries.containsKey(target)
                        && entries.keySet().stream().noneMatch(key -> key.startsWith(target + "/"))) {
                    throw new IllegalArgumentException(
                            "Missing archive link target: " + entry.name + " -> " + entry.link);
                }
                if (entry.type == EntryType.HARDLINK) {
                    regular(entry);
                }
            }
        }
    }

    Entry regular(Entry entry) {
        String source = entry == null ? "<missing>" : entry.name;
        Set<String> seen = new HashSet<>();
        while (entry != null && (entry.type == EntryType.HARDLINK || entry.type == EntryType.SYMLINK)) {
            if (!seen.add(entry.name)) {
                throw new IllegalArgumentException("Cyclic archive link: " + entry.name);
            }
            entry = entries.get(linkTarget(entry));
        }
        if (entry == null || entry.type != EntryType.FILE) {
            throw new IllegalArgumentException("Archive entry does not resolve to a regular file: " + source);
        }
        return entry;
    }

    static String linkTarget(Entry entry) {
        String link = entry.link;
        if (link == null || link.isEmpty() || link.startsWith("/") || link.contains("\\") || link.contains(":")) {
            throw new IllegalArgumentException("Unsafe archive link: " + entry.name + " -> " + link);
        }
        return normalize(
                (entry.type == EntryType.SYMLINK && !parent(entry.name).isEmpty() ? parent(entry.name) + "/" : "")
                        + link,
                true);
    }

    static String name(String value) {
        if (value == null
                || value.startsWith("/")
                || value.contains("\\")
                || value.contains(":")
                || value.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("Unsafe archive path: " + value);
        }
        return normalize(value, false);
    }

    private static String normalize(String value, boolean link) {
        Deque<String> parts = new ArrayDeque<>();
        for (String part : value.split("/")) {
            if ("..".equals(part)) {
                if (!link || parts.isEmpty()) {
                    throw new IllegalArgumentException("Unsafe archive path: " + value);
                }
                parts.removeLast();
            } else if (!part.isEmpty() && !".".equals(part)) {
                if (part.chars().anyMatch(c -> c < 32 || c == 127)) {
                    throw new IllegalArgumentException("Unsafe archive path: " + value);
                }
                parts.addLast(part);
            }
        }
        return String.join("/", parts);
    }

    static String parent(String name) {
        int index = name.lastIndexOf('/');
        return index < 0 ? "" : name.substring(0, index);
    }

    void write(Path output, String format, Instant timestamp) throws IOException {
        validate();
        if ("zip".equals(format)) {
            ZipTimestamps.validate(timestamp);
            List<Long> localHeaders = new ArrayList<>();
            long centralDirectory;
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(output)) {
                zip.setEncoding(StandardCharsets.UTF_8.name());
                for (Entry entry : entries.values()) {
                    ZipArchiveEntry item =
                            new ZipArchiveEntry(entry.name + (entry.type == EntryType.DIRECTORY ? "/" : ""));
                    item.setUnixMode((entry.type == EntryType.DIRECTORY
                                    ? 0040000
                                    : entry.type == EntryType.SYMLINK ? 0120000 : 0100000)
                            | entry.mode);
                    // Commons Compress converts DOS times using the default timezone. Normalize headers after closing.
                    item.setTime(946684800000L);
                    localHeaders.add(zip.getBytesWritten());
                    zip.putArchiveEntry(item);
                    if (entry.type == EntryType.SYMLINK) {
                        zip.write(entry.link.getBytes(StandardCharsets.UTF_8));
                    } else if (entry.type != EntryType.DIRECTORY) {
                        copyContent(entry, zip);
                    }
                    zip.closeArchiveEntry();
                }
                centralDirectory = zip.getBytesWritten();
                zip.finish();
            }
            ZipTimestamps.normalize(output, localHeaders, centralDirectory, timestamp);
        } else {
            GzipParameters parameters = new GzipParameters();
            parameters.setModificationTime(0);
            parameters.setOperatingSystem(255);
            try (OutputStream outputStream = Files.newOutputStream(output);
                    GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(outputStream, parameters);
                    TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip, StandardCharsets.UTF_8.name())) {
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
                for (Entry entry : tarOrder()) {
                    byte type = entry.type == EntryType.DIRECTORY
                            ? TarConstants.LF_DIR
                            : entry.type == EntryType.SYMLINK
                                    ? TarConstants.LF_SYMLINK
                                    : entry.type == EntryType.HARDLINK ? TarConstants.LF_LINK : TarConstants.LF_NORMAL;
                    TarArchiveEntry item =
                            new TarArchiveEntry(entry.name + (entry.type == EntryType.DIRECTORY ? "/" : ""), type);
                    item.setMode(entry.mode);
                    item.setModTime(FileTime.from(timestamp));
                    item.setUserId(0);
                    item.setGroupId(0);
                    item.setUserName("");
                    item.setGroupName("");
                    if (entry.link != null) {
                        item.setLinkName(entry.type == EntryType.HARDLINK ? regular(entry).name : entry.link);
                    }
                    if (entry.type == EntryType.FILE) {
                        item.setSize(Files.size(entry.content));
                    }
                    tar.putArchiveEntry(item);
                    if (entry.type == EntryType.FILE) {
                        copyContent(entry, tar);
                    }
                    tar.closeArchiveEntry();
                }
            }
        }
    }

    private void copyContent(Entry entry, OutputStream output) throws IOException {
        try {
            Files.copy(regular(entry).content, output);
        } catch (IOException ex) {
            throw new IOException("Cannot write archive entry " + entry.name + ": " + ex.getMessage(), ex);
        }
    }

    private List<Entry> tarOrder() {
        List<Entry> ordered = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        for (Entry entry : entries.values()) {
            if (entry.type == EntryType.HARDLINK) {
                Entry target = regular(entry);
                if (emitted.add(target.name)) {
                    ordered.add(target);
                }
            }
            if (emitted.add(entry.name)) {
                ordered.add(entry);
            }
        }
        return ordered;
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (Path content : contents) {
            try {
                Files.deleteIfExists(content);
            } catch (IOException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        try {
            Files.delete(staging);
        } catch (IOException ex) {
            if (failure == null) {
                failure = ex;
            } else {
                failure.addSuppressed(ex);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @RequiredArgsConstructor
    static final class Entry {
        final String name;
        final EntryType type;
        final int mode;
        final Path content;
        final String link;
    }
}
