package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.spi.Builder;
import nbbrd.compatibility.spi.Build;
import nbbrd.design.DirectImpl;
import nbbrd.io.sys.OS;
import nbbrd.service.ServiceProvider;

import java.io.IOException;

@DirectImpl
@ServiceProvider
public final class CommandLineBuilder implements Builder {

    @Override
    public @NonNull String getBuilderId() {
        return "command-line";
    }

    @Override
    public @NonNull String getBuilderName() {
        return "Command Line";
    }

    @Override
    public boolean isBuilderAvailable() {
        return OS.NAME.equals(OS.Name.WINDOWS);
    }

    @Override
    public @NonNull Build getBuild() throws IOException {
        return CommandLineBuild.getDefault();
    }
}
