package internal.compatibility.spi;


import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

class MvnCommandBuilderTest {

    @Test
    void test() {
        assertThat(new MvnCommandBuilder().build())
                .extracting(TextCommand::getCommands, list(String.class))
                .containsExactly(MvnCommandBuilder.getDefaultBinary().toString());

        assertThat(new MvnCommandBuilder()
                .binary(Paths.get("hello"))
                .quiet()
                .file(Paths.get("workingDir"))
                .updateSnapshots()
                .goal("clean")
                .goal("verify")
                .define("skipTests")
                .define("enforcer.skip")
                .build())
                .extracting(TextCommand::getCommands, list(String.class))
                .containsExactly("hello", "-q", "-U", "-f", "workingDir", "clean", "verify", "-D", "\"skipTests\"", "-D", "\"enforcer.skip\"");
    }
}