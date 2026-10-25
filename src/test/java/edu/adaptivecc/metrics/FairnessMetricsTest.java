package edu.adaptivecc.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FairnessMetricsTest {
    @Test void equalRatesArePerfectlyFair() {
        assertEquals(1.0, FairnessMetrics.jainsIndex(10.0, 10.0, 10.0), 0.0001);
    }

    @Test void unequalRatesHaveLowerFairness() {
        assertEquals(0.9, FairnessMetrics.jainsIndex(1.0, 2.0), 0.0001);
    }

    @Test void rejectsInvalidRates() {
        assertThrows(IllegalArgumentException.class, () -> FairnessMetrics.jainsIndex(-1.0, 1.0));
    }
}
