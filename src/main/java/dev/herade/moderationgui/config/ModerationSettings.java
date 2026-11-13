package dev.herade.moderationgui.config;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Хранит проверенные настройки сервера в отдельном properties-файле. */
public final class ModerationSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModerationSettings.class);
    private static final Map<String, Boolean> DEFAULTS = Map.of(
            "enforce_bans", true,
            "enforce_mutes", true
    );

    private final Path file;
    private final Map<String, Boolean> values = new LinkedHashMap<>();

    public ModerationSettings(Path file) {
        this.file = file.toAbsolutePath().normalize();
        values.putAll(DEFAULTS);
    }

    public synchronized void load() throws IOException {
        values.clear();
        values.putAll(DEFAULTS);
        if (Files.exists(file)) {
            Properties properties = new Properties();
            try (var input = Files.newInputStream(file)) {
                properties.load(input);
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Malformed settings file; restoring all defaults.", exception);
                properties.clear();
            }
            for (String key : DEFAULTS.keySet()) {
                String value = properties.getProperty(key);
                if (value != null) {
                    try {
                        values.put(key, parseBoolean(key, value));
                    } catch (IllegalArgumentException exception) {
                        LOGGER.warn("Invalid value for setting '{}'; restoring its default.", key);
                    }
                }
            }
        }
        save();
    }

    public synchronized boolean enabled(String key) {
        Boolean value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Unknown setting: " + key);
        return value;
    }

    public synchronized void set(String key, String rawValue) throws IOException {
        if (!DEFAULTS.containsKey(key)) throw new IllegalArgumentException("Unknown setting: " + key);
        boolean parsed = parseBoolean(key, rawValue);
        boolean previous = values.put(key, parsed);
        try {
            save();
        } catch (IOException exception) {
            values.put(key, previous);
            throw exception;
        }
    }

    public synchronized List<String> describe() {
        return List.of(
                "Settings file: " + file.getFileName(),
                "enforce_bans = " + enabled("enforce_bans"),
                "enforce_mutes = " + enabled("enforce_mutes"),
                "Change: /moderationgui settings set <key> <true|false>",
                "Available keys: enforce_bans, enforce_mutes"
        );
    }

    private void save() throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Properties properties = new Properties();
        values.forEach((key, value) -> properties.setProperty(key, value.toString()));
        Path temporary = Files.createTempFile(parent, ".moderation-settings-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(output, "Moderation GUI server settings");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean parseBoolean(String key, String value) {
        if ("true".equalsIgnoreCase(value.trim())) return true;
        if ("false".equalsIgnoreCase(value.trim())) return false;
        throw new IllegalArgumentException("Setting " + key + " must be true or false");
    }
}
