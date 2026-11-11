package dev.herade.moderationgui.network;

import dev.herade.moderationgui.ModerationGui;
import dev.herade.moderationgui.storage.ActivityLogRepository;
import dev.herade.moderationgui.storage.PermissionRepository;
import dev.herade.moderationgui.storage.PunishmentRepository;
import dev.herade.moderationgui.storage.RankRepository;
import dev.herade.moderationgui.storage.PlayerRepository;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Формирует ограниченные сервером представления данных для вкладок интерфейса. */
public final class ModerationDataService {
    private ModerationDataService() { }

    public static String load(ServerPlayer viewer, String requestedSection) {
        String section = requestedSection == null ? "" : requestedSection.trim();
        String permission = requiredPermission(section);
        if (permission == null) return "Unknown section.";
        try {
            if (!hasAccess(viewer, permission)) {
                return "Access denied. Required permission: " + permission;
            }
            List<String> rows = switch (section) {
                case "Players" -> loadPlayers();
                case "Ranks" -> loadRanks();
                case "Permissions" -> loadPermissions(viewer);
                case "Punishments" -> loadPunishments();
                case "History" -> loadHistory();
                case "Activity Log" -> loadActivity();
                case "Settings" -> List.of(
                        "Storage: SQLite",
                        "Permission checks: server-authoritative",
                        "Temporary punishment time unit: milliseconds",
                        "Configuration editor: not implemented yet"
                );
                default -> List.of();
            };
            return rows.isEmpty() ? "No entries found." : String.join("\n", rows);
        } catch (SQLException | RuntimeException exception) {
            ModerationGui.LOGGER.error("Could not load GUI section {} for {}.",
                    section, viewer.getUUID(), exception);
            return "Could not load this section. Check the server log.";
        }
    }

    private static List<String> loadPlayers() throws SQLException {
        List<String> rows = new ArrayList<>();
        for (PlayerRepository.Player player : ModerationGui.players().searchByName("", 50)) {
            rows.add(player.lastName() + " | " + player.uuid()
                    + " | Last seen: " + player.lastSeenAt());
        }
        return rows;
    }

    private static List<String> loadRanks() throws SQLException {
        List<String> rows = new ArrayList<>();
        for (RankRepository.Rank rank : ModerationGui.ranks().findAll()) {
            rows.add("#" + rank.id() + " " + rank.name()
                    + " | Priority: " + rank.priority()
                    + " | Prefix: " + safe(rank.prefix())
                    + " | Color: " + rank.color()
                    + " | Parent: " + (rank.parentRankId() == null ? "None" : rank.parentRankId()));
        }
        return rows;
    }

    private static List<String> loadPermissions(ServerPlayer viewer) throws SQLException {
        List<String> rows = new ArrayList<>();
        for (RankRepository.Rank rank : ModerationGui.ranks().findAll()) {
            for (PermissionRepository.PermissionRule rule :
                    ModerationGui.permissions().findRankPermissions(rank.id())) {
                rows.add("Rank " + rank.name() + " | "
                        + (rule.allowed() ? "ALLOW " : "DENY ") + rule.node());
            }
        }
        for (PermissionRepository.PermissionRule rule :
                ModerationGui.permissions().findPlayerPermissions(viewer.getUUID().toString())) {
            rows.add("You | " + (rule.allowed() ? "ALLOW " : "DENY ") + rule.node());
        }
        return rows;
    }

    private static List<String> loadPunishments() throws SQLException {
        List<String> rows = new ArrayList<>();
        for (PunishmentRepository.Punishment punishment :
                ModerationGui.punishments().findRecentActive(50, System.currentTimeMillis())) {
            rows.add("#" + punishment.id() + " " + punishment.type()
                    + " | " + punishment.playerName()
                    + " | " + (punishment.active() ? "Active" : "Inactive")
                    + " | " + safe(punishment.reason()));
        }
        return rows;
    }

    private static List<String> loadHistory() throws SQLException {
        List<String> rows = new ArrayList<>();
        for (PunishmentRepository.Punishment punishment :
                ModerationGui.punishments().findRecent(50, System.currentTimeMillis())) {
            rows.add("#" + punishment.id() + " " + punishment.type()
                    + " | " + punishment.playerName()
                    + " | " + (punishment.active() ? "Active" : "Inactive")
                    + " | " + safe(punishment.reason()));
        }
        return rows;
    }

    private static List<String> loadActivity() throws SQLException {
        List<String> rows = new ArrayList<>();
        for (ActivityLogRepository.Entry entry : ModerationGui.activityLog().recent(50)) {
            rows.add(entry.action() + " | " + entry.actorUuid()
                    + " | Target: " + (entry.targetUuid() == null ? "None" : entry.targetUuid())
                    + " | " + safe(entry.details()));
        }
        return rows;
    }

    private static String requiredPermission(String section) {
        return switch (section) {
            case "Players" -> "moderation.players.view";
            case "Ranks" -> "moderation.ranks.view";
            case "Permissions" -> "moderation.permissions.view";
            case "Punishments" -> "moderation.punishments.view";
            case "History" -> "moderation.history.view";
            case "Settings" -> "moderation.settings.view";
            case "Activity Log" -> "moderation.activity.view";
            default -> null;
        };
    }

    private static boolean hasAccess(ServerPlayer player, String permission) throws SQLException {
        if (player.createCommandSourceStack().hasPermission(2)) return true;
        return ModerationGui.permissions().hasPermission(player.getUUID().toString(), permission);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
    }
}
