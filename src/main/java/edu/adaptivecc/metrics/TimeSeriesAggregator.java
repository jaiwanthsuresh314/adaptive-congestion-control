package edu.adaptivecc.metrics;

import java.util.ArrayList;
import java.util.List;

/** Converts raw sender events into fixed-width interval observations. */
public final class TimeSeriesAggregator {
    private TimeSeriesAggregator() { }

    public static List<IntervalMetrics> aggregate(List<NetworkEvent> events, long intervalNanos) {
        if (events == null) throw new IllegalArgumentException("events cannot be null");
        if (intervalNanos <= 0) throw new IllegalArgumentException("interval must be positive");
        if (events.isEmpty()) return List.of();

        long origin = events.stream().mapToLong(NetworkEvent::timestampNanos).min().orElseThrow();
        long lastBucket = events.stream()
                .mapToLong(event -> bucket(event.timestampNanos(), origin, intervalNanos))
                .max().orElse(0);
        List<MutableInterval> buckets = new ArrayList<>();
        for (long i = 0; i <= lastBucket; i++) buckets.add(new MutableInterval(origin + i * intervalNanos, intervalNanos));

        for (NetworkEvent event : events) {
            MutableInterval bucket = buckets.get((int) bucket(event.timestampNanos(), origin, intervalNanos));
            bucket.accept(event);
        }
        return buckets.stream().map(MutableInterval::toImmutable).toList();
    }

    private static long bucket(long timestamp, long origin, long intervalNanos) {
        return Math.max(0, (timestamp - origin) / intervalNanos);
    }

    private static final class MutableInterval {
        private final long start;
        private final long duration;
        private long sent;
        private long retransmitted;
        private long acknowledged;
        private long lost;
        private long transmittedBytes;
        private long goodputBytes;
        private long rttSumNanos;
        private long rttSamples;

        private MutableInterval(long start, long duration) { this.start = start; this.duration = duration; }

        private void accept(NetworkEvent event) {
            switch (event.type()) {
                case PACKET_SENT -> { sent++; transmittedBytes += event.payloadBytes(); }
                case PACKET_RETRANSMITTED -> { retransmitted++; transmittedBytes += event.payloadBytes(); }
                case PACKET_ACKNOWLEDGED -> {
                    acknowledged++;
                    goodputBytes += event.payloadBytes();
                    if (event.rttNanos() > 0) { rttSumNanos += event.rttNanos(); rttSamples++; }
                }
                case PACKET_LOST -> lost++;
            }
        }

        private IntervalMetrics toImmutable() {
            long originals = sent + lost;
            return new IntervalMetrics(start, duration, sent, retransmitted, acknowledged, lost,
                    transmittedBytes, goodputBytes,
                    rttSamples == 0 ? 0.0 : rttSumNanos / (double) rttSamples / 1_000_000.0,
                    originals == 0 ? 0.0 : lost / (double) originals);
        }

    }
}
