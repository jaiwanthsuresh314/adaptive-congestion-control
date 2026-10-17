
package edu.adaptivecc.metrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Records network events during an experiment and provides
 * a time-ordered snapshot for analysis.
 */
public final class TimeSeriesRecorder {

    private final List<NetworkEvent> events = new ArrayList<>();

    /**
     * Records one network event.
     */
    public synchronized void record(NetworkEvent event) {
        events.add(Objects.requireNonNull(
                event, "event cannot be null"));
    }

    /**
     * Returns an immutable snapshot of events ordered by timestamp.
     */
    public synchronized List<NetworkEvent> snapshot() {
        List<NetworkEvent> copy = new ArrayList<>(events);

        copy.sort(Comparator.comparingLong(
                NetworkEvent::timestampNanos));

        return List.copyOf(copy);
    }

    /**
     * Returns the number of recorded events.
     */
    public synchronized int size() {
        return events.size();
    }

    /**
     * Removes all recorded events.
     */
    public synchronized void clear() {
        events.clear();
    }
}
