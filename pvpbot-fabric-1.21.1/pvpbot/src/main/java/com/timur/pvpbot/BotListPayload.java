package com.timur.pvpbot;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/** Сервер -> клиент: список ботов владельца для экрана выбора. */
public record BotListPayload(List<Info> bots) implements CustomPayload {
    public static final int MODE_TRAIN = 0;
    public static final int MODE_FIGHT = 1;
    public static final int MODE_GUARD = 2;
    public static final int MODE_IDLE = 3;

    /** state: биты 0..2 — режим, бит 3 — выбран. team: 0 — без команды, 1 — красные, 2 — синие. */
    public record Info(int id, String name, int hp, int team, int state) {
        public static final PacketCodec<RegistryByteBuf, Info> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Info::id,
                PacketCodecs.string(96), Info::name,
                PacketCodecs.VAR_INT, Info::hp,
                PacketCodecs.VAR_INT, Info::team,
                PacketCodecs.VAR_INT, Info::state,
                Info::new);

        public int mode() {
            return state & 7;
        }

        public boolean selected() {
            return (state & 8) != 0;
        }
    }

    public static final CustomPayload.Id<BotListPayload> ID =
            new CustomPayload.Id<>(Identifier.of(PvpBotMod.MOD_ID, "bot_list"));

    public static final PacketCodec<RegistryByteBuf, BotListPayload> CODEC = PacketCodec.tuple(
            Info.CODEC.collect(PacketCodecs.toList()), BotListPayload::bots,
            BotListPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
