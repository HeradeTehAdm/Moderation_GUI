package dev.herade.moderationgui.edition;

/** Минимальная редакция и стадия готовности каждой возможности продукта. */
public enum EditionFeature {
    PLAYER_PROFILES(Edition.FREE, true),
    PUNISHMENTS(Edition.FREE, true),
    RANKS_AND_PERMISSIONS(Edition.FREE, true),
    ACTIVITY_LOG(Edition.FREE, true),
    ADVANCED_ANALYTICS(Edition.PRO, true),
    AUDIT_EXPORT(Edition.PRO, true),
    BULK_MODERATION(Edition.PRO, true),
    CUSTOM_GUI_THEMES(Edition.PRO, true);

    private final Edition minimumEdition;
    private final boolean implemented;

    EditionFeature(Edition minimumEdition, boolean implemented) {
        this.minimumEdition = minimumEdition;
        this.implemented = implemented;
    }

    public Edition minimumEdition() {
        return minimumEdition;
    }

    public boolean implemented() {
        return implemented;
    }
}
