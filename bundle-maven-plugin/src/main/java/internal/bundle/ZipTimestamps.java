package internal.bundle;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.experimental.UtilityClass;

/** Sets UTC DOS fields without changing the JVM timezone or relying on local DST rules. */
@UtilityClass
class ZipTimestamps {

    void validate(Instant timestamp) {
        int year = timestamp.atOffset(ZoneOffset.UTC).getYear();
        if (year < 1980 || year > 2107) {
            throw new IllegalArgumentException("ZIP outputTimestamp must be between 1980 and 2107");
        }
    }

    void normalize(Path output, List<Long> localHeaders, long centralDirectory, Instant timestamp) throws IOException {
        LocalDateTime time = LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC);
        int dos = (time.getYear() - 1980) << 25
                | time.getMonthValue() << 21
                | time.getDayOfMonth() << 16
                | time.getHour() << 11
                | time.getMinute() << 5
                | time.getSecond() >> 1;
        try (RandomAccessFile file = new RandomAccessFile(output.toFile(), "rw")) {
            long central = centralDirectory;
            for (long local : localHeaders) {
                requireSignature(file, local, 0x04034b50);
                file.seek(local + 10);
                file.writeInt(Integer.reverseBytes(dos));
                requireSignature(file, central, 0x02014b50);
                file.seek(central + 12);
                file.writeInt(Integer.reverseBytes(dos));
                file.seek(central + 28);
                int name = Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
                int extra = Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
                int comment = Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
                central += 46L + name + extra + comment;
            }
        }
    }

    private void requireSignature(RandomAccessFile file, long offset, int signature) throws IOException {
        file.seek(offset);
        if (Integer.reverseBytes(file.readInt()) != signature) {
            throw new IOException("Unexpected ZIP header at offset " + offset);
        }
    }
}
