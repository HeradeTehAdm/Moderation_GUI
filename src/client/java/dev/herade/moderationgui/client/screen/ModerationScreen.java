package dev.herade.moderationgui.client.screen;

import dev.herade.moderationgui.network.RequestSectionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/** Навигационная оболочка Moderation GUI; данные разделов подключаются через серверные пакеты. */
public final class ModerationScreen extends Screen {
    private static final String[] SECTIONS = {
            "Players", "Ranks", "Permissions", "Punishments",
            "History", "Settings", "Activity Log", "Analytics", "Bulk Moderation"
    };

    private final String selectedSection;
    private String content = "Loading data from server...";
    private List<String> contentLines = List.of(content);
    private int contentPage;
    private int contentAreaTop;
    private int contentAreaBottom;
    private MultiLineTextWidget contentWidget;
    private EditBox targetInput;
    private EditBox reasonInput;
    private EditBox durationInput;
    private EditBox punishmentIdInput;
    private EditBox notePlayerInput;
    private EditBox noteTextInput;
    private EditBox rankNameInput;
    private EditBox rankPriorityInput;
    private EditBox rankPrefixInput;
    private EditBox rankColorInput;
    private EditBox rankPlayerInput;
    private EditBox rankParentInput;
    private EditBox permissionTargetTypeInput;
    private EditBox permissionTargetInput;
    private EditBox permissionNodeInput;
    private EditBox settingKeyInput;
    private EditBox settingValueInput;
    private EditBox searchInput;
    private EditBox bulkTargetsInput;
    private EditBox bulkDurationInput;
    private EditBox bulkReasonInput;

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
        int navigationRows = (SECTIONS.length + 3) / 4;
        int firstRowY = Math.max(42, this.height / 2 - (navigationRows * 34 + 80) / 2);

        for (int index = 0; index < SECTIONS.length; index++) {
            final String section = SECTIONS[index];
            int row = index / 4;
            int column = index % 4;
            int itemsInRow = Math.min(4, SECTIONS.length - row * 4);
            int rowWidth = itemsInRow * buttonWidth + (itemsInRow - 1) * gap;
            int rowStart = (this.width - rowWidth) / 2;
            int x = rowStart + column * (buttonWidth + gap);
            int y = firstRowY + row * 34;

            addRenderableWidget(Button.builder(Component.literal(section),
                    button -> this.minecraft.gui.setScreen(new ModerationScreen(section)))
                    .bounds(x, y, buttonWidth, 26)
                    .build());
        }

        int contentY = firstRowY + navigationRows * 34 + 34;
        contentAreaTop = contentY;
        contentWidget = new MultiLineTextWidget(
                (this.width - Math.max(120, this.width - 60)) / 2,
                contentY,
                Component.literal(content),
                this.font
        ).setMaxWidth(Math.max(120, this.width - 60))
         .setMaxRows(isManagementSection()
                 ? 3 : Math.max(2, Math.min(8, (this.height - contentY - 52) / 10)));
        addRenderableWidget(contentWidget);

        int searchWidth = Math.max(120, Math.min(220, this.width - 150));
        int searchGroupWidth = searchWidth + 72;
        int searchX = (this.width - searchGroupWidth) / 2;
        searchInput = addInput(searchX, firstRowY + navigationRows * 34 + 4, searchWidth,
                "Search entries", 64, "");
        addActionButton(searchX + searchWidth + 8, firstRowY + navigationRows * 34 + 3,
                64, "Search", this::requestSection);

        if (this.minecraft.player != null) {
            requestSection();
        } else {
            content = "Join a server to load moderation data.";
            contentWidget.setMessage(Component.literal(content));
        }



        if (selectedSection.equals("Players")) {
            if (this.height >= 400) {
                int formY = contentY + 44;
                int fieldWidth = Math.max(120, Math.min(220, (this.width - 70) / 2));
                int notesStartX = (this.width - (fieldWidth * 2 + 8)) / 2;
                notePlayerInput = addInput(notesStartX, formY, fieldWidth, "Player", 16, "");
                noteTextInput = addInput(notesStartX + fieldWidth + 8, formY,
                        fieldWidth, "Profile notes", 2000, "");
                addActionButton(this.width / 2 - 70, formY + 28, 140, "Save Notes", this::saveNotes);
            } else {
                contentWidget.setMessage(Component.literal("Increase the game window height to edit player notes."));
            }
        }

