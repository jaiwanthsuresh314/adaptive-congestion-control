package edu.adaptivecc.metrics;

import java.util.List;

public record TransferMetrics(int originalPackets, int acknowledgedPackets, int totalTransmissions,
                              long originalBytes, long elapsedNanos, List<Long> rttSamplesNanos) {
    public TransferMetrics {
        if (originalPackets < 0 || acknowledgedPackets < 0 || totalTransmissions < 0 || originalBytes < 0 || elapsedNanos < 0) {
            throw new IllegalArgumentException("metrics cannot be negative");
        }
        rttSamplesNanos = List.copyOf(rttSamplesNanos);
    }

    public int retransmissions() { return Math.max(0, totalTransmissions - originalPackets); }

    public double retransmissionRate() {
        return originalPackets == 0 ? 0.0 : (double) retransmissions() / originalPackets;
    }

    public double averageRttMillis() {
        return rttSamplesNanos.isEmpty() ? 0.0 : rttSamplesNanos.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1_000_000.0;
    }

    public double rttVariationMillis() {
        if (rttSamplesNanos.size() < 2) return 0.0;
        double mean = rttSamplesNanos.stream().mapToLong(Long::longValue).average().orElse(0.0);
        return Math.sqrt(rttSamplesNanos.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0.0)) / 1_000_000.0;
    }

    public double goodputBytesPerSecond() {
        return elapsedNanos == 0 ? 0.0 : originalBytes * 1_000_000_000.0 / elapsedNanos;
    }

    public double throughputBytesPerSecond() {
        return elapsedNanos == 0 ? 0.0 : (long) acknowledgedPackets * originalBytesPerPacket() * 1_000_000_000.0 / elapsedNanos;
    }

    private long originalBytesPerPacket() {
        return originalPackets == 0 ? 0 : originalBytes / originalPackets;
    }
}
