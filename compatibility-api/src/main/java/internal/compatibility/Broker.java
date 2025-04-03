package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Maven;
import nbbrd.design.SealedType;

import java.io.IOException;
import java.nio.file.Path;

@SealedType(Broker.PropertyBroker.class)
public abstract class Broker {

    private Broker() {
        // sealed class
    }

    public abstract @NonNull Version getVersion(@NonNull Maven maven, @NonNull Path project) throws IOException;

    public abstract void setVersion(@NonNull Maven maven, @NonNull Path project, @NonNull Version version) throws IOException;

    @lombok.Value
    @lombok.EqualsAndHashCode(callSuper = false)
    public static class PropertyBroker extends Broker {

        @NonNull
        String property;

        @Override
        public @NonNull Version getVersion(@NonNull Maven maven, @NonNull Path project) throws IOException {
            String result = maven.getProperty(project, property);
            return result != null ? Version.parse(result) : Version.NO_VERSION;
        }

        @Override
        public void setVersion(@NonNull Maven maven, @NonNull Path project, @NonNull Version version) throws IOException {
            maven.setProperty(project, property, version.toString());
        }
    }
}
