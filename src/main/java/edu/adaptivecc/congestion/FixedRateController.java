package edu.adaptivecc.congestion;

public final class FixedRateController implements CongestionController {
    private final int window;
    private final long rateBytesPerSecond;

    public FixedRateController(int window, long rateBytesPerSecond) {
        if (window <= 0 || rateBytesPerSecond <= 0) throw new IllegalArgumentException("window and rate must be positive");
        this.window = window;
        this.rateBytesPerSecond = rateBytesPerSecond;
    }

    @Override public int congestionWindow() { return window; }

    @Override public long pacingDelayNanos(int payloadBytes) {
        if (payloadBytes < 0) throw new IllegalArgumentException("payload size cannot be negative");
        return Math.max(0L, Math.round(payloadBytes * 1_000_000_000.0 / rateBytesPerSecond));
    }

    @Override public void onAcknowledgement(long rttNanos, int payloadBytes) { }
    @Override public void onTimeout() { }
}
