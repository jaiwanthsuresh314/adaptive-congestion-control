package edu.adaptivecc.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class CsvMetricsWriter {
    private CsvMetricsWriter() { }

    public static void writeHeader(Path output) throws IOException {
        ensureParent(output);
        Files.writeString(output, "scenario,packets,payload_bytes,window,acknowledged,transmissions,retransmissions,retransmission_rate,elapsed_ms,goodput_bytes_per_sec,average_rtt_ms,rtt_variation_ms\n");
    }

    public static void append(Path output, String scenario, int payloadBytes, int window, TransferMetrics metrics) throws IOException {
        String row = String.format(Locale.ROOT, "%s,%d,%d,%d,%d,%d,%d,%.6f,%.3f,%.3f,%.3f,%.3f\n",
                scenario, metrics.originalPackets(), payloadBytes, window, metrics.acknowledgedPackets(),
                metrics.totalTransmissions(), metrics.retransmissions(), metrics.retransmissionRate(),
                metrics.elapsedNanos() / 1_000_000.0, metrics.goodputBytesPerSecond(),
                metrics.averageRttMillis(), metrics.rttVariationMillis());
        Files.writeString(output, row, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    private static void ensureParent(Path output) throws IOException {
        if (output.getParent() != null) Files.createDirectories(output.getParent());
    }
}
