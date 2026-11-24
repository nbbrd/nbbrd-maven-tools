package internal.buf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.Test;

class BufTest {

    @Test
    void testParseProblems() throws Exception {
        String output = "{\"path\":\"src\\\\main\\\\proto\\\\foo.proto\",\"start_line\":16,\"start_column\":25,"
                + "\"end_line\":16,\"end_column\":32,\"type\":\"FIELD_LOWER_SNAKE_CASE\","
                + "\"message\":\"Field name \\\"badName\\\" should be lower_snake_case.\"}\r\n"
                + "{\"path\":\"buf.yaml\",\"type\":\"COMPILE\",\"message\":\"invalid\"}\n";
        assertThat(Buf.parseProblems(output))
                .containsExactly(
                        Problem.builder()
                                .file("src\\main\\proto\\foo.proto")
                                .line(16)
                                .column(25)
                                .rule("FIELD_LOWER_SNAKE_CASE")
                                .message("Field name \"badName\" should be lower_snake_case.")
                                .build(),
                        Problem.builder()
                                .file("buf.yaml")
                                .rule("COMPILE")
                                .message("invalid")
                                .build());
        assertThat(Buf.parseProblems("")).isEmpty();
        assertThatIOException().isThrownBy(() -> Buf.parseProblems("Failure: oops"));
        assertThatIOException().isThrownBy(() -> Buf.parseProblems("{\"path\":\"a.proto\"}"));
    }

    @Test
    void testParseFiles() throws Exception {
        String output = "{\"path\":\"C:\\\\cache\\\\google\\\\protobuf\\\\empty.proto\","
                + "\"import_path\":\"google/protobuf/empty.proto\",\"is_import\":true}\n"
                + "{\"path\":\"proto\\\\foo\\\\v1\\\\foo.proto\",\"import_path\":\"foo/v1/foo.proto\","
                + "\"module\":\"\",\"commit\":\"\",\"is_import\":false}\n";
        assertThat(Buf.parseFiles(output)).containsExactly(entry("foo/v1/foo.proto", "proto\\foo\\v1\\foo.proto"));
    }
}
