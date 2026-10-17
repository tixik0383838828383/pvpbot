package com.timur.pvpbot;

import net.minecraft.util.math.MathHelper;

/** Настройки бота, которые выбираются в GUI и передаются на сервер. */
public record BotConfig(int skill, int armor, int weapon, int flags, String skin) {
    public static final int SHIELD = 1;
    public static final int BOW = 2;
    public static final int WEB = 4;
    public static final int WATER = 8;
    public static final int PEARL = 16;
    public static final int APPLE = 32;
    public static final int RESPAWN = 64;
    /** Бот строит: мосты, столбы вверх, башню для удара булавой. */
    public static final int BUILD = 128;
    /** Визуальное вращение модели (спин-бот / сальто). */
    public static final int SPIN = 256;

    public static final int W_SWORD = 0;
    public static final int W_AXE = 1;
    public static final int W_MACE = 2;

    public static final int ALL_DEFAULT_FLAGS = SHIELD | BOW | WEB | WATER | PEARL | APPLE | BUILD;

    public BotConfig {
        skill = MathHelper.clamp(skill, 1, 10);
        armor = MathHelper.clamp(armor, 0, 4);
        weapon = MathHelper.clamp(weapon, 0, 2);
        flags = flags & 0x1FF;
        skin = skin == null ? "" : skin.trim();
        if (skin.length() > 48) {
            skin = skin.substring(0, 48);
        }
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }
}
