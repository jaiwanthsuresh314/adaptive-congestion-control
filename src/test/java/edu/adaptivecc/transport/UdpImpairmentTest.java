package edu.adaptivecc.transport;

import edu.adaptivecc.metrics.NetworkEvent;
import edu.adaptivecc.metrics.TimeSeriesRecorder;
import edu.adaptivecc.congestion.FixedRateController;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
}
