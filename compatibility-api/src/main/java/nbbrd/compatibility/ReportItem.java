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
    RefVersion sourceVersion;

    @lombok.NonNull
    URI targetUri;

    @lombok.NonNull
    RefVersion targetVersion;

    public static @NonNull String toLabel(@NonNull URI uri, @NonNull RefVersion version) {
        String path = uri.getPath();
        String label;
        if (path != null) {
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            label = path.substring(path.lastIndexOf('/') + 1);
        } else {
            label = uri.toString();
        }
        return label + "@" + version;
    }

    public static final class Builder {

        public Builder source(URI uri, RefVersion version) {
            return sourceUri(uri).sourceVersion(version);
        }

        public Builder target(URI uri, RefVersion version) {
            return targetUri(uri).targetVersion(version);
        }
    }
}
