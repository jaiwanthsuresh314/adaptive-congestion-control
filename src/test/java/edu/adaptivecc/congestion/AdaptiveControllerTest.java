package edu.adaptivecc.congestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveControllerTest {
    @Test void smoothsRttAndEstablishesBaseline() {
        AdaptiveController controller = new AdaptiveController();
        controller.onAcknowledgement(10_000_000, 1_000);
        controller.onAcknowledgement(14_000_000, 1_000);

        assertEquals(10_000_000, controller.baselineRttNanos());
        assertTrue(controller.smoothedRttNanos() > 10_000_000);
        assertTrue(controller.rttVariationNanos() > 0);
        assertTrue(controller.estimatedRateBytesPerSecond() > 0);
    }

    @Test void healthyAcksGrowWithinConfiguredBounds() {
        AdaptiveController.Parameters parameters = new AdaptiveController.Parameters(1, 1, 3, 0.5, 0.5, 0.5, 1.0, 0.5, 2, 1_000, 1_000_000);
        AdaptiveController controller = new AdaptiveController(parameters);
        controller.onAcknowledgement(10_000_000, 1_000);
        controller.onAcknowledgement(10_000_000, 1_000);
        controller.onAcknowledgement(10_000_000, 1_000);
        controller.onAcknowledgement(10_000_000, 1_000);

        assertEquals(3, controller.congestionWindow());
        controller.onAcknowledgement(10_000_000, 1_000);
        assertEquals(3, controller.congestionWindow());
    }

    @Test void growingDelayReducesWindowAndTimeoutBacksOff() {
        AdaptiveController.Parameters parameters = new AdaptiveController.Parameters(8, 1, 16, 1.0, 1.0, 0.2, 0.5, 0.5, 100, 1_000, 1_000_000);
        AdaptiveController controller = new AdaptiveController(parameters);
        controller.onAcknowledgement(10_000_000, 1_000);
        for (int i = 0; i < 5; i++) controller.onAcknowledgement(20_000_000, 1_000);
        int delayedWindow = controller.congestionWindow();
        controller.onTimeout();

        assertTrue(delayedWindow < 8);
        assertTrue(controller.congestionWindow() <= delayedWindow);
        assertTrue(controller.congestionWindow() >= 1);
    }
}
