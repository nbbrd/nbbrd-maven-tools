package internal.bundle;

import internal.bundle.BundleRequest.RuntimeBundle;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;
import nbbrd.bundle.spi.Launcher;
import nbbrd.bundle.spi.LauncherFile;

@UtilityClass
public class Bundler {

    public void bundle(BundleRequest request) throws IOException {
        BundleRequest validated = validate(request);
        Launcher launcher = Launchers.find(validated.getLauncher());
        List<LauncherFile> files = launcherFiles(launcher, validated.getAppName());
        Path stagingParent = validated.getRuntimes().get(0).getOutput().getParent();
        Files.createDirectories(stagingParent);
        try (Archive application = new Archive(stagingParent)) {
            read(application, validated.getApplication());
            String base = validated.getBaseDirectory();
            if (!base.isEmpty()
                    && application.entries.keySet().stream().noneMatch(name -> name.startsWith(base + "/"))) {
                throw new IllegalArgumentException(
                        "Application root '" + base + "' not found in " + validated.getApplication());
            }
            for (RuntimeBundle runtime : validated.getRuntimes()) {
                String context = context(validated, runtime);
                try {
                    bundleRuntime(validated, runtime, application, launcher, files);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(context + ": " + ex.getMessage(), ex);
                } catch (IOException ex) {
                    throw new IOException(context + ": " + ex.getMessage(), ex);
                }
            }
        }
    }

    private BundleRequest validate(BundleRequest request) throws IOException {
        if (!"zip".equals(request.getFormat()) && !"tar.gz".equals(request.getFormat())) {
            throw new IllegalArgumentException("format must be zip or tar.gz");
        }
        if ("zip".equals(request.getFormat())) {
            ZipTimestamps.validate(request.getTimestamp());
        }
        String base = safeLauncherPath(request.getBaseDirectory());
        if (request.getRuntimes().isEmpty()) {
            throw new IllegalArgumentException("At least one runtime is required");
        }
        Path application = requireArchive(request.getApplication());
        List<Path> inputs = new ArrayList<>();
        inputs.add(application);
        List<RuntimeBundle> runtimes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (RuntimeBundle runtime : request.getRuntimes()) {
            try {
                String id = Launchers.component(runtime.getId(), "Runtime id");
                if (!ids.add(id.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Duplicate runtime id: " + id);
                }
                Path archive = requireArchive(runtime.getArchive());
                inputs.add(archive);
                runtimes.add(runtime.toBuilder()
                        .archive(archive)
                        .output(resolveDestination(runtime.getOutput()))
                        .javaHome(runtime.getJavaHome() == null ? null : safeLauncherPath(runtime.getJavaHome()))
                        .build());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(context(request, runtime) + ": " + ex.getMessage(), ex);
            } catch (IOException ex) {
                throw new IOException(context(request, runtime) + ": " + ex.getMessage(), ex);
            }
        }
        Set<Path> outputs = new HashSet<>();
        for (RuntimeBundle runtime : runtimes) {
            Path output = runtime.getOutput();
            for (Path input : inputs) {
                if (input.equals(output) || (Files.exists(output) && Files.isSameFile(input, output))) {
                    throw new IllegalArgumentException(
                            context(request, runtime) + ": Output must be distinct from all input archives: " + output);
                }
            }
            if (!outputs.add(output)) {
                throw new IllegalArgumentException(context(request, runtime) + ": Duplicate output archive: " + output);
            }
        }
        return request.toBuilder()
                .application(application)
                .baseDirectory(base)
                .clearRuntimes()
                .runtimes(runtimes)
                .build();
    }

    private List<LauncherFile> launcherFiles(Launcher launcher, String appName) {
        List<LauncherFile> files = launcher.getFiles(appName);
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException(
                    "Launcher " + launcher.getLauncherId() + " must provide at least one file");
        }
        List<LauncherFile> normalized = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        for (LauncherFile file : files) {
            if (file == null) {
                throw new IllegalArgumentException("Null file from launcher " + launcher.getLauncherId());
            }
            String path;
            try {
                path = Archive.name(file.getPath());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Launcher " + launcher.getLauncherId() + ": " + ex.getMessage(), ex);
            }
            if (path.isEmpty() || !paths.add(path)) {
                throw new IllegalArgumentException(
                        "Empty or duplicate path from launcher " + launcher.getLauncherId() + ": " + file.getPath());
            }
            normalized.add(new LauncherFile(path, file.getRole(), file.isExecutable()));
        }
        return normalized;
    }

