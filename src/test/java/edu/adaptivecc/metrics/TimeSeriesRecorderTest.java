
package edu.adaptivecc.metrics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimeSeriesRecorderTest {

    @Test
    void recordsEventsAndTracksSize() {
        TimeSeriesRecorder recorder = new TimeSeriesRecorder();

        recorder.record(new NetworkEvent(
                100L,
                NetworkEvent.EventType.PACKET_SENT,
                1L,
                0L,
                512L
        ));

        recorder.record(new NetworkEvent(
                200L,
                NetworkEvent.EventType.PACKET_ACKNOWLEDGED,
                1L,
                100L,
                512L
        ));

        assertEquals(2, recorder.size());
    }

    @Test
    void snapshotReturnsEventsInTimestampOrder() {
        TimeSeriesRecorder recorder = new TimeSeriesRecorder();

        NetworkEvent later = new NetworkEvent(
                300L,
                NetworkEvent.EventType.PACKET_ACKNOWLEDGED,
                2L,
                150L,
                256L
        );

        NetworkEvent earlier = new NetworkEvent(
                100L,
                NetworkEvent.EventType.PACKET_SENT,
                2L,
                0L,
                256L
        );

        recorder.record(later);
        recorder.record(earlier);

        List<NetworkEvent> events = recorder.snapshot();

        assertEquals(2, events.size());
        assertEquals(earlier, events.get(0));
        assertEquals(later, events.get(1));
    }

    @Test
    void snapshotIsImmutable() {
        TimeSeriesRecorder recorder = new TimeSeriesRecorder();

        recorder.record(new NetworkEvent(
                100L,
                NetworkEvent.EventType.PACKET_SENT,
                1L,
                0L,
                128L
        ));

        List<NetworkEvent> events = recorder.snapshot();

        assertThrows(
                UnsupportedOperationException.class,
                () -> events.add(new NetworkEvent(
                        200L,
                        NetworkEvent.EventType.PACKET_LOST,
                        1L,
                        0L,
                        128L
                ))
        );
    }

    @Test
    void clearRemovesAllEvents() {
        TimeSeriesRecorder recorder = new TimeSeriesRecorder();

        recorder.record(new NetworkEvent(
                100L,
                NetworkEvent.EventType.PACKET_SENT,
                1L,
                0L,
                128L
        ));

        recorder.clear();

        assertEquals(0, recorder.size());
        assertTrue(recorder.snapshot().isEmpty());
    }

    @Test
    void rejectsNullEvents() {
        TimeSeriesRecorder recorder = new TimeSeriesRecorder();

        assertThrows(
                NullPointerException.class,
                () -> recorder.record(null)
        );
    }
}
