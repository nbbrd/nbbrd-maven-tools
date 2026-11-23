package demo.rules;

import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import nbbrd.buf.spi.LintReporter;
import nbbrd.buf.spi.LintRule;

/** RPC request messages end with RequestDto and response messages with ResponseDto. */
public final class RpcDtoSuffixRule implements LintRule {

    @Override
    public String getLintRuleId() {
        return "RPC_DTO_SUFFIX";
    }

    @Override
    public void check(FileDescriptor file, LintReporter reporter) {
        for (ServiceDescriptor service : file.getServices()) {
            for (MethodDescriptor method : service.getMethods()) {
                if (!method.getInputType().getName().endsWith("RequestDto")) {
                    reporter.report(method, "RPC request type \"" + method.getInputType().getName()
                            + "\" should end with \"RequestDto\".");
                }
                if (!method.getOutputType().getName().endsWith("ResponseDto")) {
                    reporter.report(method, "RPC response type \"" + method.getOutputType().getName()
                            + "\" should end with \"ResponseDto\".");
                }
            }
        }
    }
}