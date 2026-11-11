package dev.herade.moderationgui.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Операции хранения рангов и назначений игроков. */
public final class RankRepository {
    private final Database database;

    public RankRepository(Database database) {
        this.database = database;
    }

    public long create(String name, String prefix, String color, int priority) throws SQLException {
        String normalizedName = requireText(name, "name", 32);
        String normalizedPrefix = prefix == null ? "" : prefix;
        String normalizedColor = color == null || color.isBlank() ? "white" : color.trim();
        if (normalizedPrefix.length() > 64) throw new IllegalArgumentException("Rank prefix cannot exceed 64 characters");
        if (normalizedColor.length() > 24) throw new IllegalArgumentException("Rank color cannot exceed 24 characters");

        try (PreparedStatement statement = database.connection().prepareStatement(
                "INSERT INTO ranks(name, display_prefix, color, priority) VALUES (?, ?, ?, ?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, normalizedName);
            statement.setString(2, normalizedPrefix);
            statement.setString(3, normalizedColor);
            statement.setInt(4, priority);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        throw new SQLException("SQLite did not return the created rank ID");
    }

    public boolean delete(long rankId) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement("DELETE FROM ranks WHERE id = ?")) {
            statement.setLong(1, rankId);
            return statement.executeUpdate() > 0;
        }
    }

    /** Назначает родительский ранг и запрещает циклическое наследование. */
    public void setParent(long rankId, Long parentRankId) throws SQLException {
        Connection connection = database.connection();
        if (parentRankId != null) {
            if (rankId == parentRankId) throw new IllegalArgumentException("A rank cannot inherit from itself");
            Long cursor = parentRankId;
            int depth = 0;
            while (cursor != null) {
                if (cursor == rankId) throw new IllegalArgumentException("Rank inheritance cycle detected");
                if (++depth > 128) throw new IllegalStateException("Rank inheritance chain exceeds 128 levels");
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT parent_rank_id FROM ranks WHERE id = ?")) {
                    query.setLong(1, cursor);
                    try (ResultSet result = query.executeQuery()) {
                        if (!result.next()) throw new IllegalArgumentException("Parent rank does not exist: " + cursor);
                        long value = result.getLong(1);
                        cursor = result.wasNull() ? null : value;
                    }
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ranks SET parent_rank_id = ? WHERE id = ?")) {
            if (parentRankId == null) statement.setNull(1, java.sql.Types.INTEGER);
            else statement.setLong(1, parentRankId);
            statement.setLong(2, rankId);
            if (statement.executeUpdate() == 0) throw new IllegalArgumentException("Rank does not exist: " + rankId);
        }
    }

    public void assign(String playerUuid, long rankId, String assignedBy, long assignedAt) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO player_ranks(player_uuid, rank_id, assigned_by, assigned_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, rank_id) DO UPDATE SET
                    assigned_by = excluded.assigned_by,
                    assigned_at = excluded.assigned_at
                """)) {
            statement.setString(1, requireText(playerUuid, "playerUuid", 36));
            statement.setLong(2, rankId);
            statement.setString(3, requireText(assignedBy, "assignedBy", 36));
            statement.setLong(4, assignedAt);
            statement.executeUpdate();
        }
    }

    public boolean unassign(String playerUuid, long rankId) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM player_ranks WHERE player_uuid = ? AND rank_id = ?")) {
            statement.setString(1, requireText(playerUuid, "playerUuid", 36));
            statement.setLong(2, rankId);
            return statement.executeUpdate() > 0;
        }
    }

    public List<Rank> findForPlayer(String playerUuid) throws SQLException {
        List<Rank> ranks = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT r.id, r.name, r.display_prefix, r.color, r.priority, r.parent_rank_id
                FROM ranks r JOIN player_ranks pr ON pr.rank_id = r.id
                WHERE pr.player_uuid = ?
                ORDER BY r.priority DESC, r.name COLLATE NOCASE ASC
                """)) {
            statement.setString(1, requireText(playerUuid, "playerUuid", 36));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long parent = result.getLong("parent_rank_id");
                    Long parentId = result.wasNull() ? null : parent;
                    ranks.add(new Rank(result.getLong("id"), result.getString("name"),
                            result.getString("display_prefix"), result.getString("color"),
                            result.getInt("priority"), parentId));
                }
            }
        }
        return List.copyOf(ranks);
    }

    public List<Rank> findAll() throws SQLException {
        List<Rank> result = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT id, name, display_prefix, color, priority, parent_rank_id
                FROM ranks ORDER BY priority DESC, name COLLATE NOCASE ASC
                """);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                long parent = rows.getLong("parent_rank_id");
                Long parentId = rows.wasNull() ? null : parent;
                result.add(new Rank(rows.getLong("id"), rows.getString("name"),
                        rows.getString("display_prefix"), rows.getString("color"),
                        rows.getInt("priority"), parentId));
            }
        }
        return List.copyOf(result);
    }

    public void update(long rankId, String prefix, String color, int priority) throws SQLException {
        String normalizedPrefix = prefix == null ? "" : prefix;
        String normalizedColor = color == null || color.isBlank() ? "white" : color.trim();
        if (normalizedPrefix.length() > 64) throw new IllegalArgumentException("Rank prefix cannot exceed 64 characters");
        if (normalizedColor.length() > 24) throw new IllegalArgumentException("Rank color cannot exceed 24 characters");
        try (PreparedStatement statement = database.connection().prepareStatement("""
                UPDATE ranks SET display_prefix = ?, color = ?, priority = ? WHERE id = ?
                """)) {
            statement.setString(1, normalizedPrefix);
            statement.setString(2, normalizedColor);
            statement.setInt(3, priority);
            statement.setLong(4, rankId);
            if (statement.executeUpdate() == 0) throw new IllegalArgumentException("Rank does not exist: " + rankId);
        }
    }

    public Optional<Rank> findByName(String name) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT id, name, display_prefix, color, priority, parent_rank_id FROM ranks WHERE name = ?")) {
            statement.setString(1, requireText(name, "name", 32));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                long parent = result.getLong("parent_rank_id");
                Long parentId = result.wasNull() ? null : parent;
                return Optional.of(new Rank(result.getLong("id"), result.getString("name"),
                        result.getString("display_prefix"), result.getString("color"),
                        result.getInt("priority"), parentId));
            }
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
        return normalized;
    }

    public record Rank(long id, String name, String prefix, String color, int priority, Long parentRankId) { }
}
