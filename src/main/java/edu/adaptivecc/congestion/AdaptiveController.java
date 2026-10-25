package edu.adaptivecc.congestion;

import java.util.ArrayDeque;

/**
 * Deterministic delay- and loss-aware congestion controller.
 *
 * RTT is tracked with EWMA smoothing. The minimum RTT over a recent window of
 * samples is used as a propagation-delay baseline; increasing RTT above that
 * baseline is treated as a queueing-delay signal once it persists for a
 * configured number of consecutive ACKs. ACKs during a healthy interval grow
 * the window cautiously. Persistent delay and timeouts reduce it, each with its
 * own decrease factor. Sending is paced at the measured delivery rate, clamped
 * to the configured rate bounds.
 */
public final class AdaptiveController implements CongestionController {
    public record Parameters(int initialWindow, int minimumWindow, int maximumWindow,
                             double rttAlpha, double variationBeta,
                             double delayThreshold, double severeDelayThreshold,
                             double decreaseFactor, int increaseEveryAcks,
                             long minimumRateBytesPerSecond, long maximumRateBytesPerSecond,
                             double timeoutDecreaseFactor, int persistenceAcks, int baselineWindowAcks) {
        public Parameters {
            if (minimumWindow <= 0 || initialWindow < minimumWindow || initialWindow > maximumWindow || maximumWindow < minimumWindow) {
                throw new IllegalArgumentException("invalid window bounds");
            }
            if (rttAlpha <= 0 || rttAlpha > 1 || variationBeta <= 0 || variationBeta > 1) {
                throw new IllegalArgumentException("smoothing factors must be in (0, 1]");
            }
            if (delayThreshold < 0 || severeDelayThreshold <= delayThreshold || decreaseFactor <= 0 || decreaseFactor >= 1) {
                throw new IllegalArgumentException("invalid delay or decrease thresholds");
            }
            if (increaseEveryAcks <= 0 || minimumRateBytesPerSecond <= 0 || maximumRateBytesPerSecond < minimumRateBytesPerSecond) {
                throw new IllegalArgumentException("invalid rate parameters");
            }
            if (timeoutDecreaseFactor <= 0 || timeoutDecreaseFactor >= 1) {
                throw new IllegalArgumentException("timeout decrease factor must be in (0, 1)");
            }
            if (persistenceAcks <= 0 || baselineWindowAcks <= 0) {
                throw new IllegalArgumentException("persistence and baseline window must be positive");
            }
        }

        /**
         * Original 11-argument form. Keeps the original behaviour: every delayed
         * ACK may reduce the window, timeouts use {@code decreaseFactor}, and the
         * RTT baseline is the minimum over all samples.
         */
        public Parameters(int initialWindow, int minimumWindow, int maximumWindow,
                          double rttAlpha, double variationBeta,
                          double delayThreshold, double severeDelayThreshold,
                          double decreaseFactor, int increaseEveryAcks,
                          long minimumRateBytesPerSecond, long maximumRateBytesPerSecond) {
            this(initialWindow, minimumWindow, maximumWindow, rttAlpha, variationBeta,
                    delayThreshold, severeDelayThreshold, decreaseFactor, increaseEveryAcks,
                    minimumRateBytesPerSecond, maximumRateBytesPerSecond,
                    decreaseFactor, 1, Integer.MAX_VALUE);
        }

        public static Parameters defaults() {
            return new Parameters(2, 1, 64, 0.125, 0.25, 0.25, 0.75, 0.7, 4, 10_000, 10_000_000,
                    0.5, 3, 500);
        }
    }

    private final Parameters parameters;
    /** Sliding-window minimum: (sample index, RTT) pairs with increasing RTT. */
    private final ArrayDeque<long[]> baselineCandidates = new ArrayDeque<>();
    private long sampleIndex;
    private double window;
    private long smoothedRttNanos;
    private long rttVariationNanos;
    private long baselineRttNanos;
    private long estimatedRateBytesPerSecond;
    private int healthyAcknowledgements;
    private int consecutiveDelayedAcknowledgements;

    public AdaptiveController() { this(Parameters.defaults()); }

