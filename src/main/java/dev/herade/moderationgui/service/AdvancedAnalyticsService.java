package dev.herade.moderationgui.service;

import dev.herade.moderationgui.storage.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Формирует сводную серверную аналитику для Pro-редакции. */
public final class AdvancedAnalyticsService {
    private static final long DAY_MILLIS = 86_400_000L;

    private AdvancedAnalyticsService() { }

    public static List<String> generate(Database database, long now) throws SQLException {
        Connection connection = database.connection();
        long thirtyDaysAgo = now - 30 * DAY_MILLIS;
        long sevenDaysAgo = now - 7 * DAY_MILLIS;
        List<String> report = new ArrayList<>();
        report.add("Advanced Analytics | Last updated: " + now);
        report.add("Player profiles: " + count(connection, "SELECT COUNT(*) FROM players"));
        report.add("Players seen in last 30 days: " + count(connection,
                "SELECT COUNT(*) FROM players WHERE last_seen_at >= ?", thirtyDaysAgo));
        report.add("Ranks: " + count(connection, "SELECT COUNT(*) FROM ranks"));
        report.add("Rank assignments: " + count(connection,
                "SELECT COUNT(*) FROM player_ranks"));
        report.add("All recorded punishments: " + count(connection,
                "SELECT COUNT(*) FROM punishments"));
        report.add("Active bans: " + count(connection,
                "SELECT COUNT(*) FROM punishments WHERE type = 'BAN' AND active = 1 "
                        + "AND (expires_at IS NULL OR expires_at > ?)", now));
        report.add("Active mutes: " + count(connection,
                "SELECT COUNT(*) FROM punishments WHERE type = 'MUTE' AND active = 1 "
                        + "AND (expires_at IS NULL OR expires_at > ?)", now));
        report.add("Warnings issued in last 30 days: " + count(connection,
                "SELECT COUNT(*) FROM punishments WHERE type = 'WARN' AND created_at >= ?",
                thirtyDaysAgo));
        report.add("Moderation actions in last 7 days: " + count(connection,
                "SELECT COUNT(*) FROM activity_log WHERE created_at >= ?", sevenDaysAgo));
        report.add("Top moderators (last 30 days):");
        List<String> moderators = topGroups(connection, """
                SELECT COALESCE(p.last_name, a.actor_uuid) AS display_name, COUNT(*) AS total
                FROM activity_log a
                LEFT JOIN players p ON p.uuid = a.actor_uuid
                WHERE a.created_at >= ?
                GROUP BY a.actor_uuid, p.last_name
                ORDER BY total DESC, display_name COLLATE NOCASE ASC
                LIMIT 5
                """, thirtyDaysAgo);
        report.addAll(moderators.isEmpty() ? List.of("No activity recorded.") : moderators);
        report.add("Most moderated players (last 30 days):");
        List<String> targets = topGroups(connection, """
                SELECT COALESCE(p.last_name, x.player_name) AS display_name, COUNT(*) AS total
                FROM punishments x
                LEFT JOIN players p ON p.uuid = x.player_uuid
                WHERE x.created_at >= ?
                GROUP BY x.player_uuid, p.last_name, x.player_name
                ORDER BY total DESC, display_name COLLATE NOCASE ASC
                LIMIT 5
                """, thirtyDaysAgo);
        report.addAll(targets.isEmpty() ? List.of("No punishments recorded.") : targets);
        return List.copyOf(report);
    }

    private static long count(Connection connection, String sql, long... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setLong(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Analytics count query returned no row");
                return result.getLong(1);
            }
        }
    }

    private static List<String> topGroups(Connection connection, String sql, long since)
            throws SQLException {
        List<String> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, since);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(result.getString("display_name") + ": "
                            + result.getLong("total"));
                }
            }
        }
        return rows;
    }
}
