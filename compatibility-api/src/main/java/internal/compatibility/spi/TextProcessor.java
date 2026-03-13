package internal.compatibility.spi;

import lombok.NonNull;
import nbbrd.io.text.TextParser;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collector;

import static internal.compatibility.Collectors2.consuming;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;

@lombok.Value
@lombok.Builder
class TextProcessor {

    List<String> commands;

    @lombok.With
    @lombok.Builder.Default
    Charset charset = StandardCharsets.UTF_8;

    @lombok.With
    @lombok.Builder.Default
    Consumer<? super String> listener = TextProcessor::ignore;

    public <X> X process(@NonNull Collector<? super String, ?, X> collector) throws IOException {
        listener.accept(String.join(" ", commands));
        return TextParser.onParsingLines(collector).parseProcess(commands, charset);
    }

    public void process() throws IOException {
        process(consuming());
    }

    public String processToString() throws IOException {
        return process(joining(lineSeparator()));
    }

    private static void ignore(Object ignore) {
    }
}
