package dev.herade.moderationgui.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RequestThrottleTest {
    private static final long INTERVAL = 250_000_000L;
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void rejectedAttemptsDoNotExtendTheCooldown() {
        RequestThrottle throttle = new RequestThrottle(INTERVAL);

        assertTrue(throttle.tryAcquire(PLAYER, 1_000_000_000L));
        assertFalse(throttle.tryAcquire(PLAYER, 1_100_000_000L));
        assertFalse(throttle.tryAcquire(PLAYER, 1_200_000_000L));
        assertTrue(throttle.tryAcquire(PLAYER, 1_250_000_000L));
    }

    @Test
    void throttlingIsIndependentPerPlayer() {
        RequestThrottle throttle = new RequestThrottle(INTERVAL);

        assertTrue(throttle.tryAcquire(PLAYER, 10));
        assertTrue(throttle.tryAcquire(OTHER, 10));
        assertFalse(throttle.tryAcquire(PLAYER, 20));
        assertTrue(throttle.tryAcquire(OTHER, INTERVAL + 10));
    }

    @Test
    void disconnectCleanupAllowsImmediateNewSessionRequest() {
        RequestThrottle throttle = new RequestThrottle(INTERVAL);
        assertTrue(throttle.tryAcquire(PLAYER, 10));
        assertFalse(throttle.tryAcquire(PLAYER, 20));

        throttle.remove(PLAYER);

        assertTrue(throttle.tryAcquire(PLAYER, 20));
        assertEquals(1, throttle.trackedPlayers());
    }

    @Test
    void rejectsInvalidIntervalsAndNullPlayerIds() {
        assertThrows(IllegalArgumentException.class, () -> new RequestThrottle(-1));
        RequestThrottle throttle = new RequestThrottle(1);
        assertThrows(IllegalArgumentException.class, () -> throttle.tryAcquire(null, 0));
    }
}