    public AdaptiveController(Parameters parameters) {
        this.parameters = parameters;
        this.window = parameters.initialWindow();
    }

    @Override public int congestionWindow() { return (int) Math.max(parameters.minimumWindow(), Math.floor(window)); }

    @Override public long pacingDelayNanos(int payloadBytes) {
        if (payloadBytes < 0) throw new IllegalArgumentException("payload size cannot be negative");
        long rate = estimatedRateBytesPerSecond == 0 ? parameters.minimumRateBytesPerSecond() : estimatedRateBytesPerSecond;
        return Math.round(payloadBytes * 1_000_000_000.0 / rate);
    }

    @Override public void onAcknowledgement(long rttNanos, int payloadBytes) {
        if (rttNanos <= 0 || payloadBytes <= 0) throw new IllegalArgumentException("ACK feedback must be positive");
        if (smoothedRttNanos == 0) {
            smoothedRttNanos = rttNanos;
            rttVariationNanos = 0;
        } else {
            rttVariationNanos = ewma(rttVariationNanos, Math.abs(smoothedRttNanos - rttNanos), parameters.variationBeta());
            smoothedRttNanos = ewma(smoothedRttNanos, rttNanos, parameters.rttAlpha());
        }
        baselineRttNanos = updateBaseline(rttNanos);

        long instantaneousRate = Math.max(parameters.minimumRateBytesPerSecond(),
                Math.round(payloadBytes * 1_000_000_000.0 / rttNanos));
        estimatedRateBytesPerSecond = estimatedRateBytesPerSecond == 0
                ? clampRate(instantaneousRate)
                : clampRate(ewma(estimatedRateBytesPerSecond, instantaneousRate, parameters.rttAlpha()));

        double delayRatio = (double) Math.max(0, smoothedRttNanos - baselineRttNanos) / baselineRttNanos;
        if (delayRatio >= parameters.delayThreshold()) {
            healthyAcknowledgements = 0;
            if (++consecutiveDelayedAcknowledgements >= parameters.persistenceAcks()) {
                consecutiveDelayedAcknowledgements = 0;
                if (delayRatio >= parameters.severeDelayThreshold()) {
                    window = Math.max(parameters.minimumWindow(), window * parameters.decreaseFactor());
                } else {
                    window = Math.max(parameters.minimumWindow(), window - 1.0);
                }
            }
        } else {
            consecutiveDelayedAcknowledgements = 0;
            if (++healthyAcknowledgements >= parameters.increaseEveryAcks()) {
                window = Math.min(parameters.maximumWindow(), window + 1.0);
                healthyAcknowledgements = 0;
            }
        }
    }

    @Override public void onTimeout() {
        window = Math.max(parameters.minimumWindow(), window * parameters.timeoutDecreaseFactor());
        healthyAcknowledgements = 0;
        consecutiveDelayedAcknowledgements = 0;
    }

    public long smoothedRttNanos() { return smoothedRttNanos; }
    public long rttVariationNanos() { return rttVariationNanos; }
    public long baselineRttNanos() { return baselineRttNanos; }
    public long estimatedRateBytesPerSecond() { return estimatedRateBytesPerSecond; }

    /** Minimum RTT over the last {@code baselineWindowAcks} samples, in O(1) amortised time. */
    private long updateBaseline(long rttNanos) {
        long index = sampleIndex++;
        while (!baselineCandidates.isEmpty() && baselineCandidates.peekLast()[1] >= rttNanos) baselineCandidates.pollLast();
        baselineCandidates.addLast(new long[]{index, rttNanos});
        while (baselineCandidates.peekFirst()[0] <= index - parameters.baselineWindowAcks()) baselineCandidates.pollFirst();
        return baselineCandidates.peekFirst()[1];
    }

    private long clampRate(long rate) {
        return Math.max(parameters.minimumRateBytesPerSecond(), Math.min(parameters.maximumRateBytesPerSecond(), rate));
    }

    private static long ewma(long current, long sample, double weight) {
        return Math.round((1.0 - weight) * current + weight * sample);
    }
}
