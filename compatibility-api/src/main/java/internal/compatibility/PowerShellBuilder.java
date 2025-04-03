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
public final class PowerShellBuilder implements Builder {

    @Override
    public @NonNull String getBuilderId() {
        return "powershell";
    }

    @Override
    public @NonNull String getBuilderName() {
        return "Powershell";
    }

    @Override
    public boolean isBuilderAvailable() {
        return OS.NAME.equals(OS.Name.WINDOWS);
    }

    @Override
    public @NonNull Build getBuild() throws IOException {
        return PowerShellBuild.getDefault();
    }
}
