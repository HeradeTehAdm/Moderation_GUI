package dev.herade.moderationgui;

import dev.herade.moderationgui.storage.ActivityLogRepository;
import dev.herade.moderationgui.storage.Database;
import dev.herade.moderationgui.storage.PermissionRepository;
import dev.herade.moderationgui.storage.PlayerRepository;
import dev.herade.moderationgui.storage.PunishmentRepository;
import dev.herade.moderationgui.storage.RankRepository;
import dev.herade.moderationgui.service.ModerationService;
import dev.herade.moderationgui.command.ModerationCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModerationGui implements ModInitializer {
    public static final String MOD_ID = "moderation_gui";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Database database;
    private static PlayerRepository players;
    private static RankRepository ranks;
    private static PermissionRepository permissions;
    private static PunishmentRepository punishments;
    private static ActivityLogRepository activityLog;
    private static ModerationService moderation;

    @Override
    public void onInitialize() {
        PlayerLifecycleEvents.register();
        ModerationEnforcementEvents.register();
        ModerationCommands.register();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                database = new Database(server.getServerDirectory()
                        .resolve("moderation-gui").resolve("moderation.db"));
                database.open();

                players = new PlayerRepository(database);
                ranks = new RankRepository(database);
                permissions = new PermissionRepository(database);
                punishments = new PunishmentRepository(database);
                activityLog = new ActivityLogRepository(database);
                moderation = new ModerationService(database, punishments, activityLog);

                LOGGER.info("Moderation GUI storage and repositories initialized.");
            } catch (Exception exception) {
                throw new IllegalStateException("Could not initialize Moderation GUI storage", exception);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (database != null) {
                try {
                    database.close();
                } catch (Exception exception) {
                    LOGGER.error("Could not close Moderation GUI database cleanly.", exception);
                }
            }
            database = null;
            players = null;
            ranks = null;
            permissions = null;
            punishments = null;
            activityLog = null;
            moderation = null;
        });

        LOGGER.info("Moderation GUI initialized. Command and client GUI modules are under development.");
    }

    public static Database database() {
        if (database == null) throw new IllegalStateException("Database is not available before server startup");
        return database;
    }

    public static PlayerRepository players() {
        if (players == null) throw new IllegalStateException("Player repository is not available before server startup");
        return players;
    }

    public static RankRepository ranks() {
        if (ranks == null) throw new IllegalStateException("Rank repository is not available before server startup");
        return ranks;
    }

    public static PermissionRepository permissions() {
        if (permissions == null) throw new IllegalStateException("Permission repository is not available before server startup");
        return permissions;
    }

    public static PunishmentRepository punishments() {
        if (punishments == null) throw new IllegalStateException("Punishment repository is not available before server startup");
        return punishments;
    }

    public static ModerationService moderation() {
        if (moderation == null) throw new IllegalStateException("Moderation service is not available before server startup");
        return moderation;
    }

    public static ActivityLogRepository activityLog() {
        if (activityLog == null) throw new IllegalStateException("Activity log is not available before server startup");
        return activityLog;
    }
}
