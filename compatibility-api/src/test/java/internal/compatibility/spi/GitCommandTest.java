package internal.compatibility.spi;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitCommandTest {

    @Test
    void test() {
        assertThat(GitCommand.builder().build().toProcessCommand())
                .containsExactly(GitCommand.getDefaultBinary(), null);

        assertThat(GitCommand
                .builder()
                .command("tag")
                .option("--sort", "creatordate")
                .option("--format", "%(creatordate:short)/%(refname:strip=2)")
                .build().toProcessCommand())
                .containsExactly("git", "tag", "--sort=creatordate", "--format=%(creatordate:short)/%(refname:strip=2)");
    }
}