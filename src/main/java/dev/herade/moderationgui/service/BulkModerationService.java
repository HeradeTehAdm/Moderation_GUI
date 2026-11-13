package dev.herade.moderationgui.service;

import dev.herade.moderationgui.storage.ActivityLogRepository;
import dev.herade.moderationgui.storage.Database;
import dev.herade.moderationgui.storage.PlayerRepository;
import dev.herade.moderationgui.storage.PunishmentRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Атомарно применяет одно наказание к ограниченной группе офлайн- и онлайн-профилей. */
public final class BulkModerationService {
    public static final int MAX_TARGETS = 25;

    private final Database database;
    private final PlayerRepository players;
    private final PunishmentRepository punishments;
    private final ActivityLogRepository activityLog;

    public BulkModerationService(Database database, PlayerRepository players,
                                 PunishmentRepository punishments, ActivityLogRepository activityLog) {
        this.database = database;
        this.players = players;
        this.punishments = punishments;
        this.activityLog = activityLog;
    }

    public List<AppliedPunishment> issue(List<String> names, PunishmentRepository.Type type,
                                         String reason, String actorUuid, long createdAt,
                                         Long expiresAt) throws SQLException {
        if (names == null || names.isEmpty() || names.size() > MAX_TARGETS) {
            throw new IllegalArgumentException("Bulk moderation requires 1 to 25 targets");
        }
        if (type == null) throw new IllegalArgumentException("Punishment type cannot be null");
        if (reason == null || reason.isBlank() || reason.trim().length() > 512) {
            throw new IllegalArgumentException("Reason must contain 1-512 characters");
        }
        if (actorUuid == null || actorUuid.isBlank() || actorUuid.length() > 36) {
            throw new IllegalArgumentException("Invalid moderator UUID");
        }
        if (expiresAt != null && expiresAt <= createdAt) {
            throw new IllegalArgumentException("Punishment expiry must be later than creation");
        }
        if (type == PunishmentRepository.Type.WARN && expiresAt != null) {
            throw new IllegalArgumentException("WARN cannot have an expiry time");
        }

        List<PlayerRepository.Player> targets = new ArrayList<>();
        Set<String> uniqueNames = new HashSet<>();
        for (String rawName : names) {
            if (rawName == null || rawName.isBlank() || rawName.trim().length() > 16) {
                throw new IllegalArgumentException("Every target must be a valid player name");
            }
            String name = rawName.trim();
            if (!name.matches("[A-Za-z0-9_]{1,16}")) {
                throw new IllegalArgumentException("Invalid player name: " + name);
            }
            if (!uniqueNames.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate target: " + name);
            }
            PlayerRepository.Player target = players.findByName(name)
                    .orElseThrow(() -> new IllegalArgumentException("Player profile not found: " + name));
            try {
                UUID.fromString(target.uuid());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Player profile contains an invalid UUID: " + name, exception);
            }
            targets.add(target);
        }

        Connection connection = database.connection();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            List<AppliedPunishment> applied = new ArrayList<>(targets.size());
            for (PlayerRepository.Player target : targets) {
                long id = punishments.create(target.uuid(), target.lastName(), type, reason,
                        actorUuid, createdAt, expiresAt);
                activityLog.record(actorUuid, "BULK_PUNISHMENT_" + type.name(), target.uuid(),
                        "Bulk batch; punishment #" + id + "; reason=" + reason.trim(), createdAt);
                applied.add(new AppliedPunishment(id, target.uuid(), target.lastName()));
            }
            connection.commit();
            return List.copyOf(applied);
        } catch (SQLException | RuntimeException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public record AppliedPunishment(long id, String playerUuid, String playerName) { }
}
