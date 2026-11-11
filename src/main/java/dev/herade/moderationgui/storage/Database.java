package dev.herade.moderationgui.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database implements AutoCloseable {
    private final Path file;
    private Connection connection;

    public Database(Path file) { this.file = file; }

    public synchronized void open() throws SQLException {
        if (connection != null && !connection.isClosed()) return;
        try {
            java.nio.file.Files.createDirectories(file.getParent());
        } catch (java.io.IOException exception) {
            throw new SQLException("Unable to create database directory", exception);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        DatabaseSchema.migrate(connection);
    }

    public synchronized Connection connection() throws SQLException {
        if (connection == null || connection.isClosed()) throw new SQLException("Database is not open");
        return connection;
    }

    @Override public synchronized void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) { }
            connection = null;
        }
    }
}
