package nbbrd.compatibility;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static nbbrd.compatibility.ReportItem.toLabel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ReportItemTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testToLabel() {
        RefVersion ref = RefVersion.builder().ref(Ref.NO_REF).version(Version.NO_VERSION).build();

        assertThatNullPointerException().isThrownBy(() -> toLabel(null, ref));
        assertThatNullPointerException().isThrownBy(() -> toLabel(URI.create("local"), null));

        assertThat(toLabel(URI.create("http://localhost/abc"), ref))
                .isEqualTo("abc@RefVersion(ref=/, version=)");

        assertThat(toLabel(URI.create("http://localhost/abc/"), ref))
                .isEqualTo("abc@RefVersion(ref=/, version=)");

        assertThat(toLabel(URI.create("abc"), ref))
                .isEqualTo("abc@RefVersion(ref=/, version=)");
    }
}