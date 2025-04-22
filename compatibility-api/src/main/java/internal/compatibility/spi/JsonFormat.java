package internal.compatibility.spi;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import lombok.NonNull;
import nbbrd.compatibility.*;
import nbbrd.compatibility.spi.Format;
import nbbrd.design.DirectImpl;
import nbbrd.service.ServiceProvider;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

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
    public boolean canFormat(@NonNull Class<?> type) {
        return Job.class.equals(type) || Report.class.equals(type);
    }

    @Override
    public <T> Formatter<T> getFormatter(@NonNull Class<T> type) {
        return (value, writer) -> {
            try {
                GSON.toJson(requireNonNull(value, "value"), requireNonNull(writer, "writer"));
            } catch (JsonIOException ex) {
                throw new IOException(ex);
            }
        };
    }

    @Override
    public boolean canParse(@NonNull Class<?> type) {
        return Job.class.equals(type) || Report.class.equals(type);
    }

    @Override
    public <T> Parser<T> getParser(@NonNull Class<T> type) {
        return reader -> {
            try {
                return GSON.fromJson(requireNonNull(reader, "reader"), type);
            } catch (JsonIOException ex) {
                throw new IOException(ex);
            }
        };
    }

    @Override
    public DirectoryStream.@NonNull Filter<? super Path> getFormatFileFilter() {
        return file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static final Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(Path.class, representingAsString(Path.class, Paths::get, Path::toString))
            .registerTypeAdapter(Ref.class, representingAsString(Ref.class, Ref::parse, Ref::toString))
            .registerTypeAdapter(Version.class, representingAsString(Version.class, Version::parse, Version::toString))
            .registerTypeAdapter(LocalDate.class, representingAsString(LocalDate.class, LocalDate::parse, LocalDate::toString))
            .setPrettyPrinting()
            .create();

    private static <T> TypeAdapter<T> representingAsString(Class<T> type, Function<String, T> parser, Function<T, String> formatter) {
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
}
