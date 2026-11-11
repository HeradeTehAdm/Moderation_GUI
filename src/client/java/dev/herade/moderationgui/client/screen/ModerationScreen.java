package dev.herade.moderationgui.client.screen;

import dev.herade.moderationgui.network.RequestSectionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Навигационная оболочка Moderation GUI; данные разделов подключаются через серверные пакеты. */
public final class ModerationScreen extends Screen {
    private static final String[] SECTIONS = {
            "Players", "Ranks", "Permissions", "Punishments",
            "History", "Settings", "Activity Log"
    };

    private final String selectedSection;
    private String content = "Loading data from server...";
    private MultiLineTextWidget contentWidget;
    private EditBox targetInput;
    private EditBox reasonInput;
    private EditBox durationInput;
    private EditBox punishmentIdInput;

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

        int contentY = firstRowY + 76;
        contentWidget = new MultiLineTextWidget(
                (this.width - Math.max(120, this.width - 60)) / 2,
                contentY,
                Component.literal(content),
                this.font
        ).setMaxWidth(Math.max(120, this.width - 60))
         .setMaxRows(selectedSection.equals("Punishments")
                 ? 3 : Math.max(2, Math.min(8, (this.height - contentY - 52) / 10)));
        addRenderableWidget(contentWidget);

        if (this.minecraft.player != null) {
            ClientPlayNetworking.send(new RequestSectionPayload(selectedSection));
        } else {
            content = "Join a server to load moderation data.";
            contentWidget.setMessage(Component.literal(content));
        }


        if (selectedSection.equals("Punishments")) {
            if (this.height >= 400) {
                int formY = contentY + 44;
                int fieldGap = 8;
                int fieldWidth = Math.max(90, Math.min(180, (this.width - 60 - fieldGap * 2) / 3));
                int fieldsWidth = fieldWidth * 3 + fieldGap * 2;
                int fieldsX = (this.width - fieldsWidth) / 2;

                targetInput = new EditBox(this.font, fieldsX, formY, fieldWidth, 20,
                        Component.literal("Target player"));
                targetInput.setHint(Component.literal("Player"));
                targetInput.setMaxLength(16);
                addRenderableWidget(targetInput);

                durationInput = new EditBox(this.font, fieldsX + fieldWidth + fieldGap,
                        formY, fieldWidth, 20, Component.literal("Duration in seconds"));
                durationInput.setHint(Component.literal("Seconds"));
                durationInput.setMaxLength(10);
                durationInput.setValue("3600");
                addRenderableWidget(durationInput);

                punishmentIdInput = new EditBox(this.font, fieldsX + (fieldWidth + fieldGap) * 2,
                        formY, fieldWidth, 20, Component.literal("Punishment ID"));
                punishmentIdInput.setHint(Component.literal("Punishment ID"));
                punishmentIdInput.setMaxLength(12);
                addRenderableWidget(punishmentIdInput);

                int reasonY = formY + 25;
                reasonInput = new EditBox(this.font, fieldsX, reasonY, fieldsWidth, 20,
                        Component.literal("Reason"));
                reasonInput.setHint(Component.literal("Reason"));
                reasonInput.setMaxLength(256);
                addRenderableWidget(reasonInput);

                int actionY = reasonY + 26;
                int actionWidth = Math.max(62, Math.min(88, (this.width - 60 - fieldGap * 3) / 4));
                int actionGap = 6;
                int actionsWidth = actionWidth * 4 + actionGap * 3;
                int actionsX = (this.width - actionsWidth) / 2;

                addActionButton(actionsX, actionY, actionWidth, "Warn",
                        () -> issue("warn", false));
                addActionButton(actionsX + actionWidth + actionGap, actionY, actionWidth, "Mute",
                        () -> issue("mute", false));
                addActionButton(actionsX + (actionWidth + actionGap) * 2, actionY, actionWidth, "Ban",
                        () -> issue("ban", false));
                addActionButton(actionsX + (actionWidth + actionGap) * 3, actionY, actionWidth, "Kick",
                        () -> issue("kick", false));

                int secondY = actionY + 29;
                addActionButton(actionsX, secondY, actionWidth, "Temp Mute",
                        () -> issue("tempmute", true));
                addActionButton(actionsX + actionWidth + actionGap, secondY, actionWidth, "Temp Ban",
                        () -> issue("tempban", true));
                addActionButton(actionsX + (actionWidth + actionGap) * 2, secondY,
                        actionWidth * 2 + actionGap, "Revoke ID", this::revoke);
            } else {
                contentWidget.setMessage(Component.literal(
                        "Increase the game window height to use moderation actions."));
            }
        }

        int footerY = this.height - 28;
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



    private void addActionButton(int x, int y, int width, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> action.run())
                .bounds(x, y, width, 22)
                .build());
    }

    private void issue(String action, boolean temporary) {
        if (targetInput == null || reasonInput == null) return;
        String target = targetInput.getValue().trim();
        String reason = sanitize(reasonInput.getValue());
        if (target.isBlank() || reason.isBlank()) {
            content = "Enter both a target player and a reason.";
            if (contentWidget != null) contentWidget.setMessage(Component.literal(content));
            return;
        }

        String command = "moderationgui " + action + " " + target + " ";
        if (temporary) {
            String duration = durationInput == null ? "" : durationInput.getValue().trim();
            try {
                long seconds = Long.parseLong(duration);
                if (seconds < 1 || seconds > 31536000L) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                content = "Duration must be between 1 and 31536000 seconds.";
                if (contentWidget != null) contentWidget.setMessage(Component.literal(content));
                return;
            }
            command += duration + " ";
        }
        runCommand(command + reason);
    }

    private void revoke() {
        if (punishmentIdInput == null) return;
        String id = punishmentIdInput.getValue().trim();
        try {
            if (Long.parseLong(id) < 1) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            content = "Enter a valid punishment ID.";
            if (contentWidget != null) contentWidget.setMessage(Component.literal(content));
            return;
        }
        runCommand("moderationgui unpunish " + id);
    }

    private void runCommand(String command) {
        if (this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand(command);
        }
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    public static void receiveData(String section, String content) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof ModerationScreen screen
                && screen.selectedSection.equals(section)) {
            screen.content = content == null ? "No data received." : content;
            if (screen.contentWidget != null) {
                screen.contentWidget.setMessage(Component.literal(screen.content));
            }
        }
    }

    @Override
    public Component getTitle() {
        return Component.literal("Moderation GUI | " + selectedSection);
    }
}
