package edu.adaptivecc.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Writes fixed-width metric intervals for offline analysis and dashboards. */
public final class IntervalMetricsCsvWriter {
    private IntervalMetricsCsvWriter() { }

    public static void write(Path output, List<IntervalMetrics> intervals) throws IOException {
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        StringBuilder csv = new StringBuilder("interval_start_nanos,interval_duration_nanos,packets_sent,retransmitted_packets,acknowledged_packets,lost_packets,transmitted_bytes,goodput_bytes,average_rtt_ms,packet_loss_rate,sending_rate_bytes_per_sec,goodput_bytes_per_sec\n");
        for (IntervalMetrics interval : intervals) {
            csv.append(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d,%.3f,%.6f,%.3f,%.3f\n",
                    interval.intervalStartNanos(), interval.intervalDurationNanos(), interval.packetsSent(),
                    interval.retransmittedPackets(), interval.acknowledgedPackets(), interval.lostPackets(),
                    interval.transmittedBytes(), interval.goodputBytes(), interval.averageRttMillis(),
                    interval.packetLossRate(), interval.sendingRateBytesPerSecond(), interval.goodputBytesPerSecond()));
        }
        Files.writeString(output, csv.toString());
    }
}
