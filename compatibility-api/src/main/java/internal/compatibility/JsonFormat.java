package internal.compatibility;

import com.google.gson.*;
import lombok.NonNull;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.spi.Format;
import nbbrd.design.DirectImpl;
import nbbrd.service.ServiceProvider;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;

@DirectImpl
@ServiceProvider
public final class JsonFormat implements Format {

    @Override
    public @NonNull String getFormatId() {
        return "json";
    }

    @Override
    public @NonNull String getFormatName() {
        return "JSON";
    }

    @Override
    public void formatJob(@NonNull Appendable appendable, @NonNull Job job) throws IOException {
        try {
            GSON.toJson(job, appendable);
        } catch (JsonIOException ex) {
            throw new IOException(ex);
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(Path.class, (JsonSerializer<Path>) JsonFormat::serializePath)
            .setPrettyPrinting()
            .create();

    private static JsonElement serializePath(Path project, Type type, JsonSerializationContext jsonSerializationContext) {
        return new JsonPrimitive(project.toString());
    }
}
