package edu.adaptivecc.experiments;

import edu.adaptivecc.metrics.CsvMetricsWriter;
import edu.adaptivecc.metrics.TransferMetrics;
import edu.adaptivecc.transport.UdpReceiver;
import edu.adaptivecc.transport.UdpSender;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Arrays;

public final class BenchmarkMain {
    private BenchmarkMain() { }

    public static void main(String[] args) throws Exception {
        int packetCount = intArg(args, "packets", 100);
        int payloadSize = intArg(args, "payload", 256);
        int timeoutMillis = intArg(args, "timeout", 250);
        int maxAttempts = intArg(args, "attempts", 3);
        Path output = Path.of(stringArg(args, "output", "results/loopback-benchmark.csv"));
        if (packetCount <= 0 || payloadSize <= 0 || payloadSize > 1200) throw new IllegalArgumentException("packets and payload must be positive; payload must be <= 1200");

        CsvMetricsWriter.writeHeader(output);
        byte[][] payloads = new byte[packetCount][payloadSize];
        for (int i = 0; i < packetCount; i++) Arrays.fill(payloads[i], (byte) (i & 0xff));

        for (int window : new int[]{1, 4, 16}) {
            try (UdpReceiver receiver = new UdpReceiver(0)) {
                receiver.start();
                UdpSender sender = new UdpSender(new InetSocketAddress("127.0.0.1", receiver.port()), timeoutMillis, maxAttempts, window);
                TransferMetrics metrics = sender.sendWithMetrics(payloads);
                CsvMetricsWriter.append(output, "localhost-window-" + window, payloadSize, window, metrics);
                System.out.printf("window=%d packets=%d goodput=%.2f B/s avgRtt=%.3f ms retransmissions=%d%n",
                        window, metrics.acknowledgedPackets(), metrics.goodputBytesPerSecond(), metrics.averageRttMillis(), metrics.retransmissions());
            }
        }
        System.out.println("CSV written to " + output.toAbsolutePath());
    }

    private static int intArg(String[] args, String name, int fallback) {
        return Integer.parseInt(stringArg(args, name, Integer.toString(fallback)));
    }

    private static String stringArg(String[] args, String name, String fallback) {
        String prefix = "--" + name + "=";
        for (String arg : args) if (arg.startsWith(prefix)) return arg.substring(prefix.length());
        return fallback;
    }
}