        if (selectedSection.equals("Ranks")) {
            if (this.height >= 400) {
                int formY = contentY + 42;
                int gapX = 6;
                int fieldWidth = Math.max(64, Math.min(150, (this.width - 60 - gapX * 3) / 4));
                int rowWidth = fieldWidth * 4 + gapX * 3;
                int x = (this.width - rowWidth) / 2;
                rankNameInput = addInput(x, formY, fieldWidth, "Rank name", 32, "");
                rankPriorityInput = addInput(x + fieldWidth + gapX, formY,
                        fieldWidth, "Priority", 10, "0");
                rankPrefixInput = addInput(x + (fieldWidth + gapX) * 2, formY,
                        fieldWidth, "Prefix (- = none)", 64, "-");
                rankColorInput = addInput(x + (fieldWidth + gapX) * 3, formY,
                        fieldWidth, "Color", 24, "white");

                int secondY = formY + 25;
                rankPlayerInput = addInput(x, secondY, fieldWidth * 2 + gapX, "Player", 16, "");
                rankParentInput = addInput(x + (fieldWidth + gapX) * 2, secondY,
                        fieldWidth * 2 + gapX, "Parent rank (none = clear)", 32, "none");

                int actionY = secondY + 27;
                int actionWidth = Math.max(64, Math.min(100, (this.width - 60 - gapX * 3) / 4));
                int actionGap = 6;
                int actionsWidth = actionWidth * 4 + actionGap * 3;
                int actionsX = (this.width - actionsWidth) / 2;
                addActionButton(actionsX, actionY, actionWidth, "Create", this::createRank);
                addActionButton(actionsX + actionWidth + actionGap, actionY,
                        actionWidth, "Edit", this::editRank);
                addActionButton(actionsX + (actionWidth + actionGap) * 2, actionY,
                        actionWidth, "Delete", this::deleteRank);
                addActionButton(actionsX + (actionWidth + actionGap) * 3, actionY,
                        actionWidth, "Assign", this::assignRank);
                addActionButton(actionsX, actionY + 28, actionWidth, "Unassign", this::unassignRank);
                addActionButton(actionsX + actionWidth + actionGap, actionY + 28,
                        actionWidth * 2 + actionGap, "Set Parent", this::setRankParent);
            } else {
                contentWidget.setMessage(Component.literal("Increase the game window height to manage ranks."));
            }
        }

