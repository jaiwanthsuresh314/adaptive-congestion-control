package edu.adaptivecc.metrics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeSeriesAggregatorTest {
    @Test void groupsEventsAndCalculatesRates() {
        long start = 1_000_000_000L;
        List<NetworkEvent> events = List.of(
                new NetworkEvent(start, NetworkEvent.EventType.PACKET_SENT, 0, 0, 100),
                new NetworkEvent(start + 10_000_000L, NetworkEvent.EventType.PACKET_ACKNOWLEDGED, 0, 4_000_000L, 100),
                new NetworkEvent(start + 20_000_000L, NetworkEvent.EventType.PACKET_RETRANSMITTED, 1, 0, 50),
                new NetworkEvent(start + 25_000_000L, NetworkEvent.EventType.PACKET_LOST, 1, 0, 50));

        List<IntervalMetrics> result = TimeSeriesAggregator.aggregate(events, 20_000_000L);

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).packetsSent());
        assertEquals(100, result.get(0).goodputBytes());
        assertEquals(5_000.0, result.get(0).goodputBytesPerSecond(), 0.001);
        assertEquals(1, result.get(1).retransmittedPackets());
        assertEquals(1, result.get(1).lostPackets());
        assertEquals(1.0, result.get(1).packetLossRate(), 0.001);
    }

    @Test void rejectsInvalidInterval() {
        assertThrows(IllegalArgumentException.class, () -> TimeSeriesAggregator.aggregate(List.of(), 0));
    }
}
