package edu.adaptivecc.experiments;

import edu.adaptivecc.metrics.CsvMetricsWriter;
import edu.adaptivecc.metrics.TimeSeriesCsvWriter;
import edu.adaptivecc.metrics.IntervalMetricsCsvWriter;
import edu.adaptivecc.metrics.TimeSeriesAggregator;
import edu.adaptivecc.metrics.TimeSeriesRecorder;
import edu.adaptivecc.metrics.TransferMetrics;
import edu.adaptivecc.congestion.AimdController;
import edu.adaptivecc.congestion.CongestionController;
import edu.adaptivecc.congestion.FixedRateController;
import edu.adaptivecc.transport.UdpReceiver;
import edu.adaptivecc.transport.UdpSender;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.SplittableRandom;

public final class BenchmarkMain {
    private BenchmarkMain() { }

    public static void main(String[] args) throws Exception {
        BenchmarkConfig config = BenchmarkConfig.fromArgs(args);
        int packetCount = config.packetCount();
        int payloadSize = config.payloadSize();
        int timeoutMillis = config.timeoutMillis();
        int maxAttempts = config.maxAttempts();
        String controllerName = config.controllerName();
        long rate = config.rateBytesPerSecond();
        Path output = config.output();
        ExperimentManifestWriter.write(output, config);

        CsvMetricsWriter.writeHeader(output);
        byte[][] payloads = new byte[packetCount][payloadSize];
        SplittableRandom random = new SplittableRandom(config.seed());
        for (int i = 0; i < packetCount; i++) {
            for (int j = 0; j < payloadSize; j++) payloads[i][j] = (byte) random.nextInt(256);
        }

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
                String scenario = config.scenario() + "-" + (controllerName.equalsIgnoreCase("aimd") ? "aimd"
                        : controllerName.equalsIgnoreCase("adaptive") ? "adaptive" : "fixed-window-" + window);
                CsvMetricsWriter.append(output, scenario, payloadSize, controller.congestionWindow(), metrics);
                System.out.printf("controller=%s window=%d packets=%d goodput=%.2f B/s avgRtt=%.3f ms retransmissions=%d%n",
                        controllerName, controller.congestionWindow(), metrics.acknowledgedPackets(), metrics.goodputBytesPerSecond(), metrics.averageRttMillis(), metrics.retransmissions());
                String eventFileName = output.getFileName().toString().replaceFirst("\\.csv$", "-" + scenario + "-events.csv");
                var events = recorder.snapshot();
                TimeSeriesCsvWriter.write(output.resolveSibling(eventFileName), events);
                String intervalFileName = output.getFileName().toString().replaceFirst("\\.csv$", "-" + scenario + "-intervals.csv");
                IntervalMetricsCsvWriter.write(output.resolveSibling(intervalFileName),
                        TimeSeriesAggregator.aggregate(events, 100_000_000L));
            }
        }
        System.out.println("CSV written to " + output.toAbsolutePath());
    }

}