        if (selectedSection.equals("Permissions")) {
            if (this.height >= 400) {
                int formY = contentY + 44;
                int gapX = 8;
                int fieldWidth = Math.max(80, Math.min(180, (this.width - 60 - gapX * 2) / 3));
                int rowWidth = fieldWidth * 3 + gapX * 2;
                int x = (this.width - rowWidth) / 2;
                permissionTargetTypeInput = addInput(x, formY, fieldWidth, "Target type: rank/player", 8, "rank");
                permissionTargetInput = addInput(x + fieldWidth + gapX, formY,
                        fieldWidth, "Rank or player", 32, "");
                permissionNodeInput = addInput(x + (fieldWidth + gapX) * 2, formY,
                        fieldWidth, "Permission node", 128, "");
                int actionY = formY + 28;
                int actionWidth = 90;
                int actionGap = 8;
                int actionsX = (this.width - (actionWidth * 3 + actionGap * 2)) / 2;
                addActionButton(actionsX, actionY, actionWidth, "Grant", () -> changePermission("grant"));
                addActionButton(actionsX + actionWidth + actionGap, actionY,
                        actionWidth, "Deny", () -> changePermission("deny"));
                addActionButton(actionsX + (actionWidth + actionGap) * 2, actionY,
                        actionWidth, "Remove", () -> changePermission("remove"));
            } else {
                contentWidget.setMessage(Component.literal("Increase the game window height to manage permissions."));
            }
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

        if (selectedSection.equals("Settings")) {
            if (this.height >= 400) {
                int formY = contentY + 48;
                int gapX = 8;
                int fieldWidth = Math.max(110, Math.min(190, (this.width - 60 - gapX) / 2));
                int rowWidth = fieldWidth * 2 + gapX;
                int x = (this.width - rowWidth) / 2;
                settingKeyInput = addInput(x, formY, fieldWidth,
                        "Setting key", 32, "enforce_mutes");
                settingValueInput = addInput(x + fieldWidth + gapX, formY,
                        fieldWidth, "Value: true/false", 5, "true");
                addActionButton(this.width / 2 - 70, formY + 28, 140,
                        "Save Setting", this::saveSetting);
            } else {
                contentWidget.setMessage(Component.literal(
                        "Increase the game window height to edit server settings."));
            }
        }

        if (selectedSection.equals("Bulk Moderation")) {
            if (this.height >= 440) {
                int formY = contentY + 44;
                int gapX = 8;
                int bulkTotalWidth = Math.min(460, this.width - 60);
                int x = (this.width - bulkTotalWidth) / 2;
                int durationWidth = Math.min(120, bulkTotalWidth / 3);
                int reasonWidth = bulkTotalWidth - durationWidth - gapX;
                bulkTargetsInput = addInput(x, formY, bulkTotalWidth,
                        "Players (comma-separated, max 25)", 450, "");
                bulkDurationInput = addInput(x, formY + 27, durationWidth,
                        "Seconds (0 = permanent)", 10, "0");
                bulkReasonInput = addInput(x + durationWidth + gapX, formY + 27,
                        reasonWidth, "Reason", 512, "");
                int bulkButtonWidth = 96;
                int buttonGap = 8;
                int bulkStartX = (this.width - (bulkButtonWidth * 3 + buttonGap * 2)) / 2;
                int actionY = formY + 55;
                addActionButton(bulkStartX, actionY, bulkButtonWidth, "Bulk Warn",
                        () -> issueBulk("warn"));
                addActionButton(bulkStartX + bulkButtonWidth + buttonGap, actionY, bulkButtonWidth, "Bulk Mute",
                        () -> issueBulk("mute"));
                addActionButton(bulkStartX + (bulkButtonWidth + buttonGap) * 2, actionY, bulkButtonWidth, "Bulk Ban",
                        () -> issueBulk("ban"));
            } else {
                contentWidget.setMessage(Component.literal(
                        "Increase the game window height to use bulk moderation."));
            }
        }

        int footerY = this.height - 28;
        contentAreaBottom = footerY;
        int footerWidth = Math.min(76, Math.max(58, (this.width - 36) / 5));
        int footerGap = 4;
        int footerStart = (this.width - (footerWidth * 5 + footerGap * 4)) / 2;
        addRenderableWidget(Button.builder(Component.literal("Previous"),
                button -> changePage(-1))
                .bounds(footerStart, footerY, footerWidth, 26).build());

        addRenderableWidget(Button.builder(Component.literal("Next"),
                button -> changePage(1))
                .bounds(footerStart + footerWidth + footerGap, footerY, footerWidth, 26).build());

        addRenderableWidget(Button.builder(Component.literal("Refresh"),
                button -> requestSection())
                .bounds(footerStart + (footerWidth + footerGap) * 2, footerY, footerWidth, 26).build());

        addRenderableWidget(Button.builder(Component.literal("Help"),
                button -> {
                    if (this.minecraft.player != null) {
                        this.minecraft.player.connection.sendCommand("moderationgui help");
                    }
                }).bounds(footerStart + (footerWidth + footerGap) * 3, footerY, footerWidth, 26).build());

        addRenderableWidget(Button.builder(Component.literal("Close"),
                button -> this.onClose())
                .bounds(footerStart + (footerWidth + footerGap) * 4, footerY, footerWidth, 26)
                .build());
    }




