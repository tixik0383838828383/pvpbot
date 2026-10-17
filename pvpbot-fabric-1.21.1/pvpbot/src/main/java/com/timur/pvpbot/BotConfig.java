package com.timur.pvpbot;

import net.minecraft.util.math.MathHelper;

/** Настройки бота, которые выбираются в GUI и передаются на сервер. */
public record BotConfig(int skill, int armor, int weapon, int flags) {
    public static final int SHIELD = 1;
    public static final int BOW = 2;
    public static final int WEB = 4;
    public static final int WATER = 8;
    public static final int PEARL = 16;
    public static final int APPLE = 32;
    public static final int RESPAWN = 64;

    public static final int W_SWORD = 0;
    public static final int W_AXE = 1;
    public static final int W_MACE = 2;

    public static final int ALL_DEFAULT_FLAGS = SHIELD | BOW | WEB | WATER | PEARL | APPLE;

    public BotConfig {
        skill = MathHelper.clamp(skill, 1, 10);
        armor = MathHelper.clamp(armor, 0, 4);
        weapon = MathHelper.clamp(weapon, 0, 2);
        flags = flags & 0x7F;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }
}
