package com.timur.pvpbot;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер: команды управления уже созданными ботами (выбор, конфликты, режимы, скин). */
public record BotCommandPayload(int cmd, int entityId, String text) implements CustomPayload {
    /** Переключить выбор бота с данным entityId. */
    public static final int SELECT_TOGGLE = 0;
    public static final int SELECT_ALL = 1;
    public static final int SELECT_NONE = 2;
    /** Выбранные дерутся все против всех. */
    public static final int FIGHT_FFA = 3;
    /** Выбранные делятся на две команды (красные / синие) и дерутся. */
    public static final int FIGHT_TEAMS = 4;
    /** Прекратить конфликт: выбранные снова атакуют владельца (тренировка). */
    public static final int PEACE = 5;
    /** Выбранные охраняют владельца: следуют и бьют враждебных мобов. */
    public static final int GUARD = 6;
    /** Выбранные стоят на месте и осматриваются. */
    public static final int IDLE = 7;
    /** Сменить скин выбранным (text = ник или файл.png). */
    public static final int SKIN = 8;
    /** Удалить выбранных. */
    public static final int REMOVE = 9;
    /** Просто запросить актуальный список ботов. */
    public static final int REQUEST_LIST = 10;

    public static final CustomPayload.Id<BotCommandPayload> ID =
            new CustomPayload.Id<>(Identifier.of(PvpBotMod.MOD_ID, "bot_command"));

    public static final PacketCodec<RegistryByteBuf, BotCommandPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, BotCommandPayload::cmd,
            PacketCodecs.VAR_INT, BotCommandPayload::entityId,
            PacketCodecs.string(64), BotCommandPayload::text,
            BotCommandPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
