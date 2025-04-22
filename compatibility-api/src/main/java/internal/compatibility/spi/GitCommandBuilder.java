package internal.compatibility.spi;

import nbbrd.design.BuilderPattern;
import nbbrd.design.VisibleForTesting;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@BuilderPattern(TextCommand.class)
final class GitCommandBuilder {

    private Path binary = null;
    private boolean quiet = false;
    private Path workingDir = null;
    private String command = null;
    private final List<String> parameters = new ArrayList<>();
    private final Map<String, String> options = new HashMap<>();

    public GitCommandBuilder binary(Path binary) {
        this.binary = binary;
        return this;
    }

    public GitCommandBuilder quiet() {
        this.quiet = true;
        return this;
    }

    public GitCommandBuilder workingDir(Path workingDir) {
        this.workingDir = workingDir;
        return this;
    }

    public GitCommandBuilder command(String command) {
        this.command = command;
        return this;
    }

    public GitCommandBuilder parameter(String parameter) {
        this.parameters.add(parameter);
        return this;
    }

    public GitCommandBuilder option(String key, String value) {
        this.options.put(key, value);
        return this;
    }

    public TextCommand build() {
        TextCommand.Builder result = TextCommand.builder();
        result.command(binary != null ? binary.toString() : getDefaultBinary());
        if (workingDir != null) result.command("-C").command(workingDir.toString());
        result.command(command);
        result.commands(parameters);
        if (quiet) result.command("-q");
        options.forEach((k, v) -> result.command(k + "=" + v));
        return result.build();
    }

    @VisibleForTesting
    static String getDefaultBinary() {
        return "git";
    }
}
