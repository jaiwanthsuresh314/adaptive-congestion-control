package edu.adaptivecc.metrics;

/** Aggregated observations for one fixed-width experiment interval. */
public record IntervalMetrics(
        long intervalStartNanos,
        long intervalDurationNanos,
        long packetsSent,
        long retransmittedPackets,
        long acknowledgedPackets,
        long lostPackets,
        long transmittedBytes,
        long goodputBytes,
        double averageRttMillis,
        double packetLossRate
) {
    public IntervalMetrics {
        if (intervalStartNanos < 0 || intervalDurationNanos <= 0
                || packetsSent < 0 || retransmittedPackets < 0 || acknowledgedPackets < 0
                || lostPackets < 0 || transmittedBytes < 0 || goodputBytes < 0
                || averageRttMillis < 0 || packetLossRate < 0 || packetLossRate > 1) {
            throw new IllegalArgumentException("interval metrics are outside valid bounds");
        }
    }

    public double sendingRateBytesPerSecond() {
        return transmittedBytes * 1_000_000_000.0 / intervalDurationNanos;
    }

    public double goodputBytesPerSecond() {
        return goodputBytes * 1_000_000_000.0 / intervalDurationNanos;
    }
}
