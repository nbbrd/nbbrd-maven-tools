package nbbrd.buf.spi;

import com.google.protobuf.Descriptors;
import lombok.NonNull;
import nbbrd.service.Quantifier;
import nbbrd.service.ServiceDefinition;
import nbbrd.service.ServiceId;

/**
 * A custom lint rule applied by buf-maven-plugin to the protobuf files linted by buf.
 * <p>
 * Implementations are discovered with {@link java.util.ServiceLoader} in the dependencies of the plugin.
 */
@ServiceDefinition(quantifier = Quantifier.MULTIPLE)
public interface LintRule {

    /**
     * Identifier reported with each problem, in SCREAMING_SNAKE_CASE like the buf rules.
     *
     * @return a non-null identifier
     */
    @ServiceId(pattern = ServiceId.SCREAMING_SNAKE_CASE)
    @NonNull String getLintRuleId();

    /**
     * Checks a linted file. Imported files are not checked but are reachable through the linked descriptors.
     *
     * @param file     a linked file descriptor, with source code info
     * @param reporter the reporter of problems
     */
    void check(Descriptors.@NonNull FileDescriptor file, @NonNull LintReporter reporter);
}
