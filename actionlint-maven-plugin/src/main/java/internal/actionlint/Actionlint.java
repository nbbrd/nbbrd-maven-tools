package internal.actionlint;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.Singular;
import org.jspecify.annotations.Nullable;

/** Runs actionlint and parses its JSON output. */
@lombok.Builder
public final class Actionlint {

    @NonNull private final Path executable;

    @NonNull private final Path workingDirectory;

    @Singular
    private final List<String> files;

    @Singular
    private final List<String> ignores;

    private final @Nullable Path configFile;

    private final @Nullable String shellcheck;

    private final @Nullable String pyflakes;

    public @NonNull List<Problem> run() throws IOException, InterruptedException {
        Path output = Files.createTempFile("actionlint", ".json");
        Path error = Files.createTempFile("actionlint", ".err");
        try {
            Process process = new ProcessBuilder(toCommand())
                    .directory(workingDirectory.toFile())
                    .redirectOutput(output.toFile())
                    .redirectError(error.toFile())
                    .start();
            int exitCode = process.waitFor();
            switch (exitCode) {
                case 0:
                case 1:
                    return parseProblems(new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
                default:
                    throw new IOException("actionlint failed with exit code " + exitCode + ": "
                            + new String(Files.readAllBytes(error), StandardCharsets.UTF_8).trim());
            }
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(error);
        }
    }

    List<String> toCommand() {
        List<String> result = new ArrayList<>();
        result.add(executable.toString());
        result.add("-format");
        result.add("{{json .}}");
        result.add("-no-color");
        if (configFile != null) {
            result.add("-config-file");
            result.add(configFile.toString());
        }
        for (String ignore : ignores) {
            result.add("-ignore");
            result.add(ignore);
        }
        if (shellcheck != null) {
            result.add("-shellcheck=" + shellcheck);
        }
        if (pyflakes != null) {
            result.add("-pyflakes=" + pyflakes);
        }
        result.addAll(files);
        return result;
    }

    static List<Problem> parseProblems(String json) throws IOException {
        List<Problem> result = new ArrayList<>();
        if (json.trim().isEmpty()) {
            return result;
        }
        try {
            for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject item = element.getAsJsonObject();
                result.add(Problem.builder()
                        .file(getField(item, "filepath").getAsString())
                        .line(getField(item, "line").getAsInt())
                        .column(getField(item, "column").getAsInt())
                        .kind(getField(item, "kind").getAsString())
                        .message(getField(item, "message").getAsString())
                        .build());
            }
        } catch (JsonParseException
                | IllegalStateException
                | UnsupportedOperationException
                | NumberFormatException ex) {
            throw new IOException("Cannot parse actionlint output: " + json, ex);
        }
        return result;
    }

    private static JsonElement getField(JsonObject item, String name) {
        JsonElement result = item.get(name);
        if (result == null || result.isJsonNull()) {
            throw new JsonParseException("Missing field '" + name + "'");
        }
        return result;
    }
}
