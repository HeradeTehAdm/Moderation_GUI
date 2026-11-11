package dev.herade.moderationgui.service;

import dev.herade.moderationgui.storage.ActivityLogRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

/** Экспортирует ограниченную выборку журнала действий в CSV для Pro. */
public final class AuditExportService {
    private final ActivityLogRepository activityLog;

    public AuditExportService(ActivityLogRepository activityLog) {
        this.activityLog = activityLog;
    }

    public ExportResult export(Path serverDirectory, int limit) throws IOException, java.sql.SQLException {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Export limit must be between 1 and 200");
        }

        var entries = activityLog.recent(limit);
        Path serverRoot = serverDirectory.toAbsolutePath().normalize();
        Path exportDirectory = serverRoot.resolve("moderation-gui").resolve("exports").normalize();
        Files.createDirectories(exportDirectory);

        String fileName = "activity-log-" + System.currentTimeMillis()
                + "-" + UUID.randomUUID().toString().substring(0, 8) + ".csv";
        Path target = exportDirectory.resolve(fileName).normalize();
        if (!target.getParent().equals(exportDirectory)) {
            throw new IOException("Invalid export destination");
        }

        StringBuilder csv = new StringBuilder("\uFEFFid,created_at,actor_uuid,action,target_uuid,details\r\n");
        for (ActivityLogRepository.Entry entry : entries) {
            csv.append(entry.id()).append(',')
                    .append(cell(Instant.ofEpochMilli(entry.createdAt()).toString())).append(',')
                    .append(cell(entry.actorUuid())).append(',')
                    .append(cell(entry.action())).append(',')
                    .append(cell(entry.targetUuid())).append(',')
                    .append(cell(entry.details())).append("\r\n");
        }

        Path temporary = Files.createTempFile(exportDirectory, ".activity-log-", ".tmp");
        try {
            Files.writeString(temporary, csv, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }

        return new ExportResult(serverRoot.relativize(target), entries.size());
    }

    private static String cell(String value) {
        String safe = value == null ? "" : value;
        int firstVisible = 0;
        while (firstVisible < safe.length() && safe.charAt(firstVisible) <= ' ') {
            firstVisible++;
        }
        if (firstVisible < safe.length()
                && "=+-@".indexOf(safe.charAt(firstVisible)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    public record ExportResult(Path relativePath, int rows) { }
}
