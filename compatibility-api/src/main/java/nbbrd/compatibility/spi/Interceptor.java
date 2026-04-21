package nbbrd.compatibility.spi;

import nbbrd.service.Quantifier;
import nbbrd.service.ServiceDefinition;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

@ServiceDefinition(quantifier = Quantifier.MULTIPLE)
public interface Interceptor {

    @Nullable String onErrorMessage(@NonNull Maven maven, @NonNull Path project, @NonNull String errorMessage,
                                    @NonNull Consumer<? super String> onEvent, @NonNull Consumer<? super String> onDebug) throws IOException;
}
