package dev.herade.moderationgui.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Регистрирует типы пакетов на обеих сторонах до регистрации обработчиков. */
public final class ModerationNetworking {
    private ModerationNetworking() { }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.serverboundPlay().register(
                RequestSectionPayload.TYPE, RequestSectionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                SectionDataPayload.TYPE, SectionDataPayload.CODEC);
    }
}
