package dev.herade.moderationgui;

import dev.herade.moderationgui.storage.PunishmentRepository;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;

/** Применяет сохранённые наказания к серверным игровым событиям. */
public final class ModerationEnforcementEvents {
    private static final Component MUTED_MESSAGE =
            Component.literal("You are muted and cannot send public messages.");

    private ModerationEnforcementEvents() { }

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, boundChatType) ->
                allowPublicChat(sender));

        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register((message, source, boundChatType) -> {
            if (!(source.getEntity() instanceof ServerPlayer sender)) {
                return true;
            }
            return allowPublicChat(sender);
        });
    }

    private static boolean allowPublicChat(ServerPlayer player) {
        try {
            boolean muted = ModerationGui.punishments()
                    .findActiveForPlayer(player.getUUID().toString(), System.currentTimeMillis())
                    .stream()
                    .anyMatch(punishment -> punishment.type() == PunishmentRepository.Type.MUTE);
            if (muted) {
                player.sendSystemMessage(MUTED_MESSAGE);
                return false;
            }
            return true;
        } catch (SQLException exception) {
            ModerationGui.LOGGER.error("Could not check active mute for {}.",
                    player.getUUID(), exception);
            player.sendSystemMessage(Component.literal(
                    "Chat moderation is temporarily unavailable. Please try again later."));
            return false;
        }
    }
}
