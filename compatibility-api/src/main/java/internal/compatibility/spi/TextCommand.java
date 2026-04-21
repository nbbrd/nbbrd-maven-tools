package internal.compatibility.spi;

import lombok.NonNull;

import java.util.List;

interface TextCommand {

    @NonNull
    List<String> toProcessCommand();

    default @NonNull TextProcessor toTextProcessor() {
        return TextProcessor.builder().commands(toProcessCommand()).build();
    }
}
