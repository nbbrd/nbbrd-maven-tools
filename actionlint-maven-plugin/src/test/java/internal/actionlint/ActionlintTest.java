package internal.actionlint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class ActionlintTest {

    @Test
    void buildsCommandWithOptionsBeforeFiles() {
        Actionlint actionlint = Actionlint.builder()
                .executable(Paths.get("actionlint"))
                .workingDirectory(Paths.get("."))
                .file(".github/workflows/ci.yml")
                .ignore("SC2086")
                .configFile(Paths.get("actionlint.yaml"))
                .shellcheck("shellcheck")
                .build();
        assertThat(actionlint.toCommand())
                .containsExactly(
                        "actionlint",
                        "-format",
                        "{{json .}}",
                        "-no-color",
                        "-config-file",
                        "actionlint.yaml",
                        "-ignore",
                        "SC2086",
                        "-shellcheck=shellcheck",
                        ".github/workflows/ci.yml");
    }

    @Test
    void parsesJsonProblems() throws Exception {
        String json = "[{\"message\":\"job \\\"missing\\\" is not defined\",\"filepath\":\".github/workflows/ci.yml\","
                + "\"line\":8,\"column\":12,\"kind\":\"job-needs\",\"snippet\":\"x\",\"end_column\":20}]";
        assertThat(Actionlint.parseProblems(json))
                .singleElement()
                .hasToString(".github/workflows/ci.yml:8:12: job \"missing\" is not defined [job-needs]");
        assertThat(Actionlint.parseProblems("[]")).isEmpty();
        assertThat(Actionlint.parseProblems("")).isEmpty();
    }

    @Test
    void rejectsUnexpectedOutput() {
        assertThatIOException().isThrownBy(() -> Actionlint.parseProblems("{}"));
        assertThatIOException().isThrownBy(() -> Actionlint.parseProblems("[{\"message\":\"x\"}]"));
    }
}
