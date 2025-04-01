package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.spi.Versioning;
import nbbrd.design.DirectImpl;
import nbbrd.service.ServiceProvider;

@DirectImpl
@ServiceProvider
public final class SemVer implements Versioning {

    @Override
    public @NonNull String getVersioningId() {
        return "semver";
    }

    @Override
    public @NonNull String getVersioningName() {
        return "Semantic Versioning";
    }

    @Override
    public boolean isValidVersion(@NonNull CharSequence text) {
        return org.semver4j.Semver.isValid(text.toString());
    }

    @Override
    public boolean isOrdered(@NonNull String from, @NonNull String to) {
        return new org.semver4j.Semver(from).isLowerThanOrEqualTo(to);
    }
}
