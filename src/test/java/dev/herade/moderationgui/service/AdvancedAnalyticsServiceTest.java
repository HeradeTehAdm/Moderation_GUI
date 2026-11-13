package dev.herade.moderationgui.service;

import dev.herade.moderationgui.storage.ActivityLogRepository;
import dev.herade.moderationgui.storage.Database;
import dev.herade.moderationgui.storage.PlayerRepository;
import dev.herade.moderationgui.storage.PunishmentRepository;
import dev.herade.moderationgui.storage.RankRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedAnalyticsServiceTest {
    private static final long NOW = 2_000_000_000_000L;
    private static final long DAY = 86_400_000L;

    @TempDir
    Path temporaryDirectory;

    private Database database;
    private PlayerRepository players;
    private RankRepository ranks;
    private PunishmentRepository punishments;
    private ActivityLogRepository activity;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(temporaryDirectory.resolve("analytics.db"));
        database.open();
        players = new PlayerRepository(database);
        ranks = new RankRepository(database);
        punishments = new PunishmentRepository(database);
        activity = new ActivityLogRepository(database);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (database != null) database.close();
    }

    @Test
    void reportCountsOnlyCurrentlyActiveTemporaryPunishments() throws Exception {
        players.recordJoin("player-1", "PlayerOne", NOW - DAY);
        players.recordJoin("player-2", "PlayerTwo", NOW - 60 * DAY);
        long member = ranks.create("Member", "", "green", 10);
        ranks.assign("player-1", member, "staff-1", NOW);
        punishments.create("player-1", "PlayerOne", PunishmentRepository.Type.BAN,
                "Active ban", "staff-1", NOW - 1000, null);
        punishments.create("player-1", "PlayerOne", PunishmentRepository.Type.MUTE,
                "Expired mute", "staff-1", NOW - 2 * DAY, NOW - DAY);
        punishments.create("player-2", "PlayerTwo", PunishmentRepository.Type.WARN,
                "Recent warning", "staff-1", NOW - DAY, null);
        activity.record("staff-1", "PUNISHMENT_BAN", "player-1", "Active ban", NOW - DAY);
        activity.record("staff-1", "PUNISHMENT_WARN", "player-2", "Warning", NOW - 2 * DAY);
        List<String> report = AdvancedAnalyticsService.generate(database, NOW);
        assertTrue(report.contains("Player profiles: 2"));
        assertTrue(report.contains("Players seen in last 30 days: 1"));
        assertTrue(report.contains("Ranks: 1"));
        assertTrue(report.contains("Rank assignments: 1"));
        assertTrue(report.contains("All recorded punishments: 3"));
        assertTrue(report.contains("Active bans: 1"));
        assertTrue(report.contains("Active mutes: 0"));
        assertTrue(report.contains("Warnings issued in last 30 days: 1"));
        assertTrue(report.contains("Moderation actions in last 7 days: 2"));
        assertTrue(report.contains("PlayerOne: 2"));
        assertTrue(report.contains("PlayerTwo: 1"));
    }

    @Test
    void emptyDatabaseProducesStableZeroReport() throws Exception {
        List<String> report = AdvancedAnalyticsService.generate(database, NOW);
        assertTrue(report.contains("Player profiles: 0"));
        assertTrue(report.contains("Active bans: 0"));
        assertTrue(report.contains("Active mutes: 0"));
        assertTrue(report.contains("No activity recorded."));
        assertTrue(report.contains("No punishments recorded."));
    }
}
