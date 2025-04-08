package internal.compatibility;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import lombok.NonNull;
import nbbrd.compatibility.Filter;
import nbbrd.compatibility.Job;
import nbbrd.compatibility.Report;
import nbbrd.compatibility.Version;
import nbbrd.compatibility.spi.Format;
import nbbrd.design.DirectImpl;
import nbbrd.service.ServiceProvider;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.Function;

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
    public boolean canFormatJob() {
        return true;
    }

    @Override
    public void formatJob(@NonNull Appendable appendable, @NonNull Job job) throws IOException {
        try {
            GSON.toJson(job, appendable);
        } catch (JsonIOException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public boolean canFormatReport() {
        return true;
    }

    @Override
    public void formatReport(@NonNull Appendable appendable, @NonNull Report report) throws IOException {
        try {
            GSON.toJson(report, appendable);
        } catch (JsonIOException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public DirectoryStream.@NonNull Filter<? super Path> getFormatFileFilter() {
        return file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static final Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(Path.class, create(Path.class, Paths::get, Path::toString))
            .registerTypeAdapter(Version.class, create(Version.class, Version::parse, Version::toString))
            .registerTypeAdapter(Filter.class, (JsonSerializer<Filter>) JsonFormat::serializeFilter)
            .setPrettyPrinting()
            .create();

    private static <T> TypeAdapter<T> create(Class<T> type, Function<String, T> parser, Function<T, String> formatter) {
        return new TypeAdapter<T>() {
            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }
                String s = in.nextString();
                try {
                    return parser.apply(s);
                } catch (IllegalArgumentException e) {
                    throw new JsonSyntaxException("Failed parsing '" + s + "' as " + type + "; at path " + in.getPreviousPath(), e);
                }
            }

            @Override
            public void write(JsonWriter out, T value) throws IOException {
                out.value(value == null ? null : formatter.apply(value));
            }
        };
    }

    private static JsonElement serializeFilter(Filter src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        result.addProperty("ref", src.getRef());
        result.addProperty("from", src.getFrom().toString());
        result.addProperty("to", src.getTo().toString());
        return result;
    }
}
