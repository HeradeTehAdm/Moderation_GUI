package dev.herade.moderationgui.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Обрабатывает клиентские запросы, не доверяя переданным клиентом правам доступа. */
public final class ModerationServerNetworking {
    private ModerationServerNetworking() { }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(RequestSectionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String section = payload.section();
            String content = section == null || section.length() > 32
                    ? "Invalid section request."
                    : ModerationDataService.load(player, section);
            ServerPlayNetworking.send(player, new SectionDataPayload(
                    section == null ? "" : section, content));
        });
    }
}
