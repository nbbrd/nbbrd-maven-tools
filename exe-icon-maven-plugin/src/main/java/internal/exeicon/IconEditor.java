package internal.exeicon;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public final class IconEditor {

    private IconEditor() {}

    public static byte[] replaceIcon(byte[] executable, byte[] icon, Integer groupId) throws IOException {
        try (Context context = Context.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            context.eval(Source.newBuilder("js", loadScript(), "icons.js").build());
            Value allocate = context.eval("js", "(length) => new ArrayBuffer(length)");
            Value executableBuffer = toBuffer(allocate, executable);
            Value iconBuffer = toBuffer(allocate, icon);
            Value result = context.getBindings("js")
                    .getMember("ExeIcons")
                    .getMember("replaceIcon")
                    .execute(executableBuffer, iconBuffer, groupId);
            byte[] output = new byte[Math.toIntExact(result.getBufferSize())];
            result.readBuffer(0, output, 0, output.length);
            return output;
        }
    }

    private static Value toBuffer(Value allocate, byte[] bytes) {
        Value result = allocate.execute(bytes.length);
        for (int index = 0; index < bytes.length; index++) {
            result.writeBufferByte(index, bytes[index]);
        }
        return result;
    }

    private static String loadScript() throws IOException {
        try (InputStream stream = IconEditor.class.getResourceAsStream("icons.js")) {
            if (stream == null) {
                throw new IOException("Missing bundled icon editor: internal/exeicon/icons.js");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
