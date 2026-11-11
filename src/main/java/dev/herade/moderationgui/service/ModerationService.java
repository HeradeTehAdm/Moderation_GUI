package dev.herade.moderationgui.service;

import dev.herade.moderationgui.storage.ActivityLogRepository;
import dev.herade.moderationgui.storage.Database;
import dev.herade.moderationgui.storage.PunishmentRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Координирует изменения модерации и аудита в одной транзакции.
 * Фактическое применение наказаний к игровому серверу выполняет отдельный runtime-слой.
 */
public final class ModerationService {
    private final Database database;
    private final PunishmentRepository punishments;
    private final ActivityLogRepository activityLog;

    public ModerationService(Database database, PunishmentRepository punishments,
                             ActivityLogRepository activityLog) {
        this.database = database;
        this.punishments = punishments;
        this.activityLog = activityLog;
    }

    public long issuePunishment(String targetUuid, String targetName,
                                PunishmentRepository.Type type, String reason,
                                String moderatorUuid, long createdAt, Long expiresAt) throws SQLException {
        return inTransaction(() -> {
            long id = punishments.create(targetUuid, targetName, type, reason,
                    moderatorUuid, createdAt, expiresAt);
            String details = "Punishment #" + id + ": " + type.name()
                    + "; reason=" + (reason == null || reason.isBlank() ? "No reason provided" : reason.trim())
                    + "; expiresAt=" + (expiresAt == null ? "never" : expiresAt);
            activityLog.record(moderatorUuid, "PUNISHMENT_" + type.name(),
                    targetUuid, details, createdAt);
            return id;
        });
    }


    public void recordAction(String moderatorUuid, String action, String targetUuid,
                             String details, long timestamp) throws SQLException {
        inTransaction(() -> {
            activityLog.record(moderatorUuid, action, targetUuid, details, timestamp);
            return null;
        });
    }

    public boolean revokePunishment(long punishmentId, String moderatorUuid, long timestamp) throws SQLException {
        return inTransaction(() -> {
            Optional<PunishmentRepository.Punishment> existing = punishments.findById(punishmentId);
            if (existing.isEmpty() || !existing.get().active()) return false;

            boolean revoked = punishments.revoke(punishmentId);
            if (revoked) {
                activityLog.record(moderatorUuid, "PUNISHMENT_REVOKED",
                        existing.get().playerUuid(),
                        "Punishment #" + punishmentId + " revoked", timestamp);
            }
            return revoked;
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
