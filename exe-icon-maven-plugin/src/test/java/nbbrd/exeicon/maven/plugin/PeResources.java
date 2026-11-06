package nbbrd.exeicon.maven.plugin;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

final class PeResources {

    record Key(int type, int id, int language) {}

    private final byte[] bytes;
    private final ByteBuffer buffer;
    private final int sections;
    private final int sectionCount;
    private final int resourceBase;
    private final Map<Key, byte[]> entries = new LinkedHashMap<>();

    private PeResources(byte[] bytes) {
        this.bytes = bytes;
        buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int pe = buffer.getInt(0x3c);
        sectionCount = Short.toUnsignedInt(buffer.getShort(pe + 6));
        int optional = pe + 24;
        sections = optional + Short.toUnsignedInt(buffer.getShort(pe + 20));
        int directories = optional + (buffer.getShort(optional) == 0x20b ? 112 : 96);
        resourceBase = fileOffset(buffer.getInt(directories + 16));
        readDirectory(0, 0, 0, 0);
    }

    static Map<Key, byte[]> read(byte[] bytes) {
        return new PeResources(bytes).entries;
    }

    static Map<String, byte[]> sections(byte[] bytes) {
        PeResources pe = new PeResources(bytes);
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (int index = 0; index < pe.sectionCount; index++) {
            int position = pe.sections + index * 40;
            String name = new String(bytes, position, 8, StandardCharsets.US_ASCII).replace("\0", "");
            int size = pe.buffer.getInt(position + 16);
            int offset = pe.buffer.getInt(position + 20);
            if (!name.equals(".rsrc")) {
                result.put(name, Arrays.copyOfRange(bytes, offset, offset + size));
            }
        }
        return result;
    }

    private int fileOffset(int rva) {
        for (int index = 0; index < sectionCount; index++) {
            int position = sections + index * 40;
            int address = buffer.getInt(position + 12);
            int size = Math.max(buffer.getInt(position + 8), buffer.getInt(position + 16));
            if (rva >= address && rva < address + size) {
                return buffer.getInt(position + 20) + rva - address;
            }
        }
        throw new IllegalArgumentException("Unmapped resource RVA: " + rva);
    }

    private void readDirectory(int relative, int depth, int type, int id) {
        int directory = resourceBase + relative;
        int count = Short.toUnsignedInt(buffer.getShort(directory + 12))
                + Short.toUnsignedInt(buffer.getShort(directory + 14));
        for (int index = 0; index < count; index++) {
            int position = directory + 16 + index * 8;
            int name = buffer.getInt(position);
            int offset = buffer.getInt(position + 4);
            if (name < 0) {
                throw new IllegalArgumentException("Named resources not expected in launcher fixture");
            }
            if (offset < 0) {
                readDirectory(offset & 0x7fffffff, depth + 1, depth == 0 ? name : type, depth == 1 ? name : id);
            } else {
                int data = resourceBase + offset;
                int start = fileOffset(buffer.getInt(data));
                entries.put(new Key(type, id, name), Arrays.copyOfRange(bytes, start, start + buffer.getInt(data + 4)));
            }
        }
    }
}
