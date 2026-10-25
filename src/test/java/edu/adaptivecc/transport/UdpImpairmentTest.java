package edu.adaptivecc.transport;

import edu.adaptivecc.metrics.NetworkEvent;
import edu.adaptivecc.metrics.TimeSeriesRecorder;
import edu.adaptivecc.congestion.CongestionController;
import edu.adaptivecc.congestion.FixedRateController;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UdpImpairmentTest {
    @Test void retransmitsASelectedDroppedPacketAndRecordsEvents() throws Exception {
        try (DroppingReceiver receiver = new DroppingReceiver(0, 0)) {
            receiver.start();
            TimeSeriesRecorder recorder = new TimeSeriesRecorder();
            UdpSender sender = new UdpSender(new InetSocketAddress("127.0.0.1", receiver.port()), 50, 4,
                    new FixedRateController(2, Long.MAX_VALUE), recorder);
            byte[][] payloads = Arrays.stream(new String[]{"one", "two", "three"})
                    .map(value -> value.getBytes(StandardCharsets.UTF_8))
                    .toArray(byte[][]::new);

            assertEquals(3, sender.sendWithMetrics(payloads).acknowledgedPackets());
            assertEquals(1, receiver.droppedPackets());
            assertEquals(3, receiver.deliveredPackets());
            assertTrue(recorder.snapshot().stream().anyMatch(event -> event.type() == NetworkEvent.EventType.PACKET_RETRANSMITTED));
            assertTrue(recorder.snapshot().stream().anyMatch(event -> event.type() == NetworkEvent.EventType.PACKET_ACKNOWLEDGED));
        }
    }

    @Test void retransmitsAfterRepeatedLoss() throws Exception {
        try (ImpairmentReceiver receiver = new ImpairmentReceiver(Map.of(1L, 2), Map.of())) {
            receiver.start();
            UdpSender sender = new UdpSender(new InetSocketAddress("127.0.0.1", receiver.port()), 50, 4,
                    new FixedRateController(3, Long.MAX_VALUE));

            assertEquals(3, sender.send(List.of("one", "two", "three").stream()
                    .map(value -> value.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new)));
            assertEquals(2, receiver.droppedPackets());
            assertEquals(3, receiver.deliveredPackets());
        }
    }

    @Test void handlesDelayedAcknowledgementAndOutOfOrderAcks() throws Exception {
        try (ImpairmentReceiver receiver = new ImpairmentReceiver(
                Map.of(), Map.of(0L, 80))) {
            receiver.start();
            UdpSender sender = new UdpSender(new InetSocketAddress("127.0.0.1", receiver.port()), 150, 3,
                    new FixedRateController(2, Long.MAX_VALUE));

            byte[][] payloads = {"one".getBytes(StandardCharsets.UTF_8), "two".getBytes(StandardCharsets.UTF_8)};
            assertEquals(2, sender.sendWithMetrics(payloads).acknowledgedPackets());
            assertEquals(2, receiver.deliveredPackets());
        }
    }

    @Test void retransmittedAckDoesNotTrainCongestionController() throws Exception {
        try (DroppingReceiver receiver = new DroppingReceiver(0, 0)) {
            receiver.start();
            RecordingController controller = new RecordingController();
            UdpSender sender = new UdpSender(new InetSocketAddress("127.0.0.1", receiver.port()), 50, 4,
                    controller);
            byte[][] payloads = {"one".getBytes(StandardCharsets.UTF_8), "two".getBytes(StandardCharsets.UTF_8)};

            assertEquals(2, sender.send(payloads));
            assertEquals(1, controller.acknowledgements,
                    "only the first-transmission ACK should reach congestion control");
        }
    }

    private static final class RecordingController implements CongestionController {
        private int acknowledgements;

        @Override public int congestionWindow() { return 2; }
        @Override public long pacingDelayNanos(int payloadBytes) { return 0; }
        @Override public void onAcknowledgement(long rttNanos, int payloadBytes) { acknowledgements++; }
        @Override public void onTimeout() { }
    }
}
