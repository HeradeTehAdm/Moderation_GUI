package dev.herade.moderationgui;

import dev.herade.moderationgui.storage.Database;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModerationGui implements ModInitializer {
    public static final String MOD_ID = "moderation_gui";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static Database database;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                database = new Database(server.getServerDirectory().resolve("moderation-gui").resolve("moderation.db"));
                database.open();
                LOGGER.info("Moderation GUI database initialized.");
            } catch (Exception exception) {
                throw new IllegalStateException("Could not initialize Moderation GUI storage", exception);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (database != null) {
                database.close();
                database = null;
            }
        });
        LOGGER.info("Moderation GUI initialized. GUI and command modules are under development.");
    }

    public static Database database() {
        if (database == null) throw new IllegalStateException("Database is not available before server startup");
        return database;
    }
}