    private EditBox addInput(int x, int y, int width, String hint, int maxLength, String initialValue) {
        EditBox input = new EditBox(this.font, x, y, width, 20, Component.literal(hint));
        input.setHint(Component.literal(hint));
        input.setMaxLength(maxLength);
        if (initialValue != null && !initialValue.isEmpty()) input.setValue(initialValue);
        addRenderableWidget(input);
        return input;
    }

    private boolean isManagementSection() {
        return selectedSection.equals("Players") || selectedSection.equals("Ranks")
                || selectedSection.equals("Permissions") || selectedSection.equals("Punishments")
                || selectedSection.equals("Settings") || selectedSection.equals("Bulk Moderation");
    }

    private void saveNotes() {
        if (notePlayerInput == null || noteTextInput == null) return;
        String player = notePlayerInput.getValue().trim();
        String notes = sanitize(noteTextInput.getValue());
        if (player.isBlank()) {
            showMessage("Enter a player name.");
            return;
        }
        runCommand("moderationgui note " + player + " " + notes);
    }

    private void createRank() {
        if (rankNameInput == null) return;
        String name = rankNameInput.getValue().trim();
        String priority = rankPriorityInput.getValue().trim();
        String prefix = rankPrefixInput.getValue().trim();
        String color = rankColorInput.getValue().trim();
        if (name.isBlank() || color.isBlank() || !isInteger(priority)) {
            showMessage("Enter a rank name, integer priority and color.");
            return;
        }
        runCommand("moderationgui rank create " + name + " " + priority + " "
                + (prefix.isBlank() ? "-" : prefix) + " " + color);
    }

    private void editRank() {
        if (rankNameInput == null) return;
        String name = rankNameInput.getValue().trim();
        String priority = rankPriorityInput.getValue().trim();
        String prefix = rankPrefixInput.getValue().trim();
        String color = rankColorInput.getValue().trim();
        if (name.isBlank() || color.isBlank() || !isInteger(priority)) {
            showMessage("Enter a rank name, integer priority and color.");
            return;
        }
        runCommand("moderationgui rank edit " + name + " " + priority + " "
                + (prefix.isBlank() ? "-" : prefix) + " " + color);
    }

    private void deleteRank() {
        String name = rankNameInput == null ? "" : rankNameInput.getValue().trim();
        if (name.isBlank()) {
            showMessage("Enter a rank name.");
            return;
        }
        runCommand("moderationgui rank delete " + name);
    }

    private void assignRank() {
        String player = rankPlayerInput == null ? "" : rankPlayerInput.getValue().trim();
        String rank = rankNameInput == null ? "" : rankNameInput.getValue().trim();
        if (player.isBlank() || rank.isBlank()) {
            showMessage("Enter both player and rank.");
            return;
        }
        runCommand("moderationgui rank assign " + player + " " + rank);
    }

    private void unassignRank() {
        String player = rankPlayerInput == null ? "" : rankPlayerInput.getValue().trim();
        String rank = rankNameInput == null ? "" : rankNameInput.getValue().trim();
        if (player.isBlank() || rank.isBlank()) {
            showMessage("Enter both player and rank.");
            return;
        }
        runCommand("moderationgui rank unassign " + player + " " + rank);
    }

    private void setRankParent() {
        String child = rankNameInput == null ? "" : rankNameInput.getValue().trim();
        String parent = rankParentInput == null ? "" : rankParentInput.getValue().trim();
        if (child.isBlank() || parent.isBlank()) {
            showMessage("Enter a child rank and parent rank (or none).");
            return;
        }
        runCommand("moderationgui rank parent " + child + " " + parent);
    }

    private void changePermission(String action) {
        if (permissionTargetTypeInput == null || permissionTargetInput == null || permissionNodeInput == null) return;
        String type = permissionTargetTypeInput.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        String target = permissionTargetInput.getValue().trim();
        String node = permissionNodeInput.getValue().trim();
        if ((!type.equals("rank") && !type.equals("player")) || target.isBlank() || node.isBlank()) {
            showMessage("Target type must be rank or player; enter target and node.");
            return;
        }
        runCommand("moderationgui permission " + type + " " + target + " " + node + " " + action);
    }

