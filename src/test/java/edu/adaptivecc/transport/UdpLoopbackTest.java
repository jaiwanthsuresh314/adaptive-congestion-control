package edu.adaptivecc.transport;

import edu.adaptivecc.congestion.FixedRateController;
import edu.adaptivecc.metrics.TimeSeriesRecorder;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UdpLoopbackTest {
    @Test void senderAndReceiverExchangeDataAndTermination() throws Exception {
        try (UdpReceiver receiver = new UdpReceiver(0)) {
            receiver.start();
            byte[][] payloads = {"one".getBytes(StandardCharsets.UTF_8), "two".getBytes(StandardCharsets.UTF_8)};
            UdpSender sender = new UdpSender(new InetSocketAddress("127.0.0.1", receiver.port()), 250, 3);
            assertEquals(2, sender.send(payloads));
            long deadline = System.nanoTime() + 1_000_000_000L;
            while (receiver.deliveredPackets() < 2 && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(2, receiver.deliveredPackets());
        }
    }

    @Test void senderSupportsBoundedWindow() throws Exception {
        try (UdpReceiver receiver = new UdpReceiver(0)) {
            receiver.start();
            byte[][] payloads = new byte[8][];
            for (int i = 0; i < payloads.length; i++) payloads[i] = ("payload-" + i).getBytes(StandardCharsets.UTF_8);
            UdpSender sender = new UdpSender(new InetSocketAddress("127.0.0.1", receiver.port()), 250, 3, 3);
            assertEquals(8, sender.send(payloads));
            long deadline = System.nanoTime() + 1_000_000_000L;
            while (receiver.deliveredPackets() < payloads.length && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(payloads.length, receiver.deliveredPackets());
        }
    }

    @Test void senderWithControllerAndRecorderWorksWithProductionReceiver() throws Exception {
        try (UdpReceiver receiver = new UdpReceiver(0)) {
            receiver.start();
            TimeSeriesRecorder recorder = new TimeSeriesRecorder();
            UdpSender sender = new UdpSender(new InetSocketAddress("127.0.0.1", receiver.port()), 250, 3,
                    new FixedRateController(1, Long.MAX_VALUE), recorder);
            assertEquals(1, sender.sendWithMetrics(new byte[][]{"test".getBytes(StandardCharsets.UTF_8)}).acknowledgedPackets());
            assertEquals(2, recorder.size());
        }
    }
}
