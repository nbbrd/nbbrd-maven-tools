package internal.compatibility.spi;

import nbbrd.io.sys.ProcessReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collector;

@lombok.Value
@lombok.Builder
class TextCommand {

    @lombok.Singular
    List<String> commands;

    @lombok.NonNull
    @lombok.Builder.Default
    Charset charset = StandardCharsets.UTF_8;

    public <X> X collect(Collector<String, ?, X> collector) throws IOException {
        try (BufferedReader reader = ProcessReader.newReader(charset, commands.toArray(new String[0]))) {
            return reader.lines().collect(collector);
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }
}
