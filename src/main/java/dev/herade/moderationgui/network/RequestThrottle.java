package dev.herade.moderationgui.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ограничитель частоты, который учитывает только принятые запросы и использует монотонное время. */
public final class RequestThrottle {
    private final long intervalNanos;
    private final Map<UUID, Long> lastAcceptedAt = new HashMap<>();

    public RequestThrottle(long intervalNanos) {
        if (intervalNanos < 0) {
            throw new IllegalArgumentException("intervalNanos cannot be negative");
        }
        this.intervalNanos = intervalNanos;
    }

    public synchronized boolean tryAcquire(UUID playerId, long nowNanos) {
        if (playerId == null) throw new IllegalArgumentException("playerId cannot be null");
        Long previous = lastAcceptedAt.get(playerId);
        if (previous != null && nowNanos - previous < intervalNanos) {
            return false;
        }
        lastAcceptedAt.put(playerId, nowNanos);
        return true;
    }

    public synchronized void remove(UUID playerId) {
        if (playerId != null) lastAcceptedAt.remove(playerId);
    }

    synchronized int trackedPlayers() {
        return lastAcceptedAt.size();
    }
}
