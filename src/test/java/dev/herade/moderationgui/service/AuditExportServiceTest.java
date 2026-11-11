package dev.herade.moderationgui.service;

import dev.herade.moderationgui.storage.ActivityLogRepository;
import dev.herade.moderationgui.storage.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AuditExportServiceTest {
    @TempDir
    Path temporaryDirectory;

    private Database database;
    private ActivityLogRepository activityLog;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(temporaryDirectory.resolve("moderation.db"));
        database.open();
        activityLog = new ActivityLogRepository(database);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (database != null) database.close();
    }

    @Test
    void exportsRecentEntriesAsEscapedUtf8Csv() throws Exception {
        activityLog.record("staff-uuid", "PLAYER_WARNED", "player-uuid",
                "Spam, repeated \"quotes\" and details", 100);
        activityLog.record("staff-uuid", "PLAYER_KICKED", "player-uuid",
                "Second action", 200);

        AuditExportService.ExportResult result =
                new AuditExportService(activityLog).export(temporaryDirectory, 2);

        Path file = temporaryDirectory.resolve(result.relativePath());
        assertTrue(Files.exists(file));
        assertEquals(2, result.rows());

        String csv = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("id,created_at,actor_uuid,action,target_uuid,details"));
        assertTrue(csv.contains("PLAYER_KICKED"));
        assertTrue(csv.contains("PLAYER_WARNED"));
        assertTrue(csv.contains("\"Spam, repeated \"\"quotes\"\" and details\""));
    }

    @Test
    void rejectsInvalidExportLimits() {
        AuditExportService service = new AuditExportService(activityLog);
        assertThrows(IllegalArgumentException.class,
                () -> service.export(temporaryDirectory, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.export(temporaryDirectory, 201));
    }
}
