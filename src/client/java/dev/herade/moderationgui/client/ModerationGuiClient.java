package dev.herade.moderationgui.client;

import dev.herade.moderationgui.client.screen.ModerationScreen;
import dev.herade.moderationgui.network.ModerationClientNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

/** Регистрирует клиентскую клавишу открытия интерфейса модерации. */
public final class ModerationGuiClient implements ClientModInitializer {
    private static KeyMapping openMenu;

    @Override
    public void onInitializeClient() {
        ModerationClientNetworking.register();
        openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.moderation_gui.open",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_O,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenu.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new ModerationScreen());
                }
            }
        });
    }
}