    private void saveSetting() {
        if (settingKeyInput == null || settingValueInput == null) return;
        String key = settingKeyInput.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        String value = settingValueInput.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        if ((!key.equals("enforce_bans") && !key.equals("enforce_mutes"))
                || (!value.equals("true") && !value.equals("false"))) {
            showMessage("Use enforce_bans or enforce_mutes and value true or false.");
            return;
        }
        runCommand("moderationgui settings set " + key + " " + value);
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void showMessage(String message) {
        setContent(message);
    }

    private void setContent(String value) {
        content = value == null ? "No data received." : value;
        contentLines = Arrays.asList(content.split("\\R", -1));
        if (contentLines.isEmpty()) contentLines = List.of("No entries found.");
        contentPage = 0;
        renderContentPage();
    }

    private void changePage(int direction) {
        int pageSize = isManagementSection() ? 2 : 7;
        int pageCount = Math.max(1, (contentLines.size() + pageSize - 1) / pageSize);
        contentPage = Math.max(0, Math.min(pageCount - 1, contentPage + direction));
        renderContentPage();
    }

    private void renderContentPage() {
        if (contentWidget == null) return;
        int pageSize = isManagementSection() ? 2 : 7;
        int pageCount = Math.max(1, (contentLines.size() + pageSize - 1) / pageSize);
        int start = contentPage * pageSize;
        int end = Math.min(contentLines.size(), start + pageSize);
        String pageText = String.join("\n", contentLines.subList(start, end));
        contentWidget.setMessage(Component.literal(pageText + "\nPage "
                + (contentPage + 1) + "/" + pageCount));
    }

    private void addActionButton(int x, int y, int width, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> action.run())
                .bounds(x, y, width, 22)
                .build());
    }

    private void issueBulk(String type) {
        if (bulkTargetsInput == null || bulkDurationInput == null || bulkReasonInput == null) return;
        String rawTargets = bulkTargetsInput.getValue().trim();
        String reason = sanitize(bulkReasonInput.getValue());
        if (rawTargets.isBlank() || reason.isBlank()) {
            showMessage("Enter players and a reason.");
            return;
        }
        String[] targets = rawTargets.split(",", -1);
        if (targets.length < 1 || targets.length > 25) {
            showMessage("Bulk moderation supports 1 to 25 players.");
            return;
        }
        java.util.Set<String> unique = new java.util.HashSet<>();
        for (String target : targets) {
            String name = target.trim();
            if (!name.matches("[A-Za-z0-9_]{1,16}")
                    || !unique.add(name.toLowerCase(java.util.Locale.ROOT))) {
                showMessage("Check player names: names must be valid and unique.");
                return;
            }
        }
        long seconds;
        try {
            seconds = Long.parseLong(bulkDurationInput.getValue().trim());
            if (seconds < 0 || seconds > 31536000L || (type.equals("warn") && seconds != 0)) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            showMessage("Duration must be 0 to 31536000 seconds; WARN requires 0.");
            return;
        }
        String names = java.util.Arrays.stream(targets).map(String::trim)
                .collect(java.util.stream.Collectors.joining(","));
        runCommand("moderationgui bulk " + type + " " + seconds + " " + names + " " + reason);
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0 || mouseY < contentAreaTop || mouseY >= contentAreaBottom) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        changePage(verticalAmount < 0 ? 1 : -1);
        return true;
    }

    private void requestSection() {
        if (this.minecraft.player != null) {
            setContent("Refreshing data from server...");
            String filter = searchInput == null ? "" : searchInput.getValue().trim();
            ClientPlayNetworking.send(new RequestSectionPayload(selectedSection, filter));
        }
    }

    private void runCommand(String command) {
        if (this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand(command);
            requestSection();
        }
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    public static void receiveData(String section, String content) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() instanceof ModerationScreen screen
                && screen.selectedSection.equals(section)) {
            screen.setContent(content);
        }
    }

    @Override
    public Component getTitle() {
        return Component.literal("Moderation GUI | " + selectedSection);
    }
}
