package edu.adaptivecc.experiments;

import edu.adaptivecc.metrics.CsvMetricsWriter;
import edu.adaptivecc.metrics.TimeSeriesCsvWriter;
import edu.adaptivecc.metrics.TimeSeriesRecorder;
import edu.adaptivecc.metrics.TransferMetrics;
import edu.adaptivecc.congestion.AimdController;
import edu.adaptivecc.congestion.CongestionController;
import edu.adaptivecc.congestion.FixedRateController;
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
        String controllerName = stringArg(args, "controller", "fixed");
        long rate = longArg(args, "rate", 1_000_000L);
        Path output = Path.of(stringArg(args, "output", "results/loopback-benchmark.csv"));
        if (packetCount <= 0 || payloadSize <= 0 || payloadSize > 1200) throw new IllegalArgumentException("packets and payload must be positive; payload must be <= 1200");

        CsvMetricsWriter.writeHeader(output);
        byte[][] payloads = new byte[packetCount][payloadSize];
        for (int i = 0; i < packetCount; i++) Arrays.fill(payloads[i], (byte) (i & 0xff));

        int[] windows = controllerName.equalsIgnoreCase("aimd") || controllerName.equalsIgnoreCase("adaptive") ? new int[]{1} : new int[]{1, 4, 16};
        for (int window : windows) {
            try (UdpReceiver receiver = new UdpReceiver(0)) {
                receiver.start();
                CongestionController controller = controllerName.equalsIgnoreCase("aimd")
                        ? new AimdController(1, 0.5, 1, 16)
                        : controllerName.equalsIgnoreCase("adaptive")
                        ? new edu.adaptivecc.congestion.AdaptiveController()
                        : new FixedRateController(window, rate);
                TimeSeriesRecorder recorder = new TimeSeriesRecorder();
                UdpSender sender = new UdpSender(new InetSocketAddress("127.0.0.1", receiver.port()), timeoutMillis, maxAttempts, controller, recorder);
                TransferMetrics metrics = sender.sendWithMetrics(payloads);
                String scenario = controllerName.equalsIgnoreCase("aimd") ? "localhost-aimd"
                        : controllerName.equalsIgnoreCase("adaptive") ? "localhost-adaptive" : "localhost-fixed-window-" + window;
                CsvMetricsWriter.append(output, scenario, payloadSize, controller.congestionWindow(), metrics);
                System.out.printf("controller=%s window=%d packets=%d goodput=%.2f B/s avgRtt=%.3f ms retransmissions=%d%n",
                        controllerName, controller.congestionWindow(), metrics.acknowledgedPackets(), metrics.goodputBytesPerSecond(), metrics.averageRttMillis(), metrics.retransmissions());
                String eventFileName = output.getFileName().toString().replaceFirst("\\.csv$", "-" + scenario + "-events.csv");
                TimeSeriesCsvWriter.write(output.resolveSibling(eventFileName), recorder.snapshot());
            }
        }
        System.out.println("CSV written to " + output.toAbsolutePath());
    }

    private static int intArg(String[] args, String name, int fallback) {
        return Integer.parseInt(stringArg(args, name, Integer.toString(fallback)));
    }

    private static long longArg(String[] args, String name, long fallback) {
        return Long.parseLong(stringArg(args, name, Long.toString(fallback)));
    }

    private static String stringArg(String[] args, String name, String fallback) {
        String prefix = "--" + name + "=";
        for (String arg : args) if (arg.startsWith(prefix)) return arg.substring(prefix.length());
        return fallback;
    }
}
