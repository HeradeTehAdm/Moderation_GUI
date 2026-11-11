package dev.herade.moderationgui.storage;

import dev.herade.moderationgui.service.ModerationService;
import dev.herade.moderationgui.service.RankService;
import dev.herade.moderationgui.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    private Database database;
    private PlayerRepository players;
    private RankRepository ranks;
    private PermissionRepository permissions;
    private PunishmentRepository punishments;
    private ActivityLogRepository activityLog;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(temporaryDirectory.resolve("moderation.db"));
        database.open();
        players = new PlayerRepository(database);
        ranks = new RankRepository(database);
        permissions = new PermissionRepository(database);
        punishments = new PunishmentRepository(database);
        activityLog = new ActivityLogRepository(database);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (database != null) database.close();
    }

    @Test
    void migrationIsIdempotentAndUsesCurrentVersion() throws Exception {
        DatabaseSchema.migrate(database.connection());
        DatabaseSchema.migrate(database.connection());

        try (PreparedStatement statement = database.connection()
                .prepareStatement("SELECT version FROM schema_version");
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals(2, result.getInt(1));
            assertFalse(result.next());
        }
    }

    @Test
    void playerProfileKeepsFirstJoinAndUpdatesLastSeen() throws Exception {
        players.recordJoin("player-uuid", "PlayerOne", 100);
        players.recordJoin("player-uuid", "PlayerRenamed", 200);
        players.updateNotes("player-uuid", "Trusted member");

        PlayerRepository.Player player = players.findByUuid("player-uuid").orElseThrow();
        assertEquals("PlayerRenamed", player.lastName());
        assertEquals(100, player.firstJoinedAt());
        assertEquals(200, player.lastSeenAt());
        assertEquals("Trusted member", player.notes());
        assertEquals(1, players.searchByName("renamed", 10).size());
    }

    @Test
    void lastSeenTimestampNeverMovesBackwards() throws Exception {
        players.recordJoin("player-uuid", "PlayerOne", 500);
        players.updateLastSeen("player-uuid", 700);
        players.updateLastSeen("player-uuid", 600);

        PlayerRepository.Player player = players.findByUuid("player-uuid").orElseThrow();
        assertEquals(700, player.lastSeenAt());
    }

    @Test
    void rankInheritanceRejectsCyclesAndAssignsRanks() throws Exception {
        players.recordJoin("player-uuid", "PlayerOne", 100);
        long base = ranks.create("Member", "[M]", "green", 10);
        long staff = ranks.create("Staff", "[S]", "gold", 50);
        ranks.setParent(staff, base);
        ranks.assign("player-uuid", staff, "moderator-uuid", 150);

        assertEquals(1, ranks.findForPlayer("player-uuid").size());
        assertThrows(IllegalArgumentException.class, () -> ranks.setParent(base, staff));
    }

    @Test
    void personalPermissionsOverrideInheritedRankPermissions() throws Exception {
        players.recordJoin("player-uuid", "PlayerOne", 100);
        long member = ranks.create("Member", "", "green", 10);
        long staff = ranks.create("Staff", "", "gold", 50);
        ranks.setParent(staff, member);
        ranks.assign("player-uuid", staff, "moderator-uuid", 150);

        permissions.setRankPermission(member, "moderation.*", true);
        permissions.setRankPermission(staff, "moderation.ban", false);

        assertTrue(permissions.hasPermission("player-uuid", "moderation.kick"));
        assertFalse(permissions.hasPermission("player-uuid", "moderation.ban"));

        permissions.setPlayerPermission("player-uuid", "moderation.ban", true, "owner-uuid");
        assertTrue(permissions.hasPermission("player-uuid", "moderation.ban"));
        permissions.setPlayerPermission("player-uuid", "moderation.kick", false, "owner-uuid");
        assertFalse(permissions.hasPermission("player-uuid", "moderation.kick"));
    }

    @Test
    void temporaryPunishmentsExpireAndCanBeRevoked() throws Exception {
        long temporary = punishments.create("player-uuid", "PlayerOne",
                PunishmentRepository.Type.MUTE, "Spam", "moderator-uuid", 100, 200L);
        long permanent = punishments.create("player-uuid", "PlayerOne",
                PunishmentRepository.Type.BAN, "Repeated abuse", "moderator-uuid", 100, null);

        List<PunishmentRepository.Punishment> active = punishments.findActiveForPlayer("player-uuid", 150);
        assertEquals(2, active.size());
        assertEquals(1, punishments.expireDue(200));
        assertEquals(1, punishments.findActiveForPlayer("player-uuid", 200).size());
        assertTrue(punishments.revoke(permanent));
        assertFalse(punishments.revoke(temporary));
        assertTrue(punishments.findActiveForPlayer("player-uuid", 250).isEmpty());
    }

    @Test
    void rankServiceAuditsCreateUpdateAssignAndUnassign() throws Exception {
        players.recordJoin("player-uuid", "PlayerOne", 100);
        RankService service = new RankService(database, ranks, activityLog);
        long rankId = service.create("Helper", "[H]", "aqua", 20, "staff-uuid", 200);

        service.assign("player-uuid", rankId, "staff-uuid", 210);
        assertEquals(1, ranks.findForPlayer("player-uuid").size());

        service.update(rankId, "[HELPER]", "gold", 25, "staff-uuid", 220);
        assertEquals("[HELPER]", ranks.findByName("Helper").orElseThrow().prefix());
        assertEquals(25, ranks.findAll().getFirst().priority());

        assertTrue(service.unassign("player-uuid", rankId, "staff-uuid", 230));
        assertTrue(service.delete(rankId, "staff-uuid", 240));
        assertEquals(0, ranks.findAll().size());
        assertEquals("RANK_DELETED", activityLog.recent(1).getFirst().action());
    }

    @Test
    void rankParentChangesAreAudited() throws Exception {
        long parent = ranks.create("Parent", "", "white", 10);
        long child = ranks.create("Child", "", "gold", 20);
        RankService service = new RankService(database, ranks, activityLog);

        service.setParent(child, parent, "staff-uuid", 300);

        assertEquals(parent, ranks.findByName("Child").orElseThrow().parentRankId());
        assertEquals("RANK_PARENT_UPDATED", activityLog.recent(1).getFirst().action());
    }

    @Test
    void playerNotesChangesAreAudited() throws Exception {
        players.recordJoin("player-uuid", "PlayerOne", 100);
        ModerationService service = new ModerationService(database, punishments, activityLog);

        service.updatePlayerNotes("player-uuid", "Needs follow-up", "staff-uuid", 300);

        assertEquals("Needs follow-up", players.findByUuid("player-uuid").orElseThrow().notes());
        assertEquals("PLAYER_NOTES_UPDATED", activityLog.recent(1).getFirst().action());
        assertEquals("player-uuid", activityLog.recent(1).getFirst().targetUuid());
    }

    @Test
    void recentPunishmentsSeparatesActiveFromHistoricalEntries() throws Exception {
        punishments.create("player-uuid", "PlayerOne", PunishmentRepository.Type.MUTE,
                "Expired mute", "staff-uuid", 100, 200L);
        punishments.create("player-uuid", "PlayerOne", PunishmentRepository.Type.BAN,
                "Permanent ban", "staff-uuid", 300, null);

        assertEquals(1, punishments.findRecentActive(20, 400).size());
        assertEquals(2, punishments.findRecent(20, 400).size());
        assertFalse(punishments.findRecent(20, 400).getLast().active());
    }

    @Test
    void punishmentHistoryIncludesRevokedRecords() throws Exception {
        long id = punishments.create("player-uuid", "PlayerOne",
                PunishmentRepository.Type.WARN, "First warning", "staff-uuid", 100, null);
        assertTrue(punishments.revoke(id));

        assertEquals(id, punishments.findById(id).orElseThrow().id());
        assertEquals(1, punishments.findHistoryForPlayer("player-uuid", 20).size());
        assertFalse(punishments.findHistoryForPlayer("player-uuid", 20).getFirst().active());
        assertTrue(punishments.findActiveForPlayer("player-uuid", 200).isEmpty());
    }

    @Test
    void moderationServiceWritesPunishmentAndAuditTogether() throws Exception {
        ModerationService service = new ModerationService(database, punishments, activityLog);
        long punishmentId = service.issuePunishment("player-uuid", "PlayerOne",
                PunishmentRepository.Type.WARN, "Please follow the rules",
                "staff-uuid", 300, null);

        assertEquals(1, punishments.findActiveForPlayer("player-uuid", 300).size());
        ActivityLogRepository.Entry entry = activityLog.recent(1).getFirst();
        assertEquals("PUNISHMENT_WARN", entry.action());
        assertTrue(entry.details().contains("Punishment #" + punishmentId));

        assertTrue(service.revokePunishment(punishmentId, "staff-uuid", 400));
        assertEquals(0, punishments.findActiveForPlayer("player-uuid", 400).size());
        assertEquals("PUNISHMENT_REVOKED", activityLog.recent(1).getFirst().action());
        assertEquals("player-uuid", activityLog.recent(1).getFirst().targetUuid());
    }

    @Test
    void permissionServiceAuditsGrantDenyAndRemoval() throws Exception {
        players.recordJoin("player-uuid", "PlayerOne", 100);
        long rankId = ranks.create("Moderator", "", "gold", 50);
        PermissionService service = new PermissionService(database, permissions, activityLog);

        service.setRankRule(rankId, "moderation.*", true, "staff-uuid", 200);
        service.setPlayerRule("player-uuid", "moderation.ban", false, "staff-uuid", 210);
        ranks.assign("player-uuid", rankId, "staff-uuid", 220);

        assertTrue(permissions.hasPermission("player-uuid", "moderation.kick"));
        assertFalse(permissions.hasPermission("player-uuid", "moderation.ban"));
        assertEquals(1, permissions.findRankPermissions(rankId).size());
        assertEquals(1, permissions.findPlayerPermissions("player-uuid").size());

        assertTrue(service.removePlayerRule("player-uuid", "moderation.ban", "staff-uuid", 230));
        assertTrue(permissions.hasPermission("player-uuid", "moderation.ban"));
        assertEquals("PLAYER_PERMISSION_REMOVED", activityLog.recent(1).getFirst().action());
    }

    @Test
    void directRankRuleWinsOverInheritedRuleAtSameSpecificity() throws Exception {
        players.recordJoin("player-uuid", "PlayerOne", 100);
        long parent = ranks.create("Parent", "", "white", 1000);
        long child = ranks.create("Child", "", "gold", 5);
        ranks.setParent(child, parent);
        ranks.assign("player-uuid", child, "staff-uuid", 150);

        permissions.setRankPermission(parent, "moderation.kick", true);
        permissions.setRankPermission(child, "moderation.kick", false);

        assertFalse(permissions.hasPermission("player-uuid", "moderation.kick"));
    }

    @Test
    void permissionNodesRejectMalformedInput() {
        assertThrows(IllegalArgumentException.class,
                () -> permissions.setRankPermission(1, "moderation..ban", true));
        assertThrows(IllegalArgumentException.class,
                () -> permissions.hasPermission("player-uuid", "moderation.*"));
    }

    @Test
    void activityLogReturnsNewestEntriesFirst() throws Exception {
        activityLog.record("staff-uuid", "WARN", "player-uuid", "First warning", 100);
        activityLog.record("staff-uuid", "MUTE", "player-uuid", "Repeated spam", 200);

        List<ActivityLogRepository.Entry> entries = activityLog.recent(10);
        assertEquals(2, entries.size());
        assertEquals("MUTE", entries.getFirst().action());
        assertEquals("WARN", entries.getLast().action());
    }
}
