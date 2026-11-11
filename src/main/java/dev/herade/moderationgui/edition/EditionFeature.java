package dev.herade.moderationgui.edition;

/** Возможности продукта; базовая модерация остаётся доступной в Free. */
public enum EditionFeature {
    PLAYER_PROFILES(Edition.FREE),
    PUNISHMENTS(Edition.FREE),
    RANKS_AND_PERMISSIONS(Edition.FREE),
    ACTIVITY_LOG(Edition.FREE),
    ADVANCED_ANALYTICS(Edition.PRO),
    AUDIT_EXPORT(Edition.PRO),
    BULK_MODERATION(Edition.PRO),
    CUSTOM_GUI_THEMES(Edition.PRO);

    private final Edition minimumEdition;

    EditionFeature(Edition minimumEdition) {
        this.minimumEdition = minimumEdition;
    }

    public Edition minimumEdition() {
        return minimumEdition;
    }
}
