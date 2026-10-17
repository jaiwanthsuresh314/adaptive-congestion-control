package edu.adaptivecc.congestion;

public final class AimdController implements CongestionController {
    private final double decreaseFactor;
    private final int minimumWindow;
    private final int maximumWindow;
    private double congestionWindow;

    public AimdController(int initialWindow, double decreaseFactor, int minimumWindow, int maximumWindow) {
        if (initialWindow < minimumWindow || initialWindow > maximumWindow || decreaseFactor <= 0 || decreaseFactor >= 1 || minimumWindow <= 0 || maximumWindow < minimumWindow) {
            throw new IllegalArgumentException("invalid AIMD parameters");
        }
        this.congestionWindow = initialWindow;
        this.decreaseFactor = decreaseFactor;
        this.minimumWindow = minimumWindow;
        this.maximumWindow = maximumWindow;
    }

    @Override public int congestionWindow() { return (int) Math.floor(congestionWindow); }
    @Override public long pacingDelayNanos(int payloadBytes) { return 0; }

    @Override public void onAcknowledgement(long rttNanos, int payloadBytes) {
        congestionWindow = Math.min(maximumWindow, congestionWindow + 1.0 / congestionWindow);
    }

    @Override public void onTimeout() {
        congestionWindow = Math.max(minimumWindow, congestionWindow * decreaseFactor);
    }
}
