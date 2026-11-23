package internal.buf;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/** Runs buf commands and parses their JSON output. */
@lombok.Builder
public final class Buf {

    /** Rule reported by buf when the files cannot be compiled. */
    public static final String COMPILE_RULE = "COMPILE";

    private static final int EXIT_FILE_ANNOTATIONS = 100;

    @NonNull private final Path executable;

    @NonNull private final Path workingDirectory;

    /** Input of buf, a directory or a buf workspace, relative to the working directory. */
    @NonNull private final String input;

    private final @Nullable Path configFile;

    /** Lints the input. */
    public @NonNull List<Problem> lint() throws IOException, InterruptedException {
        List<String> command = newCommand("lint");
        command.add("--error-format=json");
        return parseProblems(run(command, true));
    }

    /** Builds the input, with its imports and source code info, into a binary image. */
    public void build(@NonNull Path image) throws IOException, InterruptedException {
        List<String> command = newCommand("build");
        command.add("-o");
        command.add(image.toString());
        run(command, false);
    }

    /** Lists the files of the input, excluding imports, by import path. */
    public @NonNull Map<String, String> listFiles() throws IOException, InterruptedException {
        List<String> command = newCommand("ls-files");
        command.add("--format=json");
        return parseFiles(run(command, false));
    }

    private List<String> newCommand(String name) {
        List<String> result = new ArrayList<>();
        result.add(executable.toString());
        result.add(name);
        result.add(input);
        if (configFile != null) {
            result.add("--config");
            result.add(configFile.toString());
        }
        return result;
    }

    private String run(List<String> command, boolean allowAnnotations) throws IOException, InterruptedException {
        Path output = Files.createTempFile("buf", ".out");
        Path error = Files.createTempFile("buf", ".err");
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectOutput(output.toFile())
                    .redirectError(error.toFile())
                    .start();
            int exitCode = process.waitFor();
            if (exitCode == 0 || (allowAnnotations && exitCode == EXIT_FILE_ANNOTATIONS)) {
                return new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            }
            String message = new String(Files.readAllBytes(error), StandardCharsets.UTF_8).trim();
            if (message.isEmpty()) {
                message = new String(Files.readAllBytes(output), StandardCharsets.UTF_8).trim();
            }
            throw new IOException("buf " + command.get(1) + " failed with exit code " + exitCode + ": " + message);
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(error);
        }
    }

    static List<Problem> parseProblems(String jsonLines) throws IOException {
        List<Problem> result = new ArrayList<>();
        for (JsonObject item : parseJsonLines(jsonLines)) {
            result.add(Problem.builder()
                    .file(getField(item, "path").getAsString())
                    .line(getOptionalInt(item, "start_line"))
                    .column(getOptionalInt(item, "start_column"))
                    .rule(getField(item, "type").getAsString())
                    .message(getField(item, "message").getAsString())
                    .build());
        }
        return result;
    }

    static Map<String, String> parseFiles(String jsonLines) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonObject item : parseJsonLines(jsonLines)) {
            JsonElement isImport = item.get("is_import");
            if (isImport == null || isImport.isJsonNull() || !isImport.getAsBoolean()) {
                result.put(
                        getField(item, "import_path").getAsString(),
                        getField(item, "path").getAsString());
            }
        }
        return result;
    }

    private static List<JsonObject> parseJsonLines(String jsonLines) throws IOException {
        List<JsonObject> result = new ArrayList<>();
        for (String line : jsonLines.split("\\R")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            try {
                result.add(JsonParser.parseString(line).getAsJsonObject());
            } catch (JsonParseException | IllegalStateException ex) {
                throw new IOException("Cannot parse buf output: " + line, ex);
            }
        }
        return result;
    }

    private static int getOptionalInt(JsonObject item, String name) throws IOException {
        JsonElement result = item.get(name);
        try {
            return result == null || result.isJsonNull() ? 0 : result.getAsInt();
        } catch (UnsupportedOperationException | NumberFormatException | IllegalStateException ex) {
            throw new IOException("Cannot parse buf output: " + item, ex);
        }
    }

    private static JsonElement getField(JsonObject item, String name) throws IOException {
        JsonElement result = item.get(name);
        if (result == null || result.isJsonNull() || !result.isJsonPrimitive()) {
            throw new IOException("Cannot parse buf output, missing field '" + name + "': " + item);
        }
        return result;
    }
}
