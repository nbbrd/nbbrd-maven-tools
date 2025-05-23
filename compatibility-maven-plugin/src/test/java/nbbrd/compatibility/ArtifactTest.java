package nbbrd.compatibility;

import org.junit.jupiter.api.Test;

import static nbbrd.compatibility.Artifact.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ArtifactTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testRepresentableAsString() {
        assertThatNullPointerException().isThrownBy(() -> parse(null));

        assertThat(parse("eu.europa.ec.joinup.sat:jdplus-main-desktop-design:jar::3.1.1"))
                .returns("eu.europa.ec.joinup.sat", Artifact::getGroupId)
                .returns("jdplus-main-desktop-design", Artifact::getArtifactId)
                .returns("jar", Artifact::getType)
                .returns("", Artifact::getClassifier)
                .returns("3.1.1", Artifact::getVersion)
                .hasToString("eu.europa.ec.joinup.sat:jdplus-main-desktop-design:jar::3.1.1");

        assertThat(parse("eu.europa.ec.joinup.sat:jdplus*"))
                .returns("eu.europa.ec.joinup.sat", Artifact::getGroupId)
                .returns("jdplus*", Artifact::getArtifactId)
                .returns("", Artifact::getType)
                .returns("", Artifact::getClassifier)
                .returns("", Artifact::getVersion)
                .hasToString("eu.europa.ec.joinup.sat:jdplus*:::");
    }

    @Test
    void testToFilter() {
        assertThat(parse("eu.europa.ec.joinup.sat:jdplus*-desktop-*").toFilter())
                .accepts(parse("eu.europa.ec.joinup.sat:jdplus-desktop-"))
                .accepts(parse("eu.europa.ec.joinup.sat:jdplus-main-desktop-design"))
                .rejects(parse("eu.europa.ec.joinup.sat:jdplus-desktop"));
    }
}