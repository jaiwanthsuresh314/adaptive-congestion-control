package edu.adaptivecc.congestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    // ---- Helpers for the expanded tests ----
    // rttAlpha = 1 makes the smoothed RTT equal the latest sample, so every step is exact.
    private static final long MS = 1_000_000L;

    private static AdaptiveController.Parameters exact(double timeoutFactor, int persistenceAcks, int baselineWindowAcks) {
        return new AdaptiveController.Parameters(8, 1, 16, 1.0, 1.0, 0.2, 0.5, 0.5, 100, 1_000, 1_000_000,
                timeoutFactor, persistenceAcks, baselineWindowAcks);
    }

    // ---- Parameters and compatibility ----

    @Test void defaultsUseSeparateFactorsPersistenceAndRecentBaseline() {
        AdaptiveController.Parameters defaults = AdaptiveController.Parameters.defaults();
        assertEquals(0.7, defaults.decreaseFactor());
        assertEquals(0.5, defaults.timeoutDecreaseFactor());
        assertEquals(3, defaults.persistenceAcks());
        assertEquals(500, defaults.baselineWindowAcks());
        assertEquals(10_000, defaults.minimumRateBytesPerSecond());
        assertEquals(10_000_000, defaults.maximumRateBytesPerSecond());
    }

    @Test void legacyConstructorKeepsOriginalBehaviour() {
        AdaptiveController.Parameters legacy = new AdaptiveController.Parameters(2, 1, 64, 0.125, 0.25, 0.25, 0.75, 0.7, 4, 10_000, 10_000_000);
        assertEquals(0.7, legacy.timeoutDecreaseFactor());
        assertEquals(1, legacy.persistenceAcks());
        assertEquals(Integer.MAX_VALUE, legacy.baselineWindowAcks());
    }

    @Test void rejectsInvalidNewParameters() {
        assertThrows(IllegalArgumentException.class, () -> exact(0.0, 3, 500));
        assertThrows(IllegalArgumentException.class, () -> exact(1.0, 3, 500));
        assertThrows(IllegalArgumentException.class, () -> exact(0.5, 0, 500));
        assertThrows(IllegalArgumentException.class, () -> exact(0.5, 3, 0));
    }

    @Test void rejectsNonPositiveFeedback() {
        AdaptiveController controller = new AdaptiveController();
        assertThrows(IllegalArgumentException.class, () -> controller.onAcknowledgement(0, 1_000));
        assertThrows(IllegalArgumentException.class, () -> controller.onAcknowledgement(10 * MS, 0));
        assertThrows(IllegalArgumentException.class, () -> controller.pacingDelayNanos(-1));
    }

    // ---- Recent-window RTT baseline ----

    @Test void baselineTracksLowerSamples() {
        AdaptiveController controller = new AdaptiveController(exact(0.5, 3, 500));
        controller.onAcknowledgement(30 * MS, 1_000);
        controller.onAcknowledgement(10 * MS, 1_000);
        assertEquals(10 * MS, controller.baselineRttNanos());
    }

    @Test void baselineExpiresOutsideRecentWindow() {
        AdaptiveController controller = new AdaptiveController(exact(0.5, 3, 5));
        controller.onAcknowledgement(10 * MS, 1_000);
        controller.onAcknowledgement(30 * MS, 1_000);
        assertEquals(10 * MS, controller.baselineRttNanos());
        for (int i = 0; i < 4; i++) controller.onAcknowledgement(30 * MS, 1_000);
        assertEquals(30 * MS, controller.baselineRttNanos());
    }

    @Test void legacyBaselineIsPermanentMinimum() {
        AdaptiveController controller = new AdaptiveController(new AdaptiveController.Parameters(
                8, 1, 16, 1.0, 1.0, 0.2, 0.5, 0.5, 100, 1_000, 1_000_000));
        controller.onAcknowledgement(10 * MS, 1_000);
        for (int i = 0; i < 600; i++) controller.onAcknowledgement(30 * MS, 1_000);
        assertEquals(10 * MS, controller.baselineRttNanos());
    }

    // ---- Persistent delay detection ----

    @Test void severeDelayCutsOnlyAfterPersistence() {
        AdaptiveController controller = new AdaptiveController(exact(0.5, 3, 500));
        controller.onAcknowledgement(10 * MS, 1_000);
        controller.onAcknowledgement(30 * MS, 1_000);
        controller.onAcknowledgement(30 * MS, 1_000);
        assertEquals(8, controller.congestionWindow());
        controller.onAcknowledgement(30 * MS, 1_000);
        assertEquals(4, controller.congestionWindow());
    }

    @Test void moderateDelayRemovesOnePacketAfterPersistence() {
        AdaptiveController controller = new AdaptiveController(exact(0.5, 3, 500));
        controller.onAcknowledgement(10 * MS, 1_000);
        for (int i = 0; i < 3; i++) controller.onAcknowledgement(13 * MS, 1_000);
        assertEquals(7, controller.congestionWindow());
    }

    @Test void healthyAckResetsPersistenceCount() {
        AdaptiveController controller = new AdaptiveController(exact(0.5, 3, 500));
        controller.onAcknowledgement(10 * MS, 1_000);
        controller.onAcknowledgement(30 * MS, 1_000);
        controller.onAcknowledgement(30 * MS, 1_000);
        controller.onAcknowledgement(10 * MS, 1_000);
        controller.onAcknowledgement(30 * MS, 1_000);
        controller.onAcknowledgement(30 * MS, 1_000);
        assertEquals(8, controller.congestionWindow());
    }

    @Test void persistenceCountRestartsAfterEachCut() {
        AdaptiveController controller = new AdaptiveController(exact(0.5, 3, 500));
        controller.onAcknowledgement(10 * MS, 1_000);
        for (int i = 0; i < 3; i++) controller.onAcknowledgement(30 * MS, 1_000);
        assertEquals(4, controller.congestionWindow());
        controller.onAcknowledgement(30 * MS, 1_000);
        controller.onAcknowledgement(30 * MS, 1_000);
        assertEquals(4, controller.congestionWindow());
        controller.onAcknowledgement(30 * MS, 1_000);
        assertEquals(2, controller.congestionWindow());
    }

    // ---- Separate decrease factors ----

    @Test void timeoutUsesItsOwnDecreaseFactor() {
        AdaptiveController controller = new AdaptiveController(exact(0.75, 3, 500));
        controller.onTimeout();
        assertEquals(6, controller.congestionWindow());
    }

    @Test void delayCutDoesNotUseTimeoutFactor() {
        AdaptiveController controller = new AdaptiveController(exact(0.75, 1, 500));
        controller.onAcknowledgement(10 * MS, 1_000);
        controller.onAcknowledgement(30 * MS, 1_000);
        assertEquals(4, controller.congestionWindow());
    }

    @Test void windowStaysWithinBounds() {
        AdaptiveController shrinking = new AdaptiveController(exact(0.5, 3, 500));
        for (int i = 0; i < 10; i++) shrinking.onTimeout();
        assertEquals(1, shrinking.congestionWindow());

        AdaptiveController growing = new AdaptiveController(new AdaptiveController.Parameters(
                8, 1, 16, 1.0, 1.0, 0.2, 0.5, 0.5, 1, 1_000, 1_000_000, 0.5, 3, 500));
        for (int i = 0; i < 100; i++) growing.onAcknowledgement(10 * MS, 1_000);
        assertEquals(16, growing.congestionWindow());
    }

    // ---- Delivery-rate estimation and pacing ----

    @Test void deliveryRateFollowsPayloadOverRtt() {
        AdaptiveController controller = new AdaptiveController(exact(0.5, 3, 500));
        controller.onAcknowledgement(10 * MS, 1_000);
        assertEquals(100_000, controller.estimatedRateBytesPerSecond());
    }

    @Test void deliveryRateIsClampedToMaximum() {
        AdaptiveController controller = new AdaptiveController(exact(0.5, 3, 500));
        controller.onAcknowledgement(100_000, 1_000);
        assertEquals(1_000_000, controller.estimatedRateBytesPerSecond());
    }

    @Test void deliveryRateIsClampedToMinimum() {
        AdaptiveController controller = new AdaptiveController(exact(0.5, 3, 500));
        controller.onAcknowledgement(10_000 * MS, 1_000);
        assertEquals(1_000, controller.estimatedRateBytesPerSecond());
    }

    @Test void pacingUsesMinimumRateBeforeFirstAck() {
        assertEquals(25_600_000, new AdaptiveController().pacingDelayNanos(256));
    }

    @Test void pacingUsesMeasuredDeliveryRate() {
        AdaptiveController controller = new AdaptiveController(exact(0.5, 3, 500));
        controller.onAcknowledgement(10 * MS, 1_000);
        assertEquals(10 * MS, controller.pacingDelayNanos(1_000));
    }
}
