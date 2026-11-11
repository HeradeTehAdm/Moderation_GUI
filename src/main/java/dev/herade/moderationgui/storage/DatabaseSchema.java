package dev.herade.moderationgui.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Управляет версионируемой схемой SQLite.
 * Все миграции выполняются в одной транзакции.
 */
public final class DatabaseSchema {
    private static final int CURRENT_VERSION = 2;

    private DatabaseSchema() { }

    public static void migrate(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            createVersionTable(connection);
            int version = readVersion(connection);
            if (version > CURRENT_VERSION) {
                throw new SQLException("Database schema version " + version
                        + " is newer than supported version " + CURRENT_VERSION);
            }

            if (version < 1) {
                migrateToVersionOne(connection);
                writeVersion(connection, 1);
                version = 1;
            }

            if (version < 2) {
                migrateToVersionTwo(connection);
                writeVersion(connection, 2);
            }

            connection.commit();
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

    private static void createVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            statement.execute("INSERT INTO schema_version(version) SELECT 0 "
                    + "WHERE NOT EXISTS (SELECT 1 FROM schema_version)");
        }
    }

    private static int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static void writeVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM schema_version");
            try (var update = connection.prepareStatement(
                    "INSERT INTO schema_version(version) VALUES (?)")) {
                update.setInt(1, version);
                update.executeUpdate();
            }
        }
    }

    private static void migrateToVersionOne(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid TEXT PRIMARY KEY,
                        last_name TEXT NOT NULL,
                        first_joined_at INTEGER NOT NULL,
                        last_seen_at INTEGER NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ranks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        display_prefix TEXT NOT NULL DEFAULT '',
                        color TEXT NOT NULL DEFAULT 'white',
                        priority INTEGER NOT NULL DEFAULT 0,
                        parent_rank_id INTEGER REFERENCES ranks(id) ON DELETE SET NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_ranks (
                        player_uuid TEXT NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
                        rank_id INTEGER NOT NULL REFERENCES ranks(id) ON DELETE CASCADE,
                        assigned_by TEXT NOT NULL,
                        assigned_at INTEGER NOT NULL,
                        PRIMARY KEY(player_uuid, rank_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS permissions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        node TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        description TEXT NOT NULL DEFAULT ''
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS rank_permissions (
                        rank_id INTEGER NOT NULL REFERENCES ranks(id) ON DELETE CASCADE,
                        permission_node TEXT NOT NULL,
                        allowed INTEGER NOT NULL CHECK(allowed IN (0, 1)),
                        PRIMARY KEY(rank_id, permission_node)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_permissions (
                        player_uuid TEXT NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
                        permission_node TEXT NOT NULL,
                        allowed INTEGER NOT NULL CHECK(allowed IN (0, 1)),
                        assigned_by TEXT NOT NULL,
                        PRIMARY KEY(player_uuid, permission_node)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS punishments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        moderator_uuid TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER,
                        active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1))
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS activity_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        actor_uuid TEXT NOT NULL,
                        action TEXT NOT NULL,
                        target_uuid TEXT,
                        details TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    private static void migrateToVersionTwo(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX IF NOT EXISTS idx_players_name ON players(last_name COLLATE NOCASE)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_player_ranks_rank ON player_ranks(rank_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_punishments_player ON punishments(player_uuid, created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_punishments_active ON punishments(player_uuid, active, expires_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_activity_created ON activity_log(created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_activity_target ON activity_log(target_uuid, created_at DESC)");
        }
    }
}
