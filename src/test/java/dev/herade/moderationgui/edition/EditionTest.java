package dev.herade.moderationgui.edition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditionTest {
    @Test
    void freeKeepsAllCoreModerationFeatures() {
        assertTrue(EditionFeature.PLAYER_PROFILES.minimumEdition() == Edition.FREE);
        assertTrue(EditionFeature.PUNISHMENTS.minimumEdition() == Edition.FREE);
        assertTrue(EditionFeature.RANKS_AND_PERMISSIONS.minimumEdition() == Edition.FREE);
        assertTrue(EditionFeature.ACTIVITY_LOG.minimumEdition() == Edition.FREE);
    }

    @Test
    void proFeaturesAreExplicitlyMarkedAsPro() {
        assertEquals(Edition.PRO, EditionFeature.ADVANCED_ANALYTICS.minimumEdition());
        assertEquals(Edition.PRO, EditionFeature.AUDIT_EXPORT.minimumEdition());
        assertEquals(Edition.PRO, EditionFeature.BULK_MODERATION.minimumEdition());
        assertEquals(Edition.PRO, EditionFeature.CUSTOM_GUI_THEMES.minimumEdition());
    }

    @Test
    void editionParsingDefaultsToFreeAndRejectsUnknownValues() {
        assertEquals(Edition.FREE, Edition.parse(null));
        assertEquals(Edition.FREE, Edition.parse("FREE"));
        assertEquals(Edition.PRO, Edition.parse("Pro"));
        assertThrows(IllegalStateException.class, () -> Edition.parse("enterprise"));
    }
}
