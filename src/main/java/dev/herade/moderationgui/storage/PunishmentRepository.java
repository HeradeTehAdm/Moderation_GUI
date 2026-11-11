package dev.herade.moderationgui.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Хранение постоянных и временных наказаний. */
public final class PunishmentRepository {
    private final Database database;

    public PunishmentRepository(Database database) {
        this.database = database;
    }

    public long create(String playerUuid, String playerName, Type type, String reason,
                       String moderatorUuid, long createdAt, Long expiresAt) throws SQLException {
        if (type == null) throw new IllegalArgumentException("Punishment type cannot be null");
        String targetId = requireText(playerUuid, "playerUuid", 36);
        String targetName = requireText(playerName, "playerName", 16);
        String actorId = requireText(moderatorUuid, "moderatorUuid", 36);
        String safeReason = reason == null || reason.isBlank() ? "No reason provided" : reason.trim();
        if (safeReason.length() > 512) throw new IllegalArgumentException("Reason cannot exceed 512 characters");
        if (expiresAt != null && expiresAt <= createdAt) {
            throw new IllegalArgumentException("Punishment expiry must be later than its creation time");
        }

        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO punishments(player_uuid, player_name, type, reason, moderator_uuid, created_at, expires_at, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                """, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, targetId);
            statement.setString(2, targetName);
            statement.setString(3, type.name());
            statement.setString(4, safeReason);
            statement.setString(5, actorId);
            statement.setLong(6, createdAt);
            if (expiresAt == null) statement.setNull(7, java.sql.Types.BIGINT);
            else statement.setLong(7, expiresAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        throw new SQLException("SQLite did not return the punishment ID");
    }

    public boolean revoke(long punishmentId) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE punishments SET active = 0 WHERE id = ? AND active = 1")) {
            statement.setLong(1, punishmentId);
            return statement.executeUpdate() > 0;
        }
    }

    public int expireDue(long now) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE punishments SET active = 0 WHERE active = 1 AND expires_at IS NOT NULL AND expires_at <= ?")) {
            statement.setLong(1, now);
            return statement.executeUpdate();
        }
    }

    public List<Punishment> findActiveForPlayer(String playerUuid, long now) throws SQLException {
        List<Punishment> punishments = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT id, player_uuid, player_name, type, reason, moderator_uuid, created_at, expires_at, active
                FROM punishments
                WHERE player_uuid = ? AND active = 1 AND (expires_at IS NULL OR expires_at > ?)
                ORDER BY created_at DESC, id DESC
                """)) {
            statement.setString(1, requireText(playerUuid, "playerUuid", 36));
            statement.setLong(2, now);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long expiry = result.getLong("expires_at");
                    Long expiresAt = result.wasNull() ? null : expiry;
                    punishments.add(mapPunishment(result));
                }
            }
        }
        return List.copyOf(punishments);
    }

    /** Возвращает историю наказаний игрока, включая снятые и истёкшие записи. */
    public List<Punishment> findHistoryForPlayer(String playerUuid, int limit) throws SQLException {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
        List<Punishment> history = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT id, player_uuid, player_name, type, reason, moderator_uuid, created_at, expires_at, active
                FROM punishments
                WHERE player_uuid = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """)) {
            statement.setString(1, requireText(playerUuid, "playerUuid", 36));
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) history.add(mapPunishment(result));
            }
        }
        return List.copyOf(history);
    }

    public java.util.Optional<Punishment> findById(long punishmentId) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT id, player_uuid, player_name, type, reason, moderator_uuid, created_at, expires_at, active
                FROM punishments WHERE id = ?
                """)) {
            statement.setLong(1, punishmentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? java.util.Optional.of(mapPunishment(result))
                        : java.util.Optional.empty();
            }
        }
    }

    private static Punishment mapPunishment(ResultSet result) throws SQLException {
        long expiry = result.getLong("expires_at");
        Long expiresAt = result.wasNull() ? null : expiry;
        return new Punishment(result.getLong("id"), result.getString("player_uuid"),
                result.getString("player_name"), Type.valueOf(result.getString("type")),
                result.getString("reason"), result.getString("moderator_uuid"),
                result.getLong("created_at"), expiresAt, result.getInt("active") == 1);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
        return normalized;
    }

    public enum Type { BAN, MUTE, WARN }

    public record Punishment(long id, String playerUuid, String playerName, Type type, String reason,
                             String moderatorUuid, long createdAt, Long expiresAt, boolean active) { }
}
