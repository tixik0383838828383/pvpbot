package com.timur.pvpbot;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер. action: 0 = спавн, 1 = удалить всех ботов игрока. */
public record BotRequestPayload(int action, int skill, int armor, int weapon, int flags) implements CustomPayload {
    public static final int SPAWN = 0;
    public static final int CLEAR = 1;

    public static final CustomPayload.Id<BotRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of(PvpBotMod.MOD_ID, "bot_request"));

    public static final PacketCodec<RegistryByteBuf, BotRequestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, BotRequestPayload::action,
            PacketCodecs.VAR_INT, BotRequestPayload::skill,
            PacketCodecs.VAR_INT, BotRequestPayload::armor,
            PacketCodecs.VAR_INT, BotRequestPayload::weapon,
            PacketCodecs.VAR_INT, BotRequestPayload::flags,
            BotRequestPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
