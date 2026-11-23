package nbbrd.buf.spi;

import com.google.protobuf.Descriptors;
import lombok.NonNull;

/**
 * Reporter of the problems found by a {@link LintRule}.
 */
@FunctionalInterface
public interface LintReporter {

    /**
     * Reports a problem located at the declaration of an element.
     *
     * @param element the faulty element, such as a message, a field or a method
     * @param message the description of the problem
     */
    void report(Descriptors.@NonNull GenericDescriptor element, @NonNull String message);
}
