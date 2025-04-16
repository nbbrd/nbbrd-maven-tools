package nbbrd.compatibility;

import lombok.NonNull;

import java.net.URI;

@lombok.Value
@lombok.Builder
public class ReportItem {

    @lombok.NonNull
    ExitStatus exitStatus;

    @lombok.NonNull
    URI sourceUri;

    @lombok.NonNull
    VersionContext sourceVersion;

    @lombok.NonNull
    URI targetUri;

    @lombok.NonNull
    VersionContext targetVersion;

    public static @NonNull String toLabel(@NonNull URI uri, @NonNull VersionContext version) {
        String path = uri.getPath();
        String label = path != null ? path.substring(path.lastIndexOf('/') + 1) : uri.toString();
        return label + "@" + version;
    }

    public static final class Builder {

        public Builder source(URI uri, VersionContext version) {
            return sourceUri(uri).sourceVersion(version);
        }

        public Builder target(URI uri, VersionContext version) {
            return targetUri(uri).targetVersion(version);
        }
    }
}
