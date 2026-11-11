package dev.herade.moderationgui.client;

import dev.herade.moderationgui.client.screen.ModerationScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/** Регистрирует клиентскую клавишу открытия интерфейса модерации. */
public final class ModerationGuiClient implements ClientModInitializer {
    private static KeyMapping openMenu;

    @Override
    public void onInitializeClient() {
        openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.moderation_gui.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenu.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new ModerationScreen());
                }
            }
        });
    }
}
