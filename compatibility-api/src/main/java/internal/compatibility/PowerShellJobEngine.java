package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.spi.JobEngine;
import nbbrd.compatibility.spi.JobExecutor;
import nbbrd.io.sys.OS;
import nbbrd.service.ServiceProvider;

import java.io.IOException;

@ServiceProvider
public final class PowerShellJobEngine implements JobEngine {

    @Override
    public @NonNull String getId() {
        return "powershell";
    }

    @Override
    public boolean isAvailable() {
        return OS.NAME.equals(OS.Name.WINDOWS);
    }

    @Override
    public @NonNull JobExecutor getExecutor() throws IOException {
        return PowerShellJobExecutor.getDefault();
    }
}
