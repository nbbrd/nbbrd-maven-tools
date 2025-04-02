package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Versioning;
import nbbrd.design.DirectImpl;
import nbbrd.service.ServiceProvider;
import org.semver4j.Semver;

import java.util.Comparator;

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
    public boolean isValidVersion(@NonNull Version version) {
        return org.semver4j.Semver.isValid(version.toString());
    }

    @Override
    public @NonNull Comparator<Version> getVersionComparator() {
        return Comparator.comparing(SemVer::toSemver);
    }

    private static Semver toSemver(Version version) {
        return Version.NO_VERSION.equals(version) ? Semver.ZERO : new Semver(version.toString());
    }
}
