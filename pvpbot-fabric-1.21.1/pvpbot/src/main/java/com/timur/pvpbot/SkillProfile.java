package com.timur.pvpbot;

import net.minecraft.util.math.MathHelper;

/** Все «умения» бота выводятся из одного числа — уровня скилла 1..10. */
public final class SkillProfile {
    public final int level;
    public final double t;               // 0..1
    public final int reaction;           // задержка реакции/смены предмета, тики
    public final float turnSpeed;        // макс. скорость поворота, градусов/тик
    public final float aimError;         // сигма разброса прицела, градусы
    public final int noiseInterval;      // как часто перебрасывается разброс, тики
    public final float readyMin;         // с какой готовности кулдауна бот бьёт
    public final double critChance;      // шанс делать крит-прыжок
    public final double strafe;          // интенсивность стрейфа 0..1
    public final double clutchChance;    // шанс сделать клатч при падении
    public final int bowDraw;            // сколько тиков натягивает лук
    public final double bowLead;         // качество упреждения при стрельбе
    public final double webUse;          // шанс начать ставить паутину
    public final int webCooldown;        // кулдаун паутины, тики
    public final int gappleHp;           // при каком HP ест золотое яблоко

    private SkillProfile(int lvl) {
        level = MathHelper.clamp(lvl, 1, 10);
        t = (level - 1) / 9.0;
        reaction = (int) Math.round(lerp(12, 1, t));
        turnSpeed = (float) lerp(5, 40, t);
        aimError = (float) lerp(12, 0.5, t);
        noiseInterval = (int) Math.round(lerp(14, 4, t));
        readyMin = (float) lerp(0.35, 0.96, t);
        critChance = lerp(0.0, 0.9, t);
        strafe = lerp(0.0, 1.0, t);
        clutchChance = lerp(0.3, 1.0, t);
        bowDraw = (int) Math.round(lerp(8, 20, t));
        bowLead = lerp(0.2, 1.0, t);
        webUse = lerp(0.03, 0.35, t);
        webCooldown = (int) Math.round(lerp(200, 70, t));
        gappleHp = (int) Math.round(lerp(5, 11, t));
    }

    public static SkillProfile of(int level) {
        return new SkillProfile(level);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public String rank() {
        if (level <= 2) return "Новичок";
        if (level <= 4) return "Любитель";
        if (level <= 6) return "Средний";
        if (level <= 8) return "Про";
        if (level == 9) return "Эксперт";
        return "Аимбот";
    }
}
