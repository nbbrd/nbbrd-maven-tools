package nbbrd.compatibility.spi;

import lombok.NonNull;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.Report;
import nbbrd.service.Quantifier;
import nbbrd.service.ServiceDefinition;
import nbbrd.service.ServiceId;

import java.io.IOException;
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

    boolean canFormatJob();

    void formatJob(@NonNull Appendable appendable, @NonNull Job job) throws IOException;

    boolean canFormatReport();

    void formatReport(@NonNull Appendable appendable, @NonNull Report report) throws IOException;

    @NonNull
    DirectoryStream.Filter<? super Path> getFormatFileFilter();
}
