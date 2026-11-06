package nbbrd.exeicon.maven.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import internal.exeicon.IconEditor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import org.apache.maven.plugin.MojoFailureException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReplaceIconMojoTest {

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"app.exe", "app64.exe"})
    void replacesNetBeansIconsAndPreservesOtherResources(String launcher) throws Exception {
        byte[] original = fixture(launcher);
        byte[] icon = largeIcon();
        byte[] result = IconEditor.replaceIcon(original, icon, 100);
        assertThat(result.length).isGreaterThan(original.length);
        Map<PeResources.Key, byte[]> before = PeResources.read(original);
        Map<PeResources.Key, byte[]> after = PeResources.read(result);
        before.forEach((key, value) -> {
            if (key.type() != 3 && key.type() != 14) {
                assertThat(after.get(key)).as("resource %s", key).isEqualTo(value);
            }
        });
        Map<String, byte[]> originalSections = PeResources.sections(original);
        Map<String, byte[]> resultSections = PeResources.sections(result);
        assertThat(resultSections.keySet()).containsExactlyElementsOf(originalSections.keySet());
        originalSections.forEach((name, data) ->
                assertThat(resultSections.get(name)).as("section %s", name).isEqualTo(data));
        assertIcon(after, icon);
        assertThat(IconEditor.replaceIcon(original, icon, null)).isEqualTo(result);
        assertThat(IconEditor.replaceIcon(original, icon, 100)).isEqualTo(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"app.exe", "app64.exe"})
    void restoresOriginalIconResourcesAfterRepeatedReplacement(String launcher) throws Exception {
        byte[] original = fixture(launcher);
        Map<PeResources.Key, byte[]> before = PeResources.read(original);
        var groups = before.keySet().stream()
                .filter(key -> key.type() == 14 && key.id() == 100)
                .toList();
        assertThat(groups).hasSize(1);
        PeResources.Key group = groups.get(0);
        byte[] originalIcon = extractIcon(before, group);
        byte[] replacementIcon = largeIcon();
        byte[] changed = IconEditor.replaceIcon(original, replacementIcon, 100);
        byte[] restored = IconEditor.replaceIcon(changed, originalIcon, 100);
        Map<PeResources.Key, byte[]> after = PeResources.read(restored);

        assertGroupImagesEqual(before, after, group);
        before.forEach((key, value) -> {
            if (key.type() != 3 && key.type() != 14) {
                assertThat(after.get(key)).as("resource %s", key).isEqualTo(value);
            }
        });
        assertThat(after.keySet().stream()
                        .filter(key -> key.type() != 3 && key.type() != 14)
                        .toList())
                .containsExactlyInAnyOrderElementsOf(before.keySet().stream()
                        .filter(key -> key.type() != 3 && key.type() != 14)
                        .toList());
        Map<String, byte[]> sections = PeResources.sections(restored);
        Map<String, byte[]> originalSections = PeResources.sections(original);
        assertThat(sections.keySet()).containsExactlyInAnyOrderElementsOf(originalSections.keySet());
        originalSections.forEach((name, data) ->
                assertThat(sections.get(name)).as("section %s", name).isEqualTo(data));

        byte[] repeated = IconEditor.replaceIcon(restored, originalIcon, 100);
        assertThat(repeated).as("repeated replacement with the same icon").isEqualTo(restored);
        for (int cycle = 0; cycle < 3; cycle++) {
            byte[] nextChanged = IconEditor.replaceIcon(repeated, replacementIcon, 100);
            assertIcon(PeResources.read(nextChanged), replacementIcon);
            byte[] nextRestored = IconEditor.replaceIcon(nextChanged, originalIcon, 100);
            assertThat(nextRestored)
                    .as("restored executable after cycle %s", cycle + 1)
                    .isEqualTo(restored);
            repeated = nextRestored;
        }
    }

    @Test
    void writesSeparateOutputAndLeavesInputsUntouched() throws Exception {
        ReplaceIconMojo mojo = configured();
        byte[] before = Files.readAllBytes(mojo.inputFile.toPath());
        mojo.execute();
        assertThat(Files.readAllBytes(mojo.inputFile.toPath())).isEqualTo(before);
        assertIcon(PeResources.read(Files.readAllBytes(mojo.outputFile.toPath())), largeIcon());
        try (var files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(".exe-icon-"));
        }
    }

    @Test
    void refusesToOverwriteEitherInput() throws Exception {
        ReplaceIconMojo mojo = configured();
        mojo.outputFile = mojo.inputFile;
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("distinct");
        mojo.outputFile = mojo.iconFile;
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("distinct");
    }

    @Test
    void rejectsMissingParametersAndFiles() throws Exception {
        ReplaceIconMojo mojo = new ReplaceIconMojo();
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("required");
        mojo = configured();
        Files.delete(mojo.inputFile.toPath());
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("regular");
    }

    @Test
    void rejectsInvalidGroups() throws Exception {
        ReplaceIconMojo mojo = configured();
        mojo.iconGroupId = -1;
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("65535");
        mojo.iconGroupId = 999;
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void updatesAllLanguagesOfTheSelectedGroup() throws Exception {
        byte[] executable = variant("languages");
        byte[] icon = largeIcon();
        Map<PeResources.Key, byte[]> result = PeResources.read(IconEditor.replaceIcon(executable, icon, 100));
        assertThat(result.keySet().stream().filter(key -> key.type() == 14)).hasSize(2);
        assertIcon(result, icon);
    }

    @Test
    void preservesOtherGroupsAndTheirSharedIcons() throws Exception {
        byte[] executable = variant("shared");
        assertThatThrownBy(() -> IconEditor.replaceIcon(executable, largeIcon(), null))
                .hasMessageContaining("Specify iconGroupId");
        Map<PeResources.Key, byte[]> before = PeResources.read(executable);
        Map<PeResources.Key, byte[]> after = PeResources.read(IconEditor.replaceIcon(executable, largeIcon(), 100));
        before.forEach((key, value) -> {
            if (key.type() == 3 || (key.type() == 14 && key.id() == 200)) {
                assertThat(after.get(key)).as("shared resource %s", key).isEqualTo(value);
            }
        });
    }

    @Test
    void refusesToCreateAnIconGroupWhenNoneExists() throws Exception {
        byte[] executable = variant("none");
        assertThatThrownBy(() -> IconEditor.replaceIcon(executable, largeIcon(), null))
                .hasMessageContaining("found 0 icon groups");
    }

    @Test
    void acceptsMultiResolutionBitmapIcons() throws Exception {
        byte[] result = IconEditor.replaceIcon(fixture("app64.exe"), fixture("app.ico"), 100);
        Map<PeResources.Key, byte[]> resources = PeResources.read(result);
        for (var entry : resources.entrySet()) {
            if (entry.getKey().type() == 14) {
                ByteBuffer group = ByteBuffer.wrap(entry.getValue()).order(ByteOrder.LITTLE_ENDIAN);
                ByteBuffer ico = ByteBuffer.wrap(fixture("app.ico")).order(ByteOrder.LITTLE_ENDIAN);
                int count = Short.toUnsignedInt(ico.getShort(4));
                assertThat(count).isGreaterThan(1);
                assertThat(group.getShort(4)).isEqualTo((short) count);
                for (int index = 0; index < count; index++) {
                    int start = ico.getInt(18 + index * 16);
                    int size = ico.getInt(14 + index * 16);
                    int id = Short.toUnsignedInt(group.getShort(18 + index * 14));
                    assertThat(resources.get(
                                    new PeResources.Key(3, id, entry.getKey().language())))
                            .isEqualTo(Arrays.copyOfRange(ico.array(), start, start + size));
                }
            }
        }
    }

    @Test
    void preservesExistingOutputOnMalformedInput() throws Exception {
        ReplaceIconMojo mojo = configured();
        Files.write(mojo.outputFile.toPath(), new byte[] {42});
        Files.write(mojo.iconFile.toPath(), new byte[] {0, 1, 2});
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("ICO");
        assertThat(Files.readAllBytes(mojo.outputFile.toPath())).containsExactly((byte) 42);
        Files.write(mojo.iconFile.toPath(), largeIcon());
        Files.write(mojo.inputFile.toPath(), new byte[128]);
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
        assertThat(Files.readAllBytes(mojo.outputFile.toPath())).containsExactly((byte) 42);
    }

    @Test
    void rejectsOutOfBoundsIconImages() throws Exception {
        ReplaceIconMojo mojo = configured();
        byte[] icon = largeIcon();
        ByteBuffer.wrap(icon).order(ByteOrder.LITTLE_ENDIAN).putInt(18, icon.length);
        Files.write(mojo.iconFile.toPath(), icon);
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("outside");
    }

    @Test
    void rejectsUnsupportedImagePayloads() throws Exception {
        ReplaceIconMojo mojo = configured();
        byte[] icon = largeIcon();
        Arrays.fill(icon, 22, 38, (byte) 0);
        Files.write(mojo.iconFile.toPath(), icon);
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("header");
    }

    @Test
    void refusesOutputAliases() throws Exception {
        ReplaceIconMojo mojo = configured();
        Files.createLink(mojo.outputFile.toPath(), mojo.inputFile.toPath());
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("distinct");
    }

    @Test
    void reportsWriteErrorsWithoutModifyingInput() throws Exception {
        ReplaceIconMojo mojo = configured();
        byte[] original = Files.readAllBytes(mojo.inputFile.toPath());
        Files.createDirectory(mojo.outputFile.toPath());
        Files.writeString(mojo.outputFile.toPath().resolve("keep.txt"), "keep");
        assertThatThrownBy(mojo::execute).isInstanceOf(org.apache.maven.plugin.MojoExecutionException.class);
        assertThat(Files.readAllBytes(mojo.inputFile.toPath())).isEqualTo(original);
        assertThat(Files.readString(mojo.outputFile.toPath().resolve("keep.txt")))
                .isEqualTo("keep");
    }

    @Test
    void rejectsSignedExecutables() throws Exception {
        ReplaceIconMojo mojo = configured();
        byte[] executable = fixture("app64.exe");
        int length = executable.length;
        executable = Arrays.copyOf(executable, length + 8);
        ByteBuffer buffer = ByteBuffer.wrap(executable).order(ByteOrder.LITTLE_ENDIAN);
        int optionalHeader = buffer.getInt(0x3c) + 24;
        buffer.putInt(optionalHeader + 112 + 8 * 4, length);
        buffer.putInt(optionalHeader + 112 + 8 * 4 + 4, 8);
        Files.write(mojo.inputFile.toPath(), executable);
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("signed");
    }

    @Test
    void skipDoesNotReadOrWriteFiles() throws Exception {
        ReplaceIconMojo mojo = new ReplaceIconMojo();
        mojo.skip = true;
        mojo.execute();
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }

    private ReplaceIconMojo configured() throws Exception {
        ReplaceIconMojo mojo = new ReplaceIconMojo();
        mojo.inputFile = directory.resolve("input.exe").toFile();
        mojo.iconFile = directory.resolve("input.ico").toFile();
        mojo.outputFile = directory.resolve("output.exe").toFile();
        Files.write(mojo.inputFile.toPath(), fixture("app64.exe"));
        Files.write(mojo.iconFile.toPath(), largeIcon());
        return mojo;
    }

    private static byte[] variant(String kind) throws Exception {
        try (Context context = Context.newBuilder("js")
                        .option("engine.WarnInterpreterOnly", "false")
                        .build();
                InputStream script = ReplaceIconMojoTest.class.getResourceAsStream("/fixtures.js")) {
            assertThat(script).isNotNull();
            context.eval("js", new String(script.readAllBytes(), StandardCharsets.UTF_8));
            byte[] original = fixture("app64.exe");
            Value input =
                    context.eval("js", "(length) => new ArrayBuffer(length)").execute(original.length);
            for (int index = 0; index < original.length; index++) {
                input.writeBufferByte(index, original[index]);
            }
            Value result = context.getBindings("js")
                    .getMember("Fixtures")
                    .getMember("variant")
                    .execute(input, kind);
            byte[] bytes = new byte[Math.toIntExact(result.getBufferSize())];
            result.readBuffer(0, bytes, 0, bytes.length);
            return bytes;
        }
    }

    private static void assertIcon(Map<PeResources.Key, byte[]> entries, byte[] ico) {
        var groups = entries.entrySet().stream()
                .filter(entry -> entry.getKey().type() == 14)
                .toList();
        assertThat(groups).isNotEmpty();
        for (var entry : groups) {
            assertThat(entry.getKey().id()).isEqualTo(100);
            ByteBuffer group = ByteBuffer.wrap(entry.getValue()).order(ByteOrder.LITTLE_ENDIAN);
            assertThat(group.getShort(4)).isEqualTo((short) 1);
            int iconId = Short.toUnsignedInt(group.getShort(18));
            assertThat(entries.get(new PeResources.Key(3, iconId, entry.getKey().language())))
                    .isEqualTo(Arrays.copyOfRange(ico, 22, ico.length));
        }
    }

    private static byte[] extractIcon(Map<PeResources.Key, byte[]> entries, PeResources.Key key) {
        byte[] groupBytes = entries.get(key);
        assertThat(groupBytes).as("original icon group %s", key).isNotNull();
        ByteBuffer group = ByteBuffer.wrap(groupBytes).order(ByteOrder.LITTLE_ENDIAN);
        int count = Short.toUnsignedInt(group.getShort(4));
        int total = 6 + count * 16;
        for (int index = 0; index < count; index++) {
            total += group.getInt(6 + index * 14 + 8);
        }
        ByteBuffer ico = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        ico.put(groupBytes, 0, 6);
        int offset = 6 + count * 16;
        for (int index = 0; index < count; index++) {
            int position = 6 + index * 14;
            int id = Short.toUnsignedInt(group.getShort(position + 12));
            byte[] image = entries.get(new PeResources.Key(3, id, key.language()));
            assertThat(image).as("original icon image %s", id).isNotNull();
            assertThat(image.length).isEqualTo(group.getInt(position + 8));
            ico.put(groupBytes, position, 12).putInt(offset);
            System.arraycopy(image, 0, ico.array(), offset, image.length);
            offset += image.length;
        }
        return ico.array();
    }

    private static void assertGroupImagesEqual(
            Map<PeResources.Key, byte[]> before, Map<PeResources.Key, byte[]> after, PeResources.Key key) {
        byte[] beforeGroup = before.get(key);
        byte[] afterGroup = after.get(key);
        assertThat(afterGroup).as("restored icon group %s", key).isNotNull();
        ByteBuffer original = ByteBuffer.wrap(beforeGroup).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer restored = ByteBuffer.wrap(afterGroup).order(ByteOrder.LITTLE_ENDIAN);
        int count = Short.toUnsignedInt(original.getShort(4));
        assertThat(Arrays.copyOfRange(afterGroup, 0, 6)).isEqualTo(Arrays.copyOfRange(beforeGroup, 0, 6));
        for (int index = 0; index < count; index++) {
            int position = 6 + index * 14;
            assertThat(Arrays.copyOfRange(afterGroup, position, position + 12))
                    .as("icon metadata %s", index)
                    .isEqualTo(Arrays.copyOfRange(beforeGroup, position, position + 12));
            int originalId = Short.toUnsignedInt(original.getShort(position + 12));
            int restoredId = Short.toUnsignedInt(restored.getShort(position + 12));
            assertThat(after.get(new PeResources.Key(3, restoredId, key.language())))
                    .as("icon image %s", index)
                    .isEqualTo(before.get(new PeResources.Key(3, originalId, key.language())));
        }
    }

    static byte[] fixture(String name) throws IOException {
        try (InputStream stream = ReplaceIconMojoTest.class.getResourceAsStream("/netbeans/" + name)) {
            if (stream == null) {
                throw new IOException("Missing fixture: " + name);
            }
            return stream.readAllBytes();
        }
    }

    private static byte[] largeIcon() throws IOException {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(0);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                image.setRGB(x, y, random.nextInt() | 0xff000000);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", png)) {
            throw new IOException("PNG writer unavailable");
        }
        byte[] data = png.toByteArray();
        ByteBuffer ico = ByteBuffer.allocate(22 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short) 0).putShort((short) 1).putShort((short) 1);
        ico.putInt(0)
                .putShort((short) 1)
                .putShort((short) 32)
                .putInt(data.length)
                .putInt(22)
                .put(data);
        return ico.array();
    }
}
