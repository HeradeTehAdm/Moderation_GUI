package dev.herade.moderationgui.service;

import dev.herade.moderationgui.storage.ActivityLogRepository;
import dev.herade.moderationgui.storage.Database;
import dev.herade.moderationgui.storage.PlayerRepository;
import dev.herade.moderationgui.storage.PunishmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BulkModerationServiceTest {
    private static final long NOW = 2_000_000_000_000L;

    @TempDir
    Path temporaryDirectory;

    private Database database;
    private PlayerRepository players;
    private PunishmentRepository punishments;
    private ActivityLogRepository activity;
    private BulkModerationService service;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(temporaryDirectory.resolve("bulk.db"));
        database.open();
        players = new PlayerRepository(database);
        punishments = new PunishmentRepository(database);
        activity = new ActivityLogRepository(database);
        service = new BulkModerationService(database, players, punishments, activity);
        players.recordJoin("00000000-0000-0000-0000-000000000001", "PlayerOne", NOW);
        players.recordJoin("00000000-0000-0000-0000-000000000002", "PlayerTwo", NOW);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (database != null) database.close();
    }

    @Test
    void appliesBatchAndAuditToEveryTarget() throws Exception {
        var result = service.issue(List.of("PlayerOne", "PlayerTwo"),
                PunishmentRepository.Type.MUTE, "Repeated spam", "00000000-0000-0000-0000-000000000099",
                NOW, NOW + 60_000L);

        assertEquals(2, result.size());
        assertEquals(2, punishments.findRecentActive(20, NOW + 1).size());
        assertEquals(2, activity.recent(10).size());
        assertEquals("BULK_PUNISHMENT_MUTE", activity.recent(10).getFirst().action());
    }

    @Test
    void rejectsUnknownTargetBeforeWritingAnyPunishment() {
        assertThrows(IllegalArgumentException.class, () -> service.issue(
                List.of("PlayerOne", "MissingPlayer"), PunishmentRepository.Type.BAN,
                "Reason", "staff-1", NOW, null));
        assertTrue(assertDoesNotThrow(() -> punishments.findRecent(20, NOW)).isEmpty());
    }

    @Test
    void rollsBackEntireBatchWhenAnAuditInsertFails() throws Exception {
        try (Statement statement = database.connection().createStatement()) {
            statement.execute("""
                    CREATE TRIGGER fail_bulk_audit BEFORE INSERT ON activity_log
                    BEGIN SELECT RAISE(ABORT, 'forced audit failure'); END
                    """);
        }

        assertThrows(java.sql.SQLException.class, () -> service.issue(
                List.of("PlayerOne", "PlayerTwo"), PunishmentRepository.Type.BAN,
                "Reason", "staff-1", NOW, null));
        assertTrue(punishments.findRecent(20, NOW).isEmpty());
    }

    @Test
    void rejectsMalformedStoredUuidBeforeWritingAnyPunishment() throws Exception {
        players.recordJoin("not-a-uuid", "Malformed", NOW);
        assertThrows(IllegalArgumentException.class, () -> service.issue(
                List.of("Malformed"), PunishmentRepository.Type.BAN,
                "Reason", "00000000-0000-0000-0000-000000000099", NOW, null));
        assertTrue(punishments.findRecent(20, NOW).isEmpty());
    }

    @Test
    void warningsCannotHaveAnExpiryTime() {
        assertThrows(IllegalArgumentException.class, () -> service.issue(
                List.of("PlayerOne"), PunishmentRepository.Type.WARN,
                "Reason", "00000000-0000-0000-0000-000000000099", NOW, NOW + 1000));
    }

    @Test
    void rejectsDuplicatesAndMoreThanMaximumTargets() {
        assertThrows(IllegalArgumentException.class, () -> service.issue(
                List.of("PlayerOne", "playerone"), PunishmentRepository.Type.WARN,
                "Reason", "staff-1", NOW, null));
        List<String> tooMany = java.util.stream.IntStream.range(0, 26)
                .mapToObj(index -> "Player" + index).toList();
        assertThrows(IllegalArgumentException.class, () -> service.issue(
                tooMany, PunishmentRepository.Type.WARN, "Reason", "staff-1", NOW, null));
    }
}
