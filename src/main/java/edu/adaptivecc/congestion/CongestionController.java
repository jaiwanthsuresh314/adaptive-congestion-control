package edu.adaptivecc.congestion;

/** A transport-independent control policy driven by ACK and timeout feedback. */
public interface CongestionController {
    int congestionWindow();
    long pacingDelayNanos(int payloadBytes);
    void onAcknowledgement(long rttNanos, int payloadBytes);
    void onTimeout();
}
