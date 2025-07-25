package internal.compatibility.spi;

import lombok.NonNull;
import nbbrd.design.BuilderPattern;
import nbbrd.design.VisibleForTesting;
import nbbrd.io.sys.OS;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@BuilderPattern(TextCommand.class)
final class MvnCommandBuilder {

    public enum FailStrategy {FAIL_AT_END, FAIL_FAST, FAIL_NEVER}

    private @Nullable Path binary = null;
    private boolean quiet = false;
    private boolean batchMode = false;
    private boolean updateSnapshots = false;
    private @Nullable Path file = null;
    private @NonNull FailStrategy failStrategy = FailStrategy.FAIL_FAST;
    private final List<String> goals = new ArrayList<>();
    private final List<String> userProperties = new ArrayList<>();

    public MvnCommandBuilder binary(@Nullable Path binary) {
        this.binary = binary;
        return this;
    }

    public MvnCommandBuilder quiet() {
        this.quiet = true;
        return this;
    }

    public MvnCommandBuilder batchMode() {
        this.batchMode = true;
        return this;
    }

    public MvnCommandBuilder updateSnapshots() {
        this.updateSnapshots = true;
        return this;
    }

    public MvnCommandBuilder file(@Nullable Path file) {
        this.file = file;
        return this;
    }

    public MvnCommandBuilder failStrategy(@NonNull FailStrategy failStrategy) {
        this.failStrategy = failStrategy;
        return this;
    }

    public MvnCommandBuilder goal(@NonNull String goal) {
        this.goals.add(goal);
        return this;
    }

    public MvnCommandBuilder define(String userProperty) {
        this.userProperties.add(userProperty);
        return this;
    }

    public MvnCommandBuilder define(String key, CharSequence value) {
        this.userProperties.add(key + "=" + value);
        return this;
    }

    public TextCommand build() {
        TextCommand.Builder result = TextCommand.builder();
        result.command((binary != null ? binary : getDefaultBinary()).toString());
        if (quiet) result.command("-q");
        if (batchMode) result.command("-B");
        if (updateSnapshots) result.command("-U");
        if (file != null) result.command("-f").command(file.toString());
        switch (failStrategy) {
            case FAIL_AT_END:
                result.command("-fae");
                break;
            case FAIL_FAST:
                result.command("-ff");
                break;
            case FAIL_NEVER:
                result.command("-fn");
                break;
        }
        result.commands(goals);
        userProperties.forEach(userProperty -> result.command("-D").command("\"" + userProperty + "\""));
        return result.build();
    }

    @VisibleForTesting
    static Path getDefaultBinary() {
        String mavenHome = System.getenv("MAVEN_HOME");
        Path binaryName = Paths.get(OS.NAME.equals(OS.Name.WINDOWS) ? "mvn.cmd" : "mvn");
        return mavenHome != null ? Paths.get(mavenHome).resolve("bin").resolve(binaryName) : binaryName;
    }
}
