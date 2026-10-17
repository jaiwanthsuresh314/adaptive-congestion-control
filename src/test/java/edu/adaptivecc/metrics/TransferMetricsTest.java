package edu.adaptivecc.metrics;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferMetricsTest {
    @Test void calculatesRatesAndRttSummaries() {
        TransferMetrics metrics = new TransferMetrics(4, 4, 5, 400, 2_000_000_000L, List.of(1_000_000L, 3_000_000L));
        assertEquals(1, metrics.retransmissions());
        assertEquals(0.25, metrics.retransmissionRate());
        assertEquals(2.0, metrics.averageRttMillis(), 0.001);
        assertEquals(1.0, metrics.rttVariationMillis(), 0.001);
        assertEquals(200.0, metrics.goodputBytesPerSecond(), 0.001);
    }
}
