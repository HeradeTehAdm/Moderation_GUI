package dev.herade.moderationgui.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database implements AutoCloseable {
    private final Path file;
    private Connection connection;

    public Database(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public synchronized void open() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }

        Path parent = file.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (java.io.IOException exception) {
                throw new SQLException("Unable to create database directory: " + parent, exception);
            }
        }

        Connection opened = DriverManager.getConnection("jdbc:sqlite:" + file);
        try {
            try (Statement statement = opened.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
                statement.execute("PRAGMA busy_timeout = 5000");
            }
            DatabaseSchema.migrate(opened);
            connection = opened;
        } catch (SQLException | RuntimeException exception) {
            try {
                opened.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    public synchronized Connection connection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Database is not open");
        }
        return connection;
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            // Shutdown must continue; the original failure is reported to the logger by the caller.
        } finally {
            connection = null;
        }
    }
}
