package dev.herade.moderationgui.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Неизменяемая история административных действий. */
public final class ActivityLogRepository {
    private final Database database;

    public ActivityLogRepository(Database database) {
        this.database = database;
    }

    public void record(String actorUuid, String action, String targetUuid, String details, long timestamp)
            throws SQLException {
        if (actorUuid == null || actorUuid.isBlank() || actorUuid.length() > 36) {
            throw new IllegalArgumentException("Invalid actor UUID");
        }
        if (action == null || action.isBlank() || action.length() > 64) {
            throw new IllegalArgumentException("Action must contain 1-64 characters");
        }
        if (targetUuid != null && !targetUuid.isBlank() && targetUuid.trim().length() > 36) {
            throw new IllegalArgumentException("Target UUID cannot exceed 36 characters");
        }
        String safeDetails = details == null ? "" : details;
        if (safeDetails.length() > 2000) throw new IllegalArgumentException("Activity details cannot exceed 2000 characters");

        try (PreparedStatement statement = database.connection().prepareStatement("""
                INSERT INTO activity_log(actor_uuid, action, target_uuid, details, created_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, actorUuid.trim());
            statement.setString(2, action.trim());
            if (targetUuid == null || targetUuid.isBlank()) statement.setNull(3, java.sql.Types.VARCHAR);
            else statement.setString(3, targetUuid.trim());
            statement.setString(4, safeDetails);
            statement.setLong(5, timestamp);
            statement.executeUpdate();
        }
    }

    public List<Entry> recent(int limit) throws SQLException {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        List<Entry> entries = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement("""
                SELECT id, actor_uuid, action, target_uuid, details, created_at
                FROM activity_log ORDER BY created_at DESC, id DESC LIMIT ?
                """)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    entries.add(new Entry(result.getLong("id"), result.getString("actor_uuid"),
                            result.getString("action"), result.getString("target_uuid"),
                            result.getString("details"), result.getLong("created_at")));
                }
            }
        }
        return List.copyOf(entries);
    }

    public record Entry(long id, String actorUuid, String action, String targetUuid,
                        String details, long createdAt) { }
}
