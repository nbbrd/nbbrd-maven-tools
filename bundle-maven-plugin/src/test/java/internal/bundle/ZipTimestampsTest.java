package internal.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipTimestampsTest {

    @TempDir
    Path temp;

    @Test
    void producesIdenticalUtcHeadersAcrossJvmTimezonesAndDstGaps() throws Exception {
        List<String> timestamps = Arrays.asList(
                "2026-03-08T02:30:07Z", "2011-12-30T00:15:00Z", "1980-01-01T00:00:00Z", "2107-12-31T23:59:59Z");
        List<byte[]> baseline = new ArrayList<>();
        for (String timezone : Arrays.asList("UTC", "America/New_York", "Pacific/Apia")) {
            Path directory = temp.resolve(timezone.replace('/', '-'));
            Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
            if (!Files.isRegularFile(java)) {
                java = java.resolveSibling("java.exe");
            }
            List<String> command = new ArrayList<>(Arrays.asList(
                    java.toString(),
                    "-Duser.timezone=" + timezone,
                    "-cp",
                    System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                    ZipReproducibilityFixture.class.getName(),
                    directory.toString()));
            command.addAll(timestamps);
            Path log = temp.resolve(timezone.replace('/', '-') + ".log");
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            try {
                assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
                assertThat(process.exitValue())
                        .withFailMessage(new String(Files.readAllBytes(log), StandardCharsets.UTF_8))
                        .isZero();
            } finally {
                process.destroyForcibly();
            }
            for (int index = 0; index < timestamps.size(); index++) {
                byte[] bytes = Files.readAllBytes(directory.resolve((index + 1) + ".zip"));
                if ("UTC".equals(timezone)) {
                    baseline.add(bytes);
                } else {
                    assertThat(bytes)
                            .as("%s in %s", timestamps.get(index), timezone)
                            .isEqualTo(baseline.get(index));
                }
                LocalDateTime time = LocalDateTime.ofInstant(Instant.parse(timestamps.get(index)), ZoneOffset.UTC);
                int expected = (time.getYear() - 1980) << 25
                        | time.getMonthValue() << 21
                        | time.getDayOfMonth() << 16
                        | time.getHour() << 11
                        | time.getMinute() << 5
                        | time.getSecond() >> 1;
                assertThat(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(10))
                        .isEqualTo(expected);
            }
        }
    }
}
