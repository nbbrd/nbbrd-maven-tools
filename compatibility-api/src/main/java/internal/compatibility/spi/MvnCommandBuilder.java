package internal.compatibility.spi;

import nbbrd.design.BuilderPattern;
import nbbrd.design.VisibleForTesting;
import nbbrd.io.sys.OS;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@BuilderPattern(TextCommand.class)
final class MvnCommandBuilder {

    private Path binary = null;
    private boolean quiet = false;
    private boolean updateSnapshots = false;
    private Path file = null;
    private final List<String> goals = new ArrayList<>();
    private final List<String> userProperties = new ArrayList<>();

    public MvnCommandBuilder binary(Path binary) {
        this.binary = binary;
        return this;
    }

    public MvnCommandBuilder quiet() {
        this.quiet = true;
        return this;
    }

    public MvnCommandBuilder updateSnapshots() {
        this.updateSnapshots = true;
        return this;
    }

    public MvnCommandBuilder file(Path file) {
        this.file = file;
        return this;
    }

    public MvnCommandBuilder goal(String goal) {
        this.goals.add(goal);
        return this;
    }

    public MvnCommandBuilder define(String userProperty) {
        this.userProperties.add(userProperty);
        return this;
    }

    public MvnCommandBuilder define(String key, String value) {
        this.userProperties.add(key + "=" + value);
        return this;
    }

    public TextCommand build() {
        TextCommand.Builder result = TextCommand.builder();
        result.command(binary != null ? binary.toString() : getDefaultBinary());
        if (quiet) result.command("-q");
        if (updateSnapshots) result.command("-U");
        if (file != null) result.command("-f").command(file.toString());
        result.commands(goals);
        userProperties.forEach(userProperty -> result.command("-D").command("\"" + userProperty + "\""));
        return result.build();
    }

    @VisibleForTesting
    static String getDefaultBinary() {
        return OS.NAME.equals(OS.Name.WINDOWS) ? "mvn.cmd" : "mvn";
    }
}
