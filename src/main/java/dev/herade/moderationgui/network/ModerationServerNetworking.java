package dev.herade.moderationgui.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Обрабатывает клиентские запросы с ограничением частоты и размера ответа. */
public final class ModerationServerNetworking {
    private static final long REQUEST_INTERVAL_MS = 250;
    private static final int MAX_RESPONSE_BYTES = 24 * 1024;
    private static final Map<UUID, Long> LAST_REQUEST_AT = new HashMap<>();

    private ModerationServerNetworking() { }

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LAST_REQUEST_AT.remove(handler.getPlayer().getUUID()));

        ServerPlayNetworking.registerGlobalReceiver(RequestSectionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            long now = System.currentTimeMillis();
            Long previous = LAST_REQUEST_AT.put(player.getUUID(), now);
            if (previous != null && now - previous < REQUEST_INTERVAL_MS) {
                return;
            }

            String section = payload.section();
            String filter = payload.filter();
            String content = section == null || section.length() > 32
                    || filter == null || filter.length() > 64
                    ? "Invalid section request."
                    : ModerationDataService.load(player, section, filter);
            send(player, section, limitUtf8(content, MAX_RESPONSE_BYTES));
        });
    }

    private static void send(ServerPlayer player, String section, String content) {
        ServerPlayNetworking.send(player, new SectionDataPayload(
                section == null ? "" : section, content));
    }

    static String limitUtf8(String value, int maxBytes) {
        if (value == null) return "";
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) return value;

        StringBuilder result = new StringBuilder();
        int usedBytes = 0;
        int offset = 0;
        while (offset < value.length()) {
            int codePoint = value.codePointAt(offset);
            int charCount = Character.charCount(codePoint);
            int codePointBytes = new String(Character.toChars(codePoint))
                    .getBytes(StandardCharsets.UTF_8).length;
            if (usedBytes + codePointBytes > maxBytes - 32) break;
            result.appendCodePoint(codePoint);
            usedBytes += codePointBytes;
            offset += charCount;
        }
        return result.append("\n... output truncated").toString();
    }
}
