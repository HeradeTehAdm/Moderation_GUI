package dev.herade.moderationgui.service;

import dev.herade.moderationgui.storage.ActivityLogRepository;
import dev.herade.moderationgui.storage.Database;
import dev.herade.moderationgui.storage.PermissionRepository;

import java.sql.Connection;
import java.sql.SQLException;

/** Управляет индивидуальными и групповыми правами с атомарным аудитом. */
public final class PermissionService {
    private final Database database;
    private final PermissionRepository permissions;
    private final ActivityLogRepository activityLog;

    public PermissionService(Database database, PermissionRepository permissions,
                             ActivityLogRepository activityLog) {
        this.database = database;
        this.permissions = permissions;
        this.activityLog = activityLog;
    }

    public void setRankRule(long rankId, String node, boolean allowed,
                            String actorUuid, long timestamp) throws SQLException {
        inTransaction(() -> {
            permissions.setRankPermission(rankId, node, allowed);
            activityLog.record(actorUuid, allowed ? "RANK_PERMISSION_GRANTED" : "RANK_PERMISSION_DENIED",
                    null, "Rank #" + rankId + "; node=" + node, timestamp);
            return null;
        });
    }

    public boolean removeRankRule(long rankId, String node, String actorUuid, long timestamp)
            throws SQLException {
        return inTransaction(() -> {
            boolean removed = permissions.removeRankPermission(rankId, node);
            if (removed) activityLog.record(actorUuid, "RANK_PERMISSION_REMOVED",
                    null, "Rank #" + rankId + "; node=" + node, timestamp);
            return removed;
        });
    }

    public void setPlayerRule(String playerUuid, String node, boolean allowed,
                              String actorUuid, long timestamp) throws SQLException {
        inTransaction(() -> {
            permissions.setPlayerPermission(playerUuid, node, allowed, actorUuid);
            activityLog.record(actorUuid, allowed ? "PLAYER_PERMISSION_GRANTED" : "PLAYER_PERMISSION_DENIED",
                    playerUuid, "node=" + node, timestamp);
            return null;
        });
    }

    public boolean removePlayerRule(String playerUuid, String node, String actorUuid, long timestamp)
            throws SQLException {
        return inTransaction(() -> {
            boolean removed = permissions.removePlayerPermission(playerUuid, node);
            if (removed) activityLog.record(actorUuid, "PLAYER_PERMISSION_REMOVED",
                    playerUuid, "node=" + node, timestamp);
            return removed;
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
