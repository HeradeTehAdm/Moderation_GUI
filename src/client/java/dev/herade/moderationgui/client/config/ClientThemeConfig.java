package dev.herade.moderationgui.client.config;

import dev.herade.moderationgui.config.GuiTheme;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Локальные настройки темы; файл не отправляется на сервер. */
public final class ClientThemeConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("moderation_gui/client");
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("moderation-gui-client.properties");
    private static GuiTheme current;

    private ClientThemeConfig() { }

    public static synchronized GuiTheme current() {
        if (current == null) current = load();
        return current;
    }

    public static synchronized GuiTheme cycle() {
        current = current().next();
        save(current);
        return current;
    }

    private static GuiTheme load() {
        if (!Files.exists(FILE)) return GuiTheme.DARK;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            GuiTheme parsed = GuiTheme.parse(properties.getProperty("theme"));
            if (!parsed.name().equalsIgnoreCase(properties.getProperty("theme", ""))) {
                LOGGER.warn("Unknown GUI theme in {}; using Dark.", FILE);
            }
            return parsed;
        } catch (IOException exception) {
            LOGGER.warn("Could not read GUI theme settings from {}; using Dark.", FILE, exception);
            return GuiTheme.DARK;
        }
    }

    private static void save(GuiTheme theme) {
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        Properties properties = new Properties();
        properties.setProperty("theme", theme.name().toLowerCase(java.util.Locale.ROOT));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Moderation GUI client settings");
            }
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.error("Could not save GUI theme settings to {}.", FILE, exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
        }
    }
}
