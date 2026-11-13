package dev.herade.moderationgui;

import dev.herade.moderationgui.storage.PlayerRepository;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/** Синхронизирует профили игроков с событиями подключения к серверу. */
public final class PlayerLifecycleEvents {
    private PlayerLifecycleEvents() { }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            ServerPlayer player = listener.getPlayer();
            try {
                ModerationGui.players().recordJoin(player.getUUID().toString(),
                        player.getName().getString(), System.currentTimeMillis());
            } catch (Exception exception) {
                ModerationGui.LOGGER.error("Could not save player join profile for {}.",
                        player.getUUID(), exception);
            }

            // Ошибка обновления профиля не должна пропускать проверку активного BAN.
            try {
                if (ModerationGui.settings().enabled("enforce_bans")) {
                    dev.herade.moderationgui.service.BanEnforcementService.findActiveBan(
                            ModerationGui.punishments(), player.getUUID().toString(),
                            System.currentTimeMillis()).ifPresent(punishment ->
                            player.connection.disconnect(net.minecraft.network.chat.Component.literal(
                                    "You are banned from this server. Reason: " + punishment.reason())));
                }
            } catch (Exception exception) {
                ModerationGui.LOGGER.error("Could not enforce active bans for {}. Denying join because the ban status could not be verified.",
                        player.getUUID(), exception);
                player.connection.disconnect(net.minecraft.network.chat.Component.literal(
                        "Moderation checks are temporarily unavailable. Please reconnect later."));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
            ServerPlayer player = listener.getPlayer();
            try {
                ModerationGui.players().updateLastSeen(
                        player.getUUID().toString(), System.currentTimeMillis());
            } catch (Exception exception) {
                ModerationGui.LOGGER.error("Could not update last-seen timestamp for {}.",
                        player.getUUID(), exception);
            }
        });
    }
}
