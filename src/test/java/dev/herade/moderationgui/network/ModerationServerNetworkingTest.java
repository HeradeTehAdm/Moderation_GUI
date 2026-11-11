package dev.herade.moderationgui.network;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ModerationServerNetworkingTest {
    @Test
    void responseLimitPreservesUtf8AndDoesNotExceedByteBudget() {
        String source = "Moderation GUI — игрок 🛡️ ".repeat(4000);
        int limit = 4096;

        String result = ModerationServerNetworking.limitUtf8(source, limit);

        assertTrue(result.endsWith("... output truncated"));
        assertTrue(result.getBytes(StandardCharsets.UTF_8).length <= limit);
        assertDoesNotThrow(() -> result.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shortResponseIsNotModified() {
        String source = "Players: 12";

        assertEquals(source, ModerationServerNetworking.limitUtf8(source, 128));
    }
}
