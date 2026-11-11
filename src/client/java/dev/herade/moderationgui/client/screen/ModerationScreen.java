package dev.herade.moderationgui.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Навигационная оболочка Moderation GUI; данные разделов подключаются через серверные пакеты. */
public final class ModerationScreen extends Screen {
    private static final String[] SECTIONS = {
            "Players", "Ranks", "Permissions", "Punishments",
            "History", "Settings", "Activity Log"
    };

    private final String selectedSection;

    public ModerationScreen() {
        this("Players");
    }

    public ModerationScreen(String selectedSection) {
        super(Component.literal("Moderation GUI"));
        this.selectedSection = selectedSection;
    }

    @Override
    protected void init() {
        int gap = 8;
        int buttonWidth = Math.max(64, Math.min(116, (this.width - 40 - gap * 3) / 4));
        int totalWidth = buttonWidth * 4 + gap * 3;
        int startX = (this.width - totalWidth) / 2;
        int firstRowY = Math.max(54, this.height / 2 - 74);

        for (int index = 0; index < SECTIONS.length; index++) {
            final String section = SECTIONS[index];
            int row = index / 4;
            int column = index % 4;
            int x = startX + column * (buttonWidth + gap);
            int y = firstRowY + row * 34;

            addRenderableWidget(Button.builder(Component.literal(section),
                    button -> this.minecraft.setScreen(new ModerationScreen(section)))
                    .bounds(x, y, buttonWidth, 26)
                    .build());
        }

        int footerY = Math.min(this.height - 58, firstRowY + 92);
        addRenderableWidget(Button.builder(Component.literal("Command Help"),
                button -> {
                    if (this.minecraft.player != null) {
                        this.minecraft.player.connection.sendCommand("moderationgui help");
                    }
                }).bounds(this.width / 2 - 124, footerY, 120, 26).build());

        addRenderableWidget(Button.builder(Component.literal("Close"),
                button -> this.onClose())
                .bounds(this.width / 2 + 4, footerY, 120, 26)
                .build());
    }

    @Override
    public Component getTitle() {
        return Component.literal("Moderation GUI | " + selectedSection);
    }
}
