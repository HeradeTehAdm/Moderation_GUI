package dev.herade.moderationgui.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseSchema {
    private DatabaseSchema() { }

    public static void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            statement.execute("INSERT INTO schema_version(version) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM schema_version)");
            statement.execute("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, last_name TEXT NOT NULL, first_joined_at INTEGER NOT NULL, last_seen_at INTEGER NOT NULL, notes TEXT NOT NULL DEFAULT '')");
            statement.execute("CREATE TABLE IF NOT EXISTS ranks (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE COLLATE NOCASE, display_prefix TEXT NOT NULL DEFAULT '', color TEXT NOT NULL DEFAULT 'white', priority INTEGER NOT NULL DEFAULT 0, parent_rank_id INTEGER REFERENCES ranks(id) ON DELETE SET NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS player_ranks (player_uuid TEXT NOT NULL REFERENCES players(uuid) ON DELETE CASCADE, rank_id INTEGER NOT NULL REFERENCES ranks(id) ON DELETE CASCADE, assigned_by TEXT NOT NULL, assigned_at INTEGER NOT NULL, PRIMARY KEY(player_uuid, rank_id))");
            statement.execute("CREATE TABLE IF NOT EXISTS permissions (id INTEGER PRIMARY KEY AUTOINCREMENT, node TEXT NOT NULL UNIQUE COLLATE NOCASE, description TEXT NOT NULL DEFAULT '')");
            statement.execute("CREATE TABLE IF NOT EXISTS rank_permissions (rank_id INTEGER NOT NULL REFERENCES ranks(id) ON DELETE CASCADE, permission_node TEXT NOT NULL, allowed INTEGER NOT NULL, PRIMARY KEY(rank_id, permission_node))");
            statement.execute("CREATE TABLE IF NOT EXISTS player_permissions (player_uuid TEXT NOT NULL REFERENCES players(uuid) ON DELETE CASCADE, permission_node TEXT NOT NULL, allowed INTEGER NOT NULL, assigned_by TEXT NOT NULL, PRIMARY KEY(player_uuid, permission_node))");
            statement.execute("CREATE TABLE IF NOT EXISTS punishments (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, type TEXT NOT NULL, reason TEXT NOT NULL, moderator_uuid TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER, active INTEGER NOT NULL DEFAULT 1)");
            statement.execute("CREATE TABLE IF NOT EXISTS activity_log (id INTEGER PRIMARY KEY AUTOINCREMENT, actor_uuid TEXT NOT NULL, action TEXT NOT NULL, target_uuid TEXT, details TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_punishments_player ON punishments(player_uuid, created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_activity_created ON activity_log(created_at DESC)");
        }
    }
}
