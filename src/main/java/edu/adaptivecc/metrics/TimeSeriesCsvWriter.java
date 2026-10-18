
package edu.adaptivecc.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TimeSeriesCsvWriter {

    private TimeSeriesCsvWriter() {
    }

    /**
     * Writes network events to a CSV file.
     * Existing file contents are replaced.
     */
    public static void write(
            Path output,
            List<NetworkEvent> events
    ) throws IOException {

        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }

        StringBuilder csv = new StringBuilder();

        csv.append(
                "timestamp_nanos,event_type,packet_id,rtt_nanos,payload_bytes\n"
        );

        for (NetworkEvent event : events) {
            csv.append(event.timestampNanos()).append(",");
            csv.append(event.type()).append(",");
            csv.append(event.packetId()).append(",");
            csv.append(event.rttNanos()).append(",");
            csv.append(event.payloadBytes()).append("\n");
        }

        Files.writeString(output, csv.toString());
    }
}
