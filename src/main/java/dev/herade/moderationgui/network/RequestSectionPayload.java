package dev.herade.moderationgui.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Клиентский запрос данных одного раздела интерфейса. */
public record RequestSectionPayload(String section) implements CustomPacketPayload {
    public static final Type<RequestSectionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("moderation_gui", "request_section"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSectionPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    RequestSectionPayload::section,
                    RequestSectionPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
