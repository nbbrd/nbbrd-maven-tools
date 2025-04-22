package internal.compatibility.spi;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

class GitCommandBuilderTest {

    @Test
    void test() {
        assertThat(new GitCommandBuilder().build())
                .extracting(TextCommand::getCommands, list(String.class))
                .containsExactly(GitCommandBuilder.getDefaultBinary(), null);

        assertThat(new GitCommandBuilder()
                .command("tag")
                .option("--sort", "creatordate")
                .option("--format", "%(creatordate:short)/%(refname:strip=2)")
                .build())
                .extracting(TextCommand::getCommands, list(String.class))
                .containsExactly("git", "tag", "--sort=creatordate", "--format=%(creatordate:short)/%(refname:strip=2)");
    }
}