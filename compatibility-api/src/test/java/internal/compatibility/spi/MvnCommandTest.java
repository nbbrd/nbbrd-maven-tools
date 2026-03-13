package internal.compatibility.spi;


import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class MvnCommandTest {

    @Test
    void test() {
        assertThat(MvnCommand.builder().build().toProcessCommand())
                .containsExactly(MvnCommand.getDefaultBinary().toString(), "-ff");

        assertThat(MvnCommand
                .builder()
                .binary(Paths.get("hello"))
                .quiet(true)
                .file(Paths.get("workingDir"))
                .updateSnapshots(true)
                .goal("clean")
                .goal("verify")
                .property("skipTests", null)
                .property("enforcer.skip", null)
                .build().toProcessCommand())
                .containsExactly("hello", "-q", "-U", "-f", "workingDir", "-ff", "clean", "verify", "-D", "\"skipTests\"", "-D", "\"enforcer.skip\"");
    }
}