package nbbrd.compatibility;

import java.net.URI;

@lombok.Value
@lombok.Builder
public class ReportItem {

    @lombok.NonNull
    ExitStatus exitStatus;

    @lombok.NonNull
    URI sourceUri;

    @lombok.NonNull
    Version sourceVersion;

    @lombok.NonNull
    URI targetUri;

    @lombok.NonNull
    Version targetVersion;

    @lombok.NonNull
    Version originalVersion;
}
