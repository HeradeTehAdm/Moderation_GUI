package dev.herade.moderationgui.edition;

import dev.herade.moderationgui.ModerationGui;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

/** Читает редакцию из метаданных JAR и централизованно проверяет доступность функций. */
public final class EditionManager {
    private static final String RESOURCE = "/moderation-gui-edition.properties";
    private static final Edition CURRENT = loadEdition();

    private EditionManager() { }

    public static Edition current() {
        return CURRENT;
    }

    public static boolean has(EditionFeature feature) {
        return CURRENT.ordinal() >= feature.minimumEdition().ordinal();
    }

    public static Set<EditionFeature> availableFeatures() {
        EnumSet<EditionFeature> available = EnumSet.noneOf(EditionFeature.class);
        for (EditionFeature feature : EditionFeature.values()) {
            if (has(feature)) available.add(feature);
        }
        return Set.copyOf(available);
    }

    public static void require(EditionFeature feature) {
        if (!has(feature)) {
            throw new FeatureUnavailableException(feature, CURRENT);
        }
    }

    private static Edition loadEdition() {
        Properties properties = new Properties();
        try (InputStream input = EditionManager.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                ModerationGui.LOGGER.warn("Edition metadata is missing; defaulting to Free.");
                return Edition.FREE;
            }
            properties.load(input);
            return Edition.parse(properties.getProperty("edition", "free"));
        } catch (IOException | IllegalStateException exception) {
            ModerationGui.LOGGER.error("Could not read edition metadata; defaulting to Free.", exception);
            return Edition.FREE;
        }
    }

    public static final class FeatureUnavailableException extends IllegalStateException {
        private final EditionFeature feature;
        private final Edition edition;

        public FeatureUnavailableException(EditionFeature feature, Edition edition) {
            super("Feature " + feature.name() + " is not available in the " + edition.name() + " edition.");
            this.feature = feature;
            this.edition = edition;
        }

        public EditionFeature feature() { return feature; }
        public Edition edition() { return edition; }
    }
}
