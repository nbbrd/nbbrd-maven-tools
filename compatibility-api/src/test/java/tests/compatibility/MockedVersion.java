package tests.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Tag;
import nbbrd.compatibility.Version;

import java.io.IOException;

@lombok.Value
@lombok.Builder(toBuilder = true)
public class MockedVersion {

    @NonNull
    Version version;

    @NonNull
    Tag tag;

    String name;

    String value;

    public String getProperty(String propertyName) throws IOException {
        checkProperty(propertyName);
        return getValue();
    }

    public MockedVersion withProperty(String propertyName, String propertyValue) throws IOException {
        checkProperty(propertyName);
        return toBuilder().value(propertyValue).build();
    }

    private void checkProperty(String name) throws IOException {
        if (!this.name.equals(name)) {
            throw new IOException("Property " + name + " not found");
        }
    }

    public static Builder builderOf(CharSequence version) {
        return builder().version(Version.parse(version)).tag(Tag.parse("v" + version));
    }

    public static final class Builder {

        public Builder property(String name, String value) {
            return name(name).value(value);
        }
    }
}
