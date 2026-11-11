package dev.herade.moderationgui.client;

import dev.herade.moderationgui.client.screen.ModerationScreen;
import dev.herade.moderationgui.network.SectionDataPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Принимает только подготовленные сервером данные для текущей вкладки. */
public final class ModerationClientNetworking {
    private ModerationClientNetworking() { }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SectionDataPayload.TYPE,
                (payload, context) -> ModerationScreen.receiveData(
                        payload.section(), payload.content()));
    }
}
