package internal.compatibility;

import lombok.NonNull;
import nbbrd.compatibility.spi.JobEngine;
import nbbrd.compatibility.spi.JobExecutor;

import java.io.IOException;

public enum NoOpJobEngine implements JobEngine {

    INSTANCE;

    @Override
    public @NonNull String getId() {
        return "no_op";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public @NonNull JobExecutor getExecutor() throws IOException {
        throw new IOException("NoOp");
    }
}
