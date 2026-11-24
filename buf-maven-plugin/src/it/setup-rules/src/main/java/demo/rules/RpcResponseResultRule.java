package demo.rules;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import java.util.LinkedHashSet;
import java.util.Set;
import nbbrd.buf.spi.LintReporter;
import nbbrd.buf.spi.LintRule;

/** RPC response messages follow the Result pattern: oneof result { T ok; ErrorDto err; }. */
public final class RpcResponseResultRule implements LintRule {

    @Override
    public String getLintRuleId() {
        return "RPC_RESPONSE_RESULT";
    }

    @Override
    public void check(FileDescriptor file, LintReporter reporter) {
        Set<Descriptor> responses = new LinkedHashSet<>();
        for (ServiceDescriptor service : file.getServices()) {
            for (MethodDescriptor method : service.getMethods()) {
                responses.add(method.getOutputType());
            }
        }
        for (Descriptor response : responses) {
            OneofDescriptor result = response.getRealOneofs().stream()
                    .filter(oneof -> oneof.getName().equals("result"))
                    .findFirst()
                    .orElse(null);
            if (result == null) {
                reporter.report(response, "RPC response \"" + response.getName() + "\" should have a \"result\" oneof.");
                continue;
            }
            FieldDescriptor ok = response.findFieldByName("ok");
            FieldDescriptor err = response.findFieldByName("err");
            if (result.getFieldCount() != 2
                    || ok == null || ok.getContainingOneof() != result
                    || err == null || err.getContainingOneof() != result
                    || err.getType() != FieldDescriptor.Type.MESSAGE
                    || !err.getMessageType().getName().equals("ErrorDto")) {
                reporter.report(result, "Oneof \"result\" of \"" + response.getName()
                        + "\" should only contain an \"ok\" field and an \"ErrorDto err\" field.");
            }
        }
    }
}