
package edu.adaptivecc.metrics;

import java.util.Objects;

/**
 * Represents a network event observed during an experiment.
 *
 * timestampNanos uses System.nanoTime() so event times can be
 * compared reliably within the same running process.
 */
public record NetworkEvent(
        long timestampNanos,
        EventType type,
        long packetId,
        long rttNanos,
        long payloadBytes
) {
    public NetworkEvent {
        Objects.requireNonNull(type, "type cannot be null");

      
        if (packetId < 0) {
            throw new IllegalArgumentException(
                    "packetId cannot be negative");
        }
        if (rttNanos < 0) {
            throw new IllegalArgumentException(
                    "rttNanos cannot be negative");
        }
        if (payloadBytes < 0) {
            throw new IllegalArgumentException(
                    "payloadBytes cannot be negative");
        }
    }

    public enum EventType {
        PACKET_SENT,
        PACKET_ACKNOWLEDGED,
        PACKET_LOST,
        PACKET_RETRANSMITTED,
        WINDOW_CHANGED
    }
}
