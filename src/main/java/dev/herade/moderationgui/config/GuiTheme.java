package dev.herade.moderationgui.config;

/** Встроенные цветовые схемы клиентского интерфейса. */
public enum GuiTheme {
    DARK("Dark", 0xFF111827, 0xFF1F2937),
    MIDNIGHT("Midnight", 0xFF080D1A, 0xFF1E1B4B),
    VIOLET("Violet", 0xFF1F1235, 0xFF5B21B6);

    private final String displayName;
    private final int topColor;
    private final int bottomColor;

    GuiTheme(String displayName, int topColor, int bottomColor) {
        this.displayName = displayName;
        this.topColor = topColor;
        this.bottomColor = bottomColor;
    }

    public String displayName() { return displayName; }
    public int topColor() { return topColor; }
    public int bottomColor() { return bottomColor; }

    public GuiTheme next() {
        GuiTheme[] themes = values();
        return themes[(ordinal() + 1) % themes.length];
    }

    public static GuiTheme parse(String value) {
        if (value == null) return DARK;
        for (GuiTheme theme : values()) {
            if (theme.name().equalsIgnoreCase(value.trim())
                    || theme.displayName.equalsIgnoreCase(value.trim())) {
                return theme;
            }
        }
        return DARK;
    }
}
