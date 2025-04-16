package tests.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Tag;

import java.io.IOException;
import java.util.List;

@lombok.Value
@lombok.Builder
public class MockedProject {

    @NonNull
    String projectId;

    @lombok.Singular
    List<MockedVersion> versions;

    public MockedVersion getLatest() throws IOException {
        if (versions.isEmpty()) {
            throw new IOException("No version available");
        }
        return versions.get(versions.size() - 1);
    }

    public MockedVersion getByTag(Tag tag) throws IOException {
        for (MockedVersion version : versions) {
            if (version.getVersion().getTag().equals(tag)) {
                return version;
            }
        }
        throw new IOException("Tag not found: '" + tag + "'");
    }
}
