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
    Version sourceVersion;

    @lombok.NonNull
    URI targetUri;

    @lombok.NonNull
    Version targetVersion;

    public static @NonNull String toLabel(@NonNull URI uri, @NonNull Version version) {
        String path = uri.getPath();
        String label = path != null ? path.substring(path.lastIndexOf('/') + 1) : uri.toString();
        return label + "@" + version;
    }

    public static final class Builder {

        public Builder source(URI uri, CharSequence version) {
            return sourceUri(uri).sourceVersion(Version.parse(version));
        }

        public Builder target(URI uri, CharSequence version) {
            return targetUri(uri).targetVersion(Version.parse(version));
        }
    }
}
