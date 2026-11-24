package internal.buf;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import lombok.Singular;
import nbbrd.buf.spi.LintRule;
import org.jspecify.annotations.Nullable;

/** Applies custom lint rules to the files of a buf image. */
@lombok.Builder
public final class RuleRunner {

    // field numbers of descriptor.proto, used in source code info paths
    private static final int FILE_PACKAGE = 2;
    private static final int FILE_MESSAGE_TYPE = 4;
    private static final int FILE_ENUM_TYPE = 5;
    private static final int FILE_SERVICE = 6;
    private static final int FILE_EXTENSION = 7;
    private static final int FILE_SYNTAX = 12;
    private static final int MESSAGE_FIELD = 2;
    private static final int MESSAGE_NESTED_TYPE = 3;
    private static final int MESSAGE_ENUM_TYPE = 4;
    private static final int MESSAGE_EXTENSION = 6;
    private static final int MESSAGE_ONEOF_DECL = 8;
    private static final int ENUM_VALUE = 2;
    private static final int SERVICE_METHOD = 2;
    private static final int ELEMENT_NAME = 1;

    private static final String IGNORE_DIRECTIVE = "buf:lint:ignore";

    @Singular
    private final List<LintRule> rules;

    /**
     * Checks the linted files of an image.
     *
     * @param image the image built by buf, with imports and source code info
     * @param files the paths of the linted files, by import path
     */
    public @NonNull List<Problem> check(@NonNull FileDescriptorSet image, @NonNull Map<String, String> files)
            throws IOException {
        List<Problem> result = new ArrayList<>();
        Map<String, FileDescriptor> linked = link(image);
        for (Map.Entry<String, String> file : files.entrySet()) {
            FileDescriptor descriptor = linked.get(file.getKey());
            if (descriptor == null) {
                throw new IOException("Missing file in buf image: " + file.getKey());
            }
            for (LintRule rule : rules) {
                String ruleId = rule.getLintRuleId();
                try {
                    rule.check(descriptor, (element, message) -> {
                        SourceCodeInfo info = element.getFile().toProto().getSourceCodeInfo();
                        List<Integer> path = getPath(element);
                        if (!isIgnored(info, path, ruleId)) {
                            result.add(toProblem(ruleId, element, info, path, message, files));
                        }
                    });
                } catch (RuntimeException ex) {
                    throw new IOException(
                            "Rule " + ruleId + " failed on " + file.getValue() + ": " + ex.getMessage(), ex);
                }
            }
        }
        return result;
    }

    private static Problem toProblem(
            String ruleId,
            GenericDescriptor element,
            SourceCodeInfo info,
            List<Integer> path,
            String message,
            Map<String, String> files) {
        String name = element.getFile().getName();
        int[] position = getPosition(info, path);
        return Problem.builder()
                .file(files.getOrDefault(name, name))
                .line(position[0])
                .column(position[1])
                .rule(ruleId)
                .message(message)
                .build();
    }

