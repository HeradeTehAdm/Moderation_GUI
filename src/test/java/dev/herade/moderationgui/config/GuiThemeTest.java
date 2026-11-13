package dev.herade.moderationgui.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuiThemeTest {
    @Test
    void cyclesThroughAllThemesAndWrapsToTheFirst() {
        assertEquals(GuiTheme.MIDNIGHT, GuiTheme.DARK.next());
        assertEquals(GuiTheme.VIOLET, GuiTheme.MIDNIGHT.next());
        assertEquals(GuiTheme.DARK, GuiTheme.VIOLET.next());
    }

    @Test
    void parsesNamesCaseInsensitivelyAndFallsBackToDark() {
        assertEquals(GuiTheme.DARK, GuiTheme.parse(null));
        assertEquals(GuiTheme.DARK, GuiTheme.parse("dark"));
        assertEquals(GuiTheme.MIDNIGHT, GuiTheme.parse("MIDNIGHT"));
        assertEquals(GuiTheme.VIOLET, GuiTheme.parse("Violet"));
        assertEquals(GuiTheme.DARK, GuiTheme.parse("unknown"));
    }

    @Test
    void themesExposeOpaqueGradientColors() {
        for (GuiTheme theme : GuiTheme.values()) {
            assertEquals(0xFF, theme.topColor() >>> 24);
            assertEquals(0xFF, theme.bottomColor() >>> 24);
            assertNotEquals(theme.topColor(), theme.bottomColor());
        }
    }
}
