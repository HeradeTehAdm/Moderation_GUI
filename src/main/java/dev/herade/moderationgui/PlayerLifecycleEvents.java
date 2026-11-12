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
                PlayerRepository repository = ModerationGui.players();
                repository.recordJoin(player.getUUID().toString(),
                        player.getName().getString(), System.currentTimeMillis());

                if (ModerationGui.settings().enabled("enforce_bans")) {
                    ModerationGui.punishments()
                        .findActiveForPlayer(player.getUUID().toString(), System.currentTimeMillis())
                        .stream()
                        .filter(punishment -> punishment.type()
                                == dev.herade.moderationgui.storage.PunishmentRepository.Type.BAN)
                        .findFirst()
                        .ifPresent(punishment -> player.connection.disconnect(
                                net.minecraft.network.chat.Component.literal(
                                        "You are banned from this server. Reason: " + punishment.reason())));
                }
            } catch (Exception exception) {
                ModerationGui.LOGGER.error("Could not save player join profile for {}.",
                        player.getUUID(), exception);
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
