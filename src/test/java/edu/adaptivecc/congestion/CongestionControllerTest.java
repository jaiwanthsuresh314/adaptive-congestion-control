package edu.adaptivecc.congestion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CongestionControllerTest {
    @Test void fixedRateDoesNotReactToFeedback() {
        FixedRateController controller = new FixedRateController(4, 1_000_000);
        controller.onAcknowledgement(10, 100);
        controller.onTimeout();
        assertEquals(4, controller.congestionWindow());
        assertEquals(100_000, controller.pacingDelayNanos(100));
    }

    @Test void aimdIncreasesAdditivelyAndDecreasesMultiplicatively() {
        AimdController controller = new AimdController(2, 0.5, 1, 10);
        controller.onAcknowledgement(1_000_000, 100);
        assertEquals(2, controller.congestionWindow());
        controller.onAcknowledgement(1_000_000, 100);
        assertEquals(2, controller.congestionWindow());
        controller.onAcknowledgement(1_000_000, 100);
        assertEquals(3, controller.congestionWindow());
        controller.onTimeout();
        assertEquals(1, controller.congestionWindow());
        assertTrue(controller.congestionWindow() >= 1);
    }
}
