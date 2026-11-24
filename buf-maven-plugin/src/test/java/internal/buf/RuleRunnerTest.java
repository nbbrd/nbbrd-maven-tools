package internal.buf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import nbbrd.buf.spi.LintReporter;
import nbbrd.buf.spi.LintRule;
import org.junit.jupiter.api.Test;

class RuleRunnerTest {

    static final FileDescriptorProto DTO = FileDescriptorProto.newBuilder()
            .setName("dto.proto")
            .setSyntax("proto3")
            .addMessageType(DescriptorProto.newBuilder().setName("ErrorDto"))
            .build();

    static final FileDescriptorProto FOO = FileDescriptorProto.newBuilder()
            .setName("foo/v1/foo.proto")
            .setSyntax("proto3")
            .setPackage("foo.v1")
            .addDependency("dto.proto")
            .addMessageType(DescriptorProto.newBuilder().setName("GetFooRequestDto"))
            .addMessageType(DescriptorProto.newBuilder()
                    .setName("Outer")
                    .addNestedType(DescriptorProto.newBuilder()
                            .setName("Inner")
                            .addField(field("badName", 1).setType(FieldDescriptorProto.Type.TYPE_STRING)))
                    .addEnumType(EnumDescriptorProto.newBuilder()
                            .setName("Kind")
                            .addValue(EnumValueDescriptorProto.newBuilder()
                                    .setName("KIND_UNSPECIFIED")
                                    .setNumber(0)))
                    .addField(field("choice_a", 1)
                            .setType(FieldDescriptorProto.Type.TYPE_STRING)
                            .setOneofIndex(0))
                    .addOneofDecl(OneofDescriptorProto.newBuilder().setName("choice")))
            .addService(ServiceDescriptorProto.newBuilder()
                    .setName("FooService")
                    .addMethod(MethodDescriptorProto.newBuilder()
                            .setName("GetFoo")
                            .setInputType(".foo.v1.GetFooRequestDto")
                            .setOutputType(".ErrorDto")))
            .setSourceCodeInfo(SourceCodeInfo.newBuilder()
                    .addLocation(location(Collections.emptyList(), 0, 0, 10, 0))
                    .addLocation(location(Arrays.asList(4, 1, 3, 0, 2, 0), 4, 4, 30))
                    .addLocation(location(Arrays.asList(4, 1, 3, 0, 2, 0, 1), 4, 11, 18))
                    .addLocation(location(Arrays.asList(6, 0, 2, 0), 8, 2, 50)))
            .build();

    static final FileDescriptorSet IMAGE =
            FileDescriptorSet.newBuilder().addFile(FOO).addFile(DTO).build();

    @Test
    void testCheck() throws Exception {
        List<String> checkedFiles = new ArrayList<>();
        LintRule rule = new LintRule() {
            @Override
            public @NonNull String getLintRuleId() {
                return "SAMPLE_RULE";
            }

            @Override
            public void check(@NonNull FileDescriptor file, @NonNull LintReporter reporter) {
                checkedFiles.add(file.getName());
                Descriptor outer = file.findMessageTypeByName("Outer");
                MethodDescriptor method =
                        file.findServiceByName("FooService").getMethods().get(0);
                reporter.report(outer.getNestedTypes().get(0).getFields().get(0), "field name");
                reporter.report(method, "method");
                reporter.report(file, "file");
                reporter.report(method.getOutputType(), "imported message");
            }
        };

        Map<String, String> files = Collections.singletonMap("foo/v1/foo.proto", "proto/foo/v1/foo.proto");
        assertThat(RuleRunner.builder().rule(rule).build().check(IMAGE, files))
                .containsExactly(
                        problem("proto/foo/v1/foo.proto", 5, 12, "field name"),
                        problem("proto/foo/v1/foo.proto", 9, 3, "method"),
                        problem("proto/foo/v1/foo.proto", 1, 1, "file"),
                        problem("dto.proto", 0, 0, "imported message"));
        assertThat(checkedFiles).containsExactly("foo/v1/foo.proto");
    }

