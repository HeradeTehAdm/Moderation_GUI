package dev.herade.moderationgui.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Ответ сервера с подготовленным текстовым содержимым раздела. */
public record SectionDataPayload(String section, String content) implements CustomPacketPayload {
    public static final Type<SectionDataPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("moderation_gui", "section_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SectionDataPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    SectionDataPayload::section,
                    ByteBufCodecs.STRING_UTF8,
                    SectionDataPayload::content,
                    SectionDataPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
