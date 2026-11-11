package dev.herade.moderationgui.edition;

/** Редакция определяется при сборке JAR, а не настройкой клиента или сервера. */
public enum Edition {
    FREE,
    PRO;

    public static Edition parse(String value) {
        if (value == null) return FREE;
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "pro" -> PRO;
            case "free" -> FREE;
            default -> throw new IllegalStateException("Unknown Moderation GUI edition: " + value);
        };
    }
}
