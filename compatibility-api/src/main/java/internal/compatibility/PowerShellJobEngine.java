package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.spi.JobEngine;
import nbbrd.compatibility.spi.JobExecutor;
import nbbrd.design.DirectImpl;
import nbbrd.io.sys.OS;
import nbbrd.service.ServiceProvider;

import java.io.IOException;

@DirectImpl
@ServiceProvider
public final class PowerShellJobEngine implements JobEngine {

    @Override
    public @NonNull String getJobEngineId() {
        return "powershell";
    }

    @Override
    public @NonNull String getJobEngineName() {
        return "Powershell";
    }

    @Override
    public boolean isJobEngineAvailable() {
        return OS.NAME.equals(OS.Name.WINDOWS);
    }

    @Override
    public @NonNull JobExecutor getExecutor() throws IOException {
        return PowerShellJobExecutor.getDefault();
    }
}
