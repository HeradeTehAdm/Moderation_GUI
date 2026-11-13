package dev.herade.moderationgui.service;

import dev.herade.moderationgui.storage.Database;
import dev.herade.moderationgui.storage.PunishmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BanEnforcementServiceTest {
    private static final long NOW = 2_000_000_000_000L;

    @TempDir
    Path temporaryDirectory;

    private Database database;
    private PunishmentRepository punishments;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(temporaryDirectory.resolve("bans.db"));
        database.open();
        punishments = new PunishmentRepository(database);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (database != null) database.close();
    }

    @Test
    void findsPermanentAndUnexpiredTemporaryBans() throws Exception {
        long permanentId = punishments.create("player-1", "PlayerOne",
                PunishmentRepository.Type.BAN, "Permanent", "staff", NOW - 10, null);
        assertEquals(permanentId, BanEnforcementService.findActiveBan(
                punishments, "player-1", NOW).orElseThrow().id());

        punishments.create("player-2", "PlayerTwo", PunishmentRepository.Type.BAN,
                "Temporary", "staff", NOW - 10, NOW + 10_000);
        assertTrue(BanEnforcementService.findActiveBan(punishments, "player-2", NOW).isPresent());
    }

    @Test
    void ignoresExpiredBansAndNonBanPunishments() throws Exception {
        punishments.create("player-1", "PlayerOne", PunishmentRepository.Type.BAN,
                "Expired", "staff", NOW - 20_000, NOW - 10_000);
        punishments.create("player-2", "PlayerTwo", PunishmentRepository.Type.MUTE,
                "Active mute", "staff", NOW - 10, null);
        punishments.create("player-3", "PlayerThree", PunishmentRepository.Type.WARN,
                "Warning", "staff", NOW - 10, null);

        assertTrue(BanEnforcementService.findActiveBan(punishments, "player-1", NOW).isEmpty());
        assertTrue(BanEnforcementService.findActiveBan(punishments, "player-2", NOW).isEmpty());
        assertTrue(BanEnforcementService.findActiveBan(punishments, "player-3", NOW).isEmpty());
        assertTrue(BanEnforcementService.findActiveBan(punishments, "missing", NOW).isEmpty());
    }

    @Test
    void propagatesDatabaseFailureSoJoinHandlerCanFailClosed() throws Exception {
        database.close();
        assertThrows(java.sql.SQLException.class,
                () -> BanEnforcementService.findActiveBan(punishments, "player-1", NOW));
    }
}
