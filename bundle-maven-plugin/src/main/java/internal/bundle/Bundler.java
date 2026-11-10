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
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import nbbrd.bundle.spi.Launcher;
import nbbrd.bundle.spi.LauncherFile;

@UtilityClass
public class Bundler {

    public void bundle(BundleRequest request) throws IOException {
        bundle(request, message -> {});
    }

    public void bundle(BundleRequest request, Consumer<String> progress) throws IOException {
        long started = System.nanoTime();
        BundleRequest validated = validate(request);
        Launcher launcher = Launchers.find(validated.getLauncher());
        List<LauncherFile> files = launcherFiles(launcher, validated.getAppName());
        Path stagingParent = validated.getRuntimes().get(0).getOutput().getParent();
        Files.createDirectories(stagingParent);
        try (Archive application = new Archive(stagingParent)) {
            progress.accept("Reading application archive: " + validated.getApplication());
            read(application, validated.getApplication());
            String base = validated.getBaseDirectory();
            if (!base.isEmpty()
                    && application.entries.keySet().stream().noneMatch(name -> name.startsWith(base + "/"))) {
                throw new IllegalArgumentException(
                        "Application root '" + base + "' not found in " + validated.getApplication());
            }
            int index = 0;
            for (RuntimeBundle runtime : validated.getRuntimes()) {
                String context = context(validated, runtime);
                try {
                    long runtimeStarted = System.nanoTime();
                    progress.accept(
                            "[" + ++index + "/" + validated.getRuntimes().size() + "] Bundling runtime '"
                                    + runtime.getId() + "' from " + runtime.getArchive());
                    bundleRuntime(validated, runtime, application, launcher, files, progress);
                    progress.accept(String.format(
                            Locale.ROOT,
                            "Bundled runtime '%s': %s (%,d bytes, %s)",
                            runtime.getId(),
                            runtime.getOutput(),
                            Files.size(runtime.getOutput()),
                            elapsed(runtimeStarted)));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(context + ": " + ex.getMessage(), ex);
                } catch (IOException ex) {
                    throw new IOException(context + ": " + ex.getMessage(), ex);
                }
            }
        }
        int count = validated.getRuntimes().size();
        progress.accept("Generated " + count + (count == 1 ? " bundle" : " bundles") + " in " + elapsed(started));
    }

    private String elapsed(long started) {
        return String.format(Locale.ROOT, "%.3f s", (System.nanoTime() - started) / 1_000_000_000.0);
    }

    private BundleRequest validate(BundleRequest request) throws IOException {
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
                String format = outputFormat(runtime.getOutput());
                if ("zip".equals(format)) {
                    ZipTimestamps.validate(request.getTimestamp());
                }
                String id = Launchers.component(runtime.getId(), "Runtime id");
                if (!ids.add(id.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Duplicate runtime id: " + id);
                }
                Path archive = requireArchive(runtime.getArchive());
                inputs.add(archive);
                runtimes.add(runtime.toBuilder()
                        .archive(archive)
                        .output(resolveDestination(runtime.getOutput()))
                        .format(format)
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
            List<LauncherFile> files,
            Consumer<String> progress)
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
                progress.accept("Writing bundle for runtime '" + runtime.getId() + "': " + destination);
                app.write(temporary, runtime.getFormat(), request.getTimestamp());
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

    private String outputFormat(Path output) {
        String name = output.getFileName().toString();
        if (name.endsWith(".zip")) {
            return "zip";
        }
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return "tar.gz";
        }
        throw new IllegalArgumentException("outputFile must end in .zip, .tar.gz or .tgz: " + output);
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
        if (override != null) {
            String home = safeLauncherPath(override);
            requireJavaHome(archive, home);
            return home;
        }
        Set<String> executableHomes = new TreeSet<>();
        for (String name : archive.entries.keySet()) {
            if (name.equals("bin/java")
                    || name.equals("bin/java.exe")
                    || name.endsWith("/bin/java")
                    || name.endsWith("/bin/java.exe")) {
                String home = Archive.parent(Archive.parent(name));
                executableHomes.add(home.isEmpty() ? "." : home);
            }
        }
        Set<String> candidates = new TreeSet<>();
        for (String candidate : executableHomes) {
            String home = ".".equals(candidate) ? "" : candidate;
            if (archive.entries.containsKey(join(home, "release"))) {
                requireJavaHome(archive, home);
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No Java installation found in runtime archive. Expected bin/java or bin/java.exe and a file named "
                            + "'release' (JDK/JRE metadata) in the same installation directory."
                            + (executableHomes.isEmpty()
                                    ? ""
                                    : " Found Java executable directories: " + executableHomes
                                            + "; check that they contain the 'release' metadata file."));
        }
        if (candidates.size() > 1) {
            throw new IllegalArgumentException("Multiple Java installations found in runtime archive: " + candidates
                    + ". Set this runtime's javaHome to one of these archive-relative directories "
                    + "('.' means the archive root).");
        }
        return safeLauncherPath(candidates.iterator().next());
    }

    private void requireJavaHome(Archive archive, String home) {
        Archive.Entry executable = archive.entries.get(join(home, "bin/java"));
        if (executable == null) {
            executable = archive.entries.get(join(home, "bin/java.exe"));
        }
        Archive.Entry release = archive.entries.get(join(home, "release"));
        List<String> missing = new ArrayList<>();
        if (executable == null) {
            missing.add("'bin/java' or 'bin/java.exe'");
        }
        if (release == null) {
            missing.add("the 'release' metadata file");
        }
        String context = "Invalid javaHome '" + (home.isEmpty() ? "." : home) + "': ";
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(context + "missing " + String.join(" and ", missing)
                    + ". javaHome must name a directory inside the runtime archive, not on the build machine.");
        }
        try {
            archive.regular(executable);
            archive.regular(release);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(context + ex.getMessage(), ex);
        }
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