    /**
     * Checks if a rule is ignored on an element by a {@code buf:lint:ignore} leading comment on the element or one of
     * its parents, as buf does; file-level problems are ignored by comments on the syntax or package statement.
     */
    static boolean isIgnored(SourceCodeInfo info, List<Integer> path, String ruleId) {
        if (path.isEmpty()) {
            return hasIgnoreComment(info, Collections.singletonList(FILE_SYNTAX), ruleId)
                    || hasIgnoreComment(info, Collections.singletonList(FILE_PACKAGE), ruleId);
        }
        for (int length = 2; length <= path.size(); length += 2) {
            if (hasIgnoreComment(info, path.subList(0, length), ruleId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIgnoreComment(SourceCodeInfo info, List<Integer> path, String ruleId) {
        SourceCodeInfo.@Nullable Location location = findLocation(info, path);
        if (location == null || !location.hasLeadingComments()) {
            return false;
        }
        for (String line : location.getLeadingComments().split("\n", -1)) {
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length >= 2 && IGNORE_DIRECTIVE.equals(tokens[0]) && ruleId.equals(tokens[1])) {
                return true;
            }
        }
        return false;
    }

    static Map<String, FileDescriptor> link(FileDescriptorSet image) throws IOException {
        Map<String, FileDescriptorProto> protos = new HashMap<>();
        for (FileDescriptorProto proto : image.getFileList()) {
            protos.put(proto.getName(), proto);
        }
        Map<String, FileDescriptor> result = new LinkedHashMap<>();
        for (FileDescriptorProto proto : image.getFileList()) {
            link(proto.getName(), protos, result, new HashSet<>());
        }
        return result;
    }

    private static FileDescriptor link(
            String name, Map<String, FileDescriptorProto> protos, Map<String, FileDescriptor> linked, Set<String> path)
            throws IOException {
        FileDescriptor result = linked.get(name);
        if (result != null) {
            return result;
        }
        FileDescriptorProto proto = protos.get(name);
        if (proto == null) {
            throw new IOException("Missing file in buf image: " + name);
        }
        if (!path.add(name)) {
            throw new IOException("Cyclic import in buf image: " + name);
        }
        FileDescriptor[] dependencies = new FileDescriptor[proto.getDependencyCount()];
        for (int i = 0; i < dependencies.length; i++) {
            dependencies[i] = link(proto.getDependency(i), protos, linked, path);
        }
        path.remove(name);
        try {
            result = FileDescriptor.buildFrom(proto, dependencies);
        } catch (DescriptorValidationException ex) {
            throw new IOException("Cannot link " + name + " from buf image: " + ex.getMessage(), ex);
        }
        linked.put(name, result);
        return result;
    }

    /** Gets the 1-based line and column of an element name, or of the element if its name is not located. */
    static int[] getPosition(SourceCodeInfo info, List<Integer> path) {
        List<Integer> namePath = new ArrayList<>(path);
        namePath.add(ELEMENT_NAME);
        SourceCodeInfo.@Nullable Location location = path.isEmpty() ? null : findLocation(info, namePath);
        if (location == null) {
            location = findLocation(info, path);
        }
        if (location == null || location.getSpanCount() < 2) {
            return new int[] {0, 0};
        }
        return new int[] {location.getSpan(0) + 1, location.getSpan(1) + 1};
    }

    private static SourceCodeInfo.@Nullable Location findLocation(SourceCodeInfo info, List<Integer> path) {
        for (SourceCodeInfo.Location location : info.getLocationList()) {
            if (location.getPathList().equals(path)) {
                return location;
            }
        }
        return null;
    }

    /** Gets the source code info path of an element. */
    static List<Integer> getPath(GenericDescriptor element) {
        List<Integer> result = new ArrayList<>();
        appendPath(element, result);
        return result;
    }

    private static void appendPath(GenericDescriptor element, List<Integer> path) {
        if (element instanceof Descriptor) {
            Descriptor message = (Descriptor) element;
            appendChild(message.getContainingType(), FILE_MESSAGE_TYPE, MESSAGE_NESTED_TYPE, message.getIndex(), path);
        } else if (element instanceof FieldDescriptor) {
            FieldDescriptor field = (FieldDescriptor) element;
            if (field.isExtension()) {
                appendChild(field.getExtensionScope(), FILE_EXTENSION, MESSAGE_EXTENSION, field.getIndex(), path);
            } else {
                appendPath(field.getContainingType(), path);
                appendIndex(MESSAGE_FIELD, field.getIndex(), path);
            }
        } else if (element instanceof OneofDescriptor) {
            OneofDescriptor oneof = (OneofDescriptor) element;
            appendPath(oneof.getContainingType(), path);
            appendIndex(MESSAGE_ONEOF_DECL, oneof.getIndex(), path);
        } else if (element instanceof EnumDescriptor) {
            EnumDescriptor enumType = (EnumDescriptor) element;
            appendChild(enumType.getContainingType(), FILE_ENUM_TYPE, MESSAGE_ENUM_TYPE, enumType.getIndex(), path);
        } else if (element instanceof EnumValueDescriptor) {
            EnumValueDescriptor value = (EnumValueDescriptor) element;
            appendPath(value.getType(), path);
            appendIndex(ENUM_VALUE, value.getIndex(), path);
        } else if (element instanceof ServiceDescriptor) {
            appendIndex(FILE_SERVICE, ((ServiceDescriptor) element).getIndex(), path);
        } else if (element instanceof MethodDescriptor) {
            MethodDescriptor method = (MethodDescriptor) element;
            appendPath(method.getService(), path);
            appendIndex(SERVICE_METHOD, method.getIndex(), path);
        }
    }

    private static void appendChild(
            @Nullable Descriptor parent, int fileFieldNumber, int messageFieldNumber, int index, List<Integer> path) {
        if (parent == null) {
            appendIndex(fileFieldNumber, index, path);
        } else {
            appendPath(parent, path);
            appendIndex(messageFieldNumber, index, path);
        }
    }

    private static void appendIndex(int fieldNumber, int index, List<Integer> path) {
        path.add(fieldNumber);
        path.add(index);
    }
}
