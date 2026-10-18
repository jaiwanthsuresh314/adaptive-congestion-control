
package edu.adaptivecc.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeSeriesCsvWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesEventsToCsv() throws IOException {
        Path output = tempDir.resolve("events.csv");

        List<NetworkEvent> events = List.of(
                new NetworkEvent(
                        100L,
                        NetworkEvent.EventType.PACKET_SENT,
                        1L,
                        0L,
                        512L
                ),
                new NetworkEvent(
                        200L,
                        NetworkEvent.EventType.PACKET_ACKNOWLEDGED,
                        1L,
                        100L,
                        512L
                )
        );

        TimeSeriesCsvWriter.write(output, events);

        List<String> lines = Files.readAllLines(output);

        assertEquals(
                "timestamp_nanos,event_type,packet_id,rtt_nanos,payload_bytes",
                lines.get(0)
        );

        assertEquals(
                "100,PACKET_SENT,1,0,512",
                lines.get(1)
        );

        assertEquals(
                "200,PACKET_ACKNOWLEDGED,1,100,512",
                lines.get(2)
        );

        assertEquals(3, lines.size());
    }

    @Test
    void writesHeaderWhenThereAreNoEvents() throws IOException {
        Path output = tempDir.resolve("empty.csv");

        TimeSeriesCsvWriter.write(output, List.of());

        List<String> lines = Files.readAllLines(output);

        assertEquals(1, lines.size());
        assertEquals(
                "timestamp_nanos,event_type,packet_id,rtt_nanos,payload_bytes",
                lines.get(0)
        );
    }
}
