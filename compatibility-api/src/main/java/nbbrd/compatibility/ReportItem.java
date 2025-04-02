package nbbrd.compatibility;

import java.net.URI;

@lombok.Value
@lombok.Builder
public class ReportItem {

    int exitCode;
    URI sourceUri;
    Version sourceVersion;
    URI targetUri;
    Version targetVersion;
    Version originalVersion;
}
