package dev.herade.moderationgui.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Хранение профилей игроков, включая игроков, которые сейчас не в сети. */
public final class PlayerRepository {
    private final Database database;

    public PlayerRepository(Database database) {
        this.database = database;
    }

    public void recordJoin(String uuid, String username, long timestamp) throws SQLException {
        String playerId = requireUuid(uuid);
        String name = requireName(username);
        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO players(uuid, last_name, first_joined_at, last_seen_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    last_name = excluded.last_name,
                    last_seen_at = MAX(players.last_seen_at, excluded.last_seen_at)
                """)) {
            statement.setString(1, playerId);
            statement.setString(2, name);
            statement.setLong(3, timestamp);
            statement.setLong(4, timestamp);
            statement.executeUpdate();
        }
    }

    public void updateLastSeen(String uuid, long timestamp) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE players SET last_seen_at = MAX(last_seen_at, ?) WHERE uuid = ?")) {
            statement.setLong(1, timestamp);
            statement.setString(2, requireUuid(uuid));
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("Player profile does not exist");
            }
        }
    }

    public Optional<Player> findByUuid(String uuid) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT uuid, last_name, first_joined_at, last_seen_at, notes FROM players WHERE uuid = ?")) {
            statement.setString(1, requireUuid(uuid));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(mapPlayer(result));
            }
        }
    }

    public Optional<Player> findByName(String username) throws SQLException {
        if (username == null || username.isBlank() || username.length() > 16) {
            return Optional.empty();
        }
        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT uuid, last_name, first_joined_at, last_seen_at, notes
                FROM players
                WHERE last_name = ? COLLATE NOCASE
                ORDER BY last_seen_at DESC
                LIMIT 1
                """)) {
            statement.setString(1, username.trim());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapPlayer(result)) : Optional.empty();
            }
        }
    }

    public List<Player> searchByName(String query, int limit) throws SQLException {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        String search = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        List<Player> players = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT uuid, last_name, first_joined_at, last_seen_at, notes
                FROM players
                WHERE instr(lower(last_name), ?) > 0
                ORDER BY last_seen_at DESC
                LIMIT ?
                """)) {
            statement.setString(1, search);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) players.add(mapPlayer(result));
            }
        }
        return List.copyOf(players);
    }

    public void updateNotes(String uuid, String notes) throws SQLException {
        String normalizedNotes = notes == null ? "" : notes;
        if (normalizedNotes.length() > 2000) {
            throw new IllegalArgumentException("Player notes cannot exceed 2000 characters");
        }
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE players SET notes = ? WHERE uuid = ?")) {
            statement.setString(1, normalizedNotes);
            statement.setString(2, requireUuid(uuid));
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("Player profile does not exist");
            }
        }
    }

    private static Player mapPlayer(ResultSet result) throws SQLException {
        return new Player(result.getString("uuid"), result.getString("last_name"),
                result.getLong("first_joined_at"), result.getLong("last_seen_at"),
                result.getString("notes"));
    }

    private static String requireUuid(String value) {
        if (value == null || value.isBlank() || value.length() > 36) {
            throw new IllegalArgumentException("Invalid player UUID");
        }
        return value.trim();
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank() || value.length() > 16) {
            throw new IllegalArgumentException("Player name must contain 1-16 characters");
        }
        return value.trim();
    }

    public record Player(String uuid, String lastName, long firstJoinedAt, long lastSeenAt, String notes) { }
}
