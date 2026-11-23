package demo.rules;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import java.util.LinkedHashSet;
import java.util.Set;
import nbbrd.buf.spi.LintReporter;
import nbbrd.buf.spi.LintRule;

/** RPC request and response messages have a string id field to match them when streaming. */
public final class RpcDtoIdRule implements LintRule {

    @Override
    public String getLintRuleId() {
        return "RPC_DTO_ID";
    }

    @Override
    public void check(FileDescriptor file, LintReporter reporter) {
        Set<Descriptor> messages = new LinkedHashSet<>();
        for (ServiceDescriptor service : file.getServices()) {
            for (MethodDescriptor method : service.getMethods()) {
                messages.add(method.getInputType());
                messages.add(method.getOutputType());
            }
        }
        for (Descriptor message : messages) {
            FieldDescriptor id = message.findFieldByName("id");
            if (id == null || id.getType() != FieldDescriptor.Type.STRING || id.isRepeated()) {
                reporter.report(message, "RPC message \"" + message.getName() + "\" should have a \"string id\" field.");
            }
        }
    }
}