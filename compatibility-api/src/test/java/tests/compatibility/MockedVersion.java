package tests.compatibility;

import lombok.NonNull;

import java.io.IOException;

@lombok.Value
@lombok.Builder(toBuilder = true)
public class MockedVersion {

    @NonNull
    String versionId;

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
}