    private void bundleRuntime(
            BundleRequest request,
            RuntimeBundle runtime,
            Archive application,
            Launcher launcher,
            List<LauncherFile> files)
            throws IOException {
        Path destination = runtime.getOutput();
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".bundle-output-", ".tmp");
        try {
            try (Archive app = application.copy(destination.getParent());
                    Archive jdk = new Archive(destination.getParent())) {
                read(jdk, runtime.getArchive());
                String javaHome = javaHome(jdk, runtime.getJavaHome());
                String base = request.getBaseDirectory();
                for (LauncherFile file : files) {
                    patch(app, base, file, launcher, javaHome);
                }
                for (Archive.Entry entry : jdk.entries.values()) {
                    app.add(new Archive.Entry(
                            join(base, entry.name),
                            entry.type,
                            entry.mode,
                            entry.content,
                            entry.type == EntryType.HARDLINK ? join(base, Archive.linkTarget(entry)) : entry.link));
                }
                app.write(temporary, request.getFormat(), request.getTimestamp());
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String context(BundleRequest request, RuntimeBundle runtime) {
        return "Runtime '" + runtime.getId() + "' [archive=" + runtime.getArchive() + ", output=" + runtime.getOutput()
                + ", launcher=" + request.getLauncher() + "]";
    }

    private Path requireArchive(Path input) throws IOException {
        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException("Not a regular input archive: " + input);
        }
        return input.toRealPath();
    }

    private Path resolveDestination(Path output) throws IOException {
        Path absolute = output.toAbsolutePath().normalize();
        if (Files.exists(absolute) && !Files.isRegularFile(absolute)) {
            throw new IllegalArgumentException("Output is not a regular file: " + output);
        }
        Path existing = absolute;
        while (!Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (!existing.equals(absolute) && !Files.isDirectory(existing)) {
            throw new IllegalArgumentException("Output ancestor is not a directory: " + existing);
        }
        return existing.toRealPath().resolve(existing.relativize(absolute));
    }

    private void read(Archive archive, Path input) throws IOException {
        try {
            archive.read(input);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Archive " + input + ": " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new IOException("Archive " + input + ": " + ex.getMessage(), ex);
        }
    }

    private String safeLauncherPath(String value) {
        String normalized = Archive.name(value);
        if (!normalized.matches("[A-Za-z0-9._+ /-]*")) {
            throw new IllegalArgumentException("Unsupported launcher path: " + value);
        }
        return normalized;
    }

    private String join(String parent, String child) {
        return parent.isEmpty() ? child : child.isEmpty() ? parent : parent + "/" + child;
    }

    private String javaHome(Archive archive, String override) {
        Set<String> candidates = new TreeSet<>();
        for (String name : archive.entries.keySet()) {
            if (name.equals("bin/java")
                    || name.equals("bin/java.exe")
                    || name.endsWith("/bin/java")
                    || name.endsWith("/bin/java.exe")) {
                String home = Archive.parent(Archive.parent(name));
                String release = join(home, "release");
                if (archive.entries.containsKey(release)) {
                    archive.regular(archive.entries.get(name));
                    archive.regular(archive.entries.get(release));
                    candidates.add(home);
                }
            }
        }
        if (override != null) {
            String home = safeLauncherPath(override);
            if (!candidates.contains(home)) {
                throw new IllegalArgumentException("javaHome does not contain bin/java[.exe] and release: " + override);
            }
            return home;
        }
        if (candidates.size() != 1) {
            throw new IllegalArgumentException("Expected one Java home in runtime archive, found " + candidates);
        }
        return safeLauncherPath(candidates.iterator().next());
    }

    private void patch(Archive archive, String base, LauncherFile file, Launcher launcher, String javaHome)
            throws IOException {
        String name = join(base, file.getPath());
        try {
            Archive.Entry entry = archive.entries.get(name);
            if (entry == null || entry.type != EntryType.FILE) {
                throw new IllegalArgumentException("Missing regular launcher file: " + name);
            }
            String original = StandardCharsets.UTF_8
                    .newDecoder()
                    .decode(ByteBuffer.wrap(Files.readAllBytes(entry.content)))
                    .toString();
            String patched = launcher.patch(file, original, javaHome);
            if (patched == null) {
                throw new IllegalArgumentException("Launcher returned null content");
            }
            Path content = archive.store(new ByteArrayInputStream(patched.getBytes(StandardCharsets.UTF_8)));
            int mode = file.isExecutable() ? entry.mode | 0111 : entry.mode;
            archive.entries.put(name, new Archive.Entry(name, entry.type, mode, content, null));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Launcher entry " + name + " (role=" + file.getRole() + "): " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new IOException("Launcher entry " + name + " (role=" + file.getRole() + "): " + ex.getMessage(), ex);
        }
    }
}
