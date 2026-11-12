package dev.herade.moderationgui.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModerationSettingsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultsAreWrittenAndCanBeChangedPersistently() throws Exception {
        Path file = temporaryDirectory.resolve("settings.properties");
        ModerationSettings settings = new ModerationSettings(file);

        settings.load();

        assertTrue(settings.enabled("enforce_bans"));
        assertTrue(settings.enabled("enforce_mutes"));
        settings.set("enforce_mutes", "false");
        assertFalse(settings.enabled("enforce_mutes"));

        ModerationSettings reloaded = new ModerationSettings(file);
        reloaded.load();
        assertTrue(reloaded.enabled("enforce_bans"));
        assertFalse(reloaded.enabled("enforce_mutes"));
    }

    @Test
    void rejectsUnknownKeysAndNonBooleanValues() throws Exception {
        ModerationSettings settings = new ModerationSettings(
                temporaryDirectory.resolve("settings.properties"));
        settings.load();

        assertThrows(IllegalArgumentException.class,
                () -> settings.set("unknown_setting", "true"));
        assertThrows(IllegalArgumentException.class,
                () -> settings.set("enforce_bans", "sometimes"));
        assertTrue(settings.enabled("enforce_bans"));
    }

    @Test
    void restoresDefaultsWhenSettingsFileIsMissing() throws Exception {
        Path file = temporaryDirectory.resolve("nested/settings.properties");
        ModerationSettings settings = new ModerationSettings(file);

        settings.load();

        assertTrue(Files.exists(file));
        assertTrue(settings.enabled("enforce_bans"));
        assertTrue(settings.enabled("enforce_mutes"));
    }
}
