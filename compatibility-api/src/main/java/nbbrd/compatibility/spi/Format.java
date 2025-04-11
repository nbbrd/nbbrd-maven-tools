package nbbrd.compatibility.spi;

import lombok.NonNull;
import nbbrd.compatibility.Formatter;
import nbbrd.compatibility.Parser;
import nbbrd.service.Quantifier;
import nbbrd.service.ServiceDefinition;
import nbbrd.service.ServiceId;

import java.nio.file.DirectoryStream;
import java.nio.file.Path;

@ServiceDefinition(
        quantifier = Quantifier.MULTIPLE
)
public interface Format {

    @ServiceId(pattern = ServiceId.KEBAB_CASE)
    @NonNull
    String getFormatId();

    @NonNull
    String getFormatName();

    boolean canFormat(@NonNull Class<?> type);

    <T> Formatter<T> getFormatter(@NonNull Class<T> type);

    boolean canParse(@NonNull Class<?> type);

    <T> Parser<T> getParser(@NonNull Class<T> type);

    @NonNull
    DirectoryStream.Filter<? super Path> getFormatFileFilter();
}
