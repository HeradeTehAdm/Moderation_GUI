package dev.herade.moderationgui.service;

import dev.herade.moderationgui.storage.ActivityLogRepository;
import dev.herade.moderationgui.storage.Database;
import dev.herade.moderationgui.storage.RankRepository;

import java.sql.Connection;
import java.sql.SQLException;

/** Выполняет операции с рангами вместе с записью аудита. */
public final class RankService {
    private final Database database;
    private final RankRepository ranks;
    private final ActivityLogRepository activityLog;

    public RankService(Database database, RankRepository ranks, ActivityLogRepository activityLog) {
        this.database = database;
        this.ranks = ranks;
        this.activityLog = activityLog;
    }

    public long create(String name, String prefix, String color, int priority,
                       String actorUuid, long timestamp) throws SQLException {
        return inTransaction(() -> {
            long id = ranks.create(name, prefix, color, priority);
            activityLog.record(actorUuid, "RANK_CREATED", null,
                    "Rank #" + id + " created: " + name, timestamp);
            return id;
        });
    }

    public boolean delete(long rankId, String actorUuid, long timestamp) throws SQLException {
        return inTransaction(() -> {
            boolean deleted = ranks.delete(rankId);
            if (deleted) activityLog.record(actorUuid, "RANK_DELETED", null,
                    "Rank #" + rankId + " deleted", timestamp);
            return deleted;
        });
    }

    public void assign(String playerUuid, long rankId, String actorUuid, long timestamp) throws SQLException {
        inTransaction(() -> {
            ranks.assign(playerUuid, rankId, actorUuid, timestamp);
            activityLog.record(actorUuid, "RANK_ASSIGNED", playerUuid,
                    "Rank #" + rankId + " assigned", timestamp);
            return null;
        });
    }

    public boolean unassign(String playerUuid, long rankId, String actorUuid, long timestamp) throws SQLException {
        return inTransaction(() -> {
            boolean removed = ranks.unassign(playerUuid, rankId);
            if (removed) activityLog.record(actorUuid, "RANK_UNASSIGNED", playerUuid,
                    "Rank #" + rankId + " unassigned", timestamp);
            return removed;
        });
    }

    public void update(long rankId, String prefix, String color, int priority,
                       String actorUuid, long timestamp) throws SQLException {
        inTransaction(() -> {
            ranks.update(rankId, prefix, color, priority);
            activityLog.record(actorUuid, "RANK_UPDATED", null,
                    "Rank #" + rankId + " updated", timestamp);
            return null;
        });
    }

    private <T> T inTransaction(SqlOperation<T> operation) throws SQLException {
        Connection connection = database.connection();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = operation.run();
            connection.commit();
            return result;
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

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run() throws SQLException;
    }
}
