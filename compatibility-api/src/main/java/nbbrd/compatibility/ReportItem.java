package nbbrd.compatibility;

import java.net.URI;

@lombok.Value
@lombok.Builder
public class ReportItem {

    int exitCode;
    URI targetUri;
    String sourceVersion;
    String targetVersion;
    String defaultVersion;
}