    @Test
    void testCheckFailures() {
        LintRule failing = new LintRule() {
            @Override
            public @NonNull String getLintRuleId() {
                return "FAILING_RULE";
            }

            @Override
            public void check(@NonNull FileDescriptor file, @NonNull LintReporter reporter) {
                throw new IllegalStateException("boom");
            }
        };
        assertThatIOException()
                .isThrownBy(() -> RuleRunner.builder()
                        .rule(failing)
                        .build()
                        .check(IMAGE, Collections.singletonMap("foo/v1/foo.proto", "foo.proto")))
                .withMessageContaining("FAILING_RULE")
                .withMessageContaining("boom");
        assertThatIOException()
                .isThrownBy(() -> RuleRunner.builder()
                        .build()
                        .check(FileDescriptorSet.newBuilder().addFile(FOO).build(), Collections.emptyMap()))
                .withMessageContaining("dto.proto");
    }

    @Test
    void testGetPath() throws Exception {
        FileDescriptor file = RuleRunner.link(IMAGE).get("foo/v1/foo.proto");
        Descriptor outer = file.findMessageTypeByName("Outer");
        assertThat(RuleRunner.getPath(file)).isEmpty();
        assertThat(RuleRunner.getPath(file.findMessageTypeByName("GetFooRequestDto")))
                .containsExactly(4, 0);
        assertThat(RuleRunner.getPath(outer.getNestedTypes().get(0).getFields().get(0)))
                .containsExactly(4, 1, 3, 0, 2, 0);
        assertThat(RuleRunner.getPath(outer.getOneofs().get(0))).containsExactly(4, 1, 8, 0);
        assertThat(RuleRunner.getPath(outer.getEnumTypes().get(0).getValues().get(0)))
                .containsExactly(4, 1, 4, 0, 2, 0);
        assertThat(RuleRunner.getPath(file.getServices().get(0).getMethods().get(0)))
                .containsExactly(6, 0, 2, 0);
    }

    @Test
    void testIsIgnored() {
        SourceCodeInfo info = SourceCodeInfo.newBuilder()
                .addLocation(comment(Arrays.asList(4, 0), " Doc.\n buf:lint:ignore PARENT_RULE\n"))
                .addLocation(comment(Arrays.asList(4, 0, 2, 0), " buf:lint:ignore FIELD_RULE OTHER\n"))
                .addLocation(comment(Arrays.asList(4, 0, 2, 1), " see buf:lint:ignore INLINE_RULE\n"))
                .addLocation(comment(Collections.singletonList(2), " buf:lint:ignore FILE_RULE "))
                .build();
        List<Integer> field = Arrays.asList(4, 0, 2, 0);
        assertThat(RuleRunner.isIgnored(info, field, "FIELD_RULE")).isTrue();
        assertThat(RuleRunner.isIgnored(info, field, "PARENT_RULE")).isTrue();
        assertThat(RuleRunner.isIgnored(info, field, "OTHER")).isFalse();
        assertThat(RuleRunner.isIgnored(info, field, "FIELD")).isFalse();
        assertThat(RuleRunner.isIgnored(info, Arrays.asList(4, 0), "FIELD_RULE"))
                .isFalse();
        assertThat(RuleRunner.isIgnored(info, Arrays.asList(4, 0, 2, 1), "INLINE_RULE"))
                .isFalse();
        assertThat(RuleRunner.isIgnored(info, Collections.emptyList(), "FILE_RULE"))
                .isTrue();
        assertThat(RuleRunner.isIgnored(info, Arrays.asList(4, 1), "FILE_RULE")).isFalse();
    }

    private static FieldDescriptorProto.Builder field(String name, int number) {
        return FieldDescriptorProto.newBuilder()
                .setName(name)
                .setNumber(number)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL);
    }

    private static SourceCodeInfo.Location location(List<Integer> path, Integer... span) {
        return SourceCodeInfo.Location.newBuilder()
                .addAllPath(path)
                .addAllSpan(Arrays.asList(span))
                .build();
    }

    private static SourceCodeInfo.Location comment(List<Integer> path, String leadingComments) {
        return SourceCodeInfo.Location.newBuilder()
                .addAllPath(path)
                .setLeadingComments(leadingComments)
                .build();
    }

    private static Problem problem(String file, int line, int column, String message) {
        return Problem.builder()
                .file(file)
                .line(line)
                .column(column)
                .rule("SAMPLE_RULE")
                .message(message)
                .build();
    }
}
