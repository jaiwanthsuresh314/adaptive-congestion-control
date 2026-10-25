package edu.adaptivecc.metrics;

/** Common metrics for comparing multiple flow rates. */
public final class FairnessMetrics {
    private FairnessMetrics() { }

    /** Returns Jain's fairness index for non-negative flow rates. */
    public static double jainsIndex(double... rates) {
        if (rates == null || rates.length == 0) return 0.0;
        double sum = 0.0;
        double sumSquares = 0.0;
        for (double rate : rates) {
            if (!Double.isFinite(rate) || rate < 0.0) {
                throw new IllegalArgumentException("rates must be finite and non-negative");
            }
            sum += rate;
            sumSquares += rate * rate;
        }
        return sumSquares == 0.0 ? 0.0 : (sum * sum) / (rates.length * sumSquares);
    }
}
