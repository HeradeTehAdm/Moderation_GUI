package dev.herade.moderationgui.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.herade.moderationgui.ModerationGui;
import dev.herade.moderationgui.storage.PlayerRepository;
import dev.herade.moderationgui.storage.PunishmentRepository;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** Команды мода. Используется отдельный корень, чтобы не конфликтовать с vanilla-командами. */
public final class ModerationCommands {
    private static final String CONSOLE_ACTOR = "00000000-0000-0000-0000-000000000000";

    private ModerationCommands() { }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
                registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("moderationgui")
                .executes(context -> showHelp(context.getSource()))
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource())))
                .then(Commands.literal("profile")
                        .requires(source -> canUse(source, "moderation.players.view"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> showProfile(context.getSource(),
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("history")
                        .requires(source -> canUse(source, "moderation.history.view"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> showHistory(context.getSource(),
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("rank")
                        .then(Commands.literal("list")
                                .requires(source -> canUse(source, "moderation.ranks.view"))
                                .executes(context -> listRanks(context.getSource())))
                        .then(Commands.literal("create")
                                .requires(source -> canUse(source, "moderation.ranks.create"))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("priority", IntegerArgumentType.integer())
                                                .then(Commands.argument("prefix", StringArgumentType.word())
                                                        .then(Commands.argument("color", StringArgumentType.word())
                                                                .executes(context -> createRank(context.getSource(),
                                                                        StringArgumentType.getString(context, "name"),
                                                                        IntegerArgumentType.getInteger(context, "priority"),
                                                                        StringArgumentType.getString(context, "prefix"),
                                                                        StringArgumentType.getString(context, "color"))))))))
                        .then(Commands.literal("delete")
                                .requires(source -> canUse(source, "moderation.ranks.delete"))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> deleteRank(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("assign")
                                .requires(source -> canUse(source, "moderation.ranks.assign"))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("rank", StringArgumentType.word())
                                                .executes(context -> assignRank(context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "rank"))))))
                        .then(Commands.literal("unassign")
                                .requires(source -> canUse(source, "moderation.ranks.assign"))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("rank", StringArgumentType.word())
                                                .executes(context -> unassignRank(context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "rank"))))))
                )
                .then(Commands.literal("warn")
                        .requires(source -> canUse(source, "moderation.warn"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> issue(context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                PunishmentRepository.Type.WARN,
                                                StringArgumentType.getString(context, "reason"))))))
                .then(Commands.literal("mute")
                        .requires(source -> canUse(source, "moderation.mute"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> issue(context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                PunishmentRepository.Type.MUTE,
                                                StringArgumentType.getString(context, "reason"))))))
                .then(Commands.literal("ban")
                        .requires(source -> canUse(source, "moderation.ban"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> issue(context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                PunishmentRepository.Type.BAN,
                                                StringArgumentType.getString(context, "reason"))))))
                .then(Commands.literal("tempban")
                        .requires(source -> canUse(source, "moderation.tempban"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("seconds", LongArgumentType.longArg(1, 31536000))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(context -> issueTemporary(context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        PunishmentRepository.Type.BAN,
                                                        LongArgumentType.getLong(context, "seconds"),
                                                        StringArgumentType.getString(context, "reason")))))))
                .then(Commands.literal("tempmute")
                        .requires(source -> canUse(source, "moderation.tempmute"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("seconds", LongArgumentType.longArg(1, 31536000))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(context -> issueTemporary(context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        PunishmentRepository.Type.MUTE,
                                                        LongArgumentType.getLong(context, "seconds"),
                                                        StringArgumentType.getString(context, "reason")))))))
                .then(Commands.literal("unpunish")
                        .requires(source -> canUse(source, "moderation.punishments.revoke"))
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(context -> revoke(context.getSource(),
                                        LongArgumentType.getLong(context, "id"))))));
    }


    private static int listRanks(CommandSourceStack source) {
        try {
            var ranks = ModerationGui.ranks().findAll();
            source.sendSuccess(() -> Component.literal("Ranks (" + ranks.size() + "):"), false);
            ranks.forEach(rank -> source.sendSuccess(() -> Component.literal(
                    "#" + rank.id() + " " + rank.name() + " | priority=" + rank.priority()
                            + " | prefix=" + (rank.prefix().isBlank() ? "None" : rank.prefix())
                            + " | color=" + rank.color()
                            + " | parent=" + (rank.parentRankId() == null ? "None" : rank.parentRankId())), false));
            return ranks.size();
        } catch (SQLException exception) {
            return reportFailure(source, "Could not load ranks.", exception);
        }
    }

    private static int createRank(CommandSourceStack source, String name, int priority,
                                  String prefix, String color) {
        try {
            long id = ModerationGui.rankService().create(name,
                    prefix.equals("-") ? "" : prefix, color, priority,
                    actorUuid(source), System.currentTimeMillis());
            source.sendSuccess(() -> Component.literal("Rank " + name + " created. ID: #" + id), true);
            return 1;
        } catch (SQLException | IllegalArgumentException exception) {
            return reportFailure(source, "Could not create rank.", exception);
        }
    }

    private static int deleteRank(CommandSourceStack source, String name) {
        try {
            var rank = ModerationGui.ranks().findByName(name);
            if (rank.isEmpty()) {
                source.sendFailure(Component.literal("Rank not found: " + name));
                return 0;
            }
            boolean deleted = ModerationGui.rankService().delete(
                    rank.get().id(), actorUuid(source), System.currentTimeMillis());
            if (!deleted) {
                source.sendFailure(Component.literal("Rank could not be deleted: " + name));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Rank " + name + " deleted."), true);
            return 1;
        } catch (SQLException exception) {
            return reportFailure(source, "Could not delete rank.", exception);
        }
    }

    private static int assignRank(CommandSourceStack source, String playerName, String rankName) {
        try {
            PlayerRepository.Player player = findPlayer(playerName).orElse(null);
            var rank = ModerationGui.ranks().findByName(rankName);
            if (player == null || rank.isEmpty()) {
                source.sendFailure(Component.literal("Player profile or rank not found."));
                return 0;
            }
            ModerationGui.rankService().assign(player.uuid(), rank.get().id(),
                    actorUuid(source), System.currentTimeMillis());
            source.sendSuccess(() -> Component.literal(
                    "Rank " + rank.get().name() + " assigned to " + player.lastName() + "."), true);
            return 1;
        } catch (SQLException | IllegalArgumentException exception) {
            return reportFailure(source, "Could not assign rank.", exception);
        }
    }

    private static int unassignRank(CommandSourceStack source, String playerName, String rankName) {
        try {
            PlayerRepository.Player player = findPlayer(playerName).orElse(null);
            var rank = ModerationGui.ranks().findByName(rankName);
            if (player == null || rank.isEmpty()) {
                source.sendFailure(Component.literal("Player profile or rank not found."));
                return 0;
            }
            boolean removed = ModerationGui.rankService().unassign(player.uuid(), rank.get().id(),
                    actorUuid(source), System.currentTimeMillis());
            if (!removed) {
                source.sendFailure(Component.literal("That rank is not assigned to this player."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(
                    "Rank " + rank.get().name() + " removed from " + player.lastName() + "."), true);
            return 1;
        } catch (SQLException | IllegalArgumentException exception) {
            return reportFailure(source, "Could not unassign rank.", exception);
        }
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Moderation GUI: /moderationgui profile <player>, history <player>, "
                        + "rank list|create|delete|assign|unassign, "
                        + "warn <player> <reason>, mute <player> <reason>, "
                        + "ban <player> <reason>, tempban <player> <seconds> <reason>, "
                        + "tempmute <player> <seconds> <reason>, unpunish <id>"), false);
        return 1;
    }

    private static int showProfile(CommandSourceStack source, String name) {
        try {
            PlayerRepository.Player player = findPlayer(name).orElse(null);
            if (player == null) {
                source.sendFailure(Component.literal("Player profile not found: " + name));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(
                    "Player: " + player.lastName()
                            + " | UUID: " + player.uuid()
                            + " | First joined: " + player.firstJoinedAt()
                            + " | Last seen: " + player.lastSeenAt()
                            + " | Notes: " + (player.notes().isBlank() ? "None" : player.notes())), false);
            return 1;
        } catch (SQLException exception) {
            return reportFailure(source, "Could not load player profile.", exception);
        }
    }

    private static int showHistory(CommandSourceStack source, String name) {
        try {
            PlayerRepository.Player player = findPlayer(name).orElse(null);
            if (player == null) {
                source.sendFailure(Component.literal("Player profile not found: " + name));
                return 0;
            }
            var history = ModerationGui.punishments().findHistoryForPlayer(player.uuid(), 20);
            source.sendSuccess(() -> Component.literal(
                    "Punishment history for " + player.lastName() + " (" + history.size() + " records):"), false);
            history.forEach(entry -> source.sendSuccess(() -> Component.literal(
                    "#" + entry.id() + " " + entry.type() + " | " + entry.reason()
                            + " | " + (entry.expiresAt() == null ? "Permanent" : "Expires: " + entry.expiresAt())
                            + " | " + (entry.active() ? "Active" : "Inactive")), false));
            return history.size();
        } catch (SQLException exception) {
            return reportFailure(source, "Could not load punishment history.", exception);
        }
    }

    private static int issue(CommandSourceStack source, String targetName,
                             PunishmentRepository.Type type, String reason) {
        try {
            PlayerRepository.Player target = findPlayer(targetName).orElse(null);
            if (target == null) {
                source.sendFailure(Component.literal(
                        "Player profile not found. The player must have joined this server at least once."));
                return 0;
            }
            long now = System.currentTimeMillis();
            long id = ModerationGui.moderation().issuePunishment(target.uuid(), target.lastName(),
                    type, reason, actorUuid(source), now, null);

            if (type == PunishmentRepository.Type.BAN) {
                ServerPlayer online = source.getServer().getPlayerList().getPlayer(UUID.fromString(target.uuid()));
                if (online != null) {
                    online.connection.disconnect(Component.literal(
                            "You are banned from this server. Reason: " + reason));
                }
            }

            source.sendSuccess(() -> Component.literal(
                    type + " issued to " + target.lastName() + ". Punishment ID: #" + id), true);
            return 1;
        } catch (SQLException | IllegalArgumentException exception) {
            return reportFailure(source, "Could not issue punishment.", exception);
        }
    }


    private static int issueTemporary(CommandSourceStack source, String targetName,
                                      PunishmentRepository.Type type, long durationSeconds, String reason) {
        try {
            PlayerRepository.Player target = findPlayer(targetName).orElse(null);
            if (target == null) {
                source.sendFailure(Component.literal(
                        "Player profile not found. The player must have joined this server at least once."));
                return 0;
            }
            long now = System.currentTimeMillis();
            long expiresAt = Math.addExact(now, Math.multiplyExact(durationSeconds, 1000L));
            long id = ModerationGui.moderation().issuePunishment(target.uuid(), target.lastName(),
                    type, reason, actorUuid(source), now, expiresAt);

            if (type == PunishmentRepository.Type.BAN) {
                ServerPlayer online = source.getServer().getPlayerList().getPlayer(UUID.fromString(target.uuid()));
                if (online != null) {
                    online.connection.disconnect(Component.literal(
                            "You are temporarily banned from this server. Reason: " + reason));
                }
            }

            source.sendSuccess(() -> Component.literal(
                    type + " issued to " + target.lastName() + " for " + durationSeconds
                            + " seconds. Punishment ID: #" + id), true);
            return 1;
        } catch (SQLException | IllegalArgumentException | ArithmeticException exception) {
            return reportFailure(source, "Could not issue temporary punishment.", exception);
        }
    }

    private static int revoke(CommandSourceStack source, long punishmentId) {
        try {
            Optional<PunishmentRepository.Punishment> existing =
                    ModerationGui.punishments().findById(punishmentId);
            if (existing.isEmpty()) {
                source.sendFailure(Component.literal("Punishment not found: #" + punishmentId));
                return 0;
            }
            boolean revoked = ModerationGui.moderation().revokePunishment(
                    punishmentId, actorUuid(source), System.currentTimeMillis());
            if (!revoked) {
                source.sendFailure(Component.literal("Punishment is already inactive: #" + punishmentId));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(
                    "Punishment #" + punishmentId + " has been revoked."), true);
            return 1;
        } catch (SQLException exception) {
            return reportFailure(source, "Could not revoke punishment.", exception);
        }
    }

    private static Optional<PlayerRepository.Player> findPlayer(String name) throws SQLException {
        return ModerationGui.players().searchByName(name, 100).stream()
                .filter(player -> player.lastName().equalsIgnoreCase(name))
                .findFirst();
    }

    private static String actorUuid(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player.getUUID().toString();
        }
        return CONSOLE_ACTOR;
    }

    private static boolean canUse(CommandSourceStack source, String permission) {
        if (source.hasPermission(2)) return true;
        if (!(source.getEntity() instanceof ServerPlayer player)) return true;
        try {
            return ModerationGui.permissions().hasPermission(player.getUUID().toString(), permission);
        } catch (SQLException exception) {
            ModerationGui.LOGGER.error("Could not check permission {} for {}.",
                    permission, player.getUUID(), exception);
            return false;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private static int reportFailure(CommandSourceStack source, String message, Exception exception) {
        ModerationGui.LOGGER.error(message, exception);
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
