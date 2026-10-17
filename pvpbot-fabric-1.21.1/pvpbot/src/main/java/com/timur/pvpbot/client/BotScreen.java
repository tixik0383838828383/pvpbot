package com.timur.pvpbot.client;

import com.timur.pvpbot.BotConfig;
import com.timur.pvpbot.BotRequestPayload;
import com.timur.pvpbot.SkillProfile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** GUI: скилл, оружие, броня, набор умений. Настройки запоминаются до перезапуска игры. */
public class BotScreen extends Screen {
    private static int skill = 5;
    private static int armor = 3;
    private static int weapon = BotConfig.W_SWORD;
    private static int flags = BotConfig.ALL_DEFAULT_FLAGS;

    private static final String[] WEAPONS = {"Меч", "Топор", "Булава"};
    private static final String[] ARMORS = {"Без брони", "Железо", "Алмаз", "Незерит", "Незерит + чары"};

    public BotScreen() {
        super(Text.literal("PvP-тренер"));
    }

    public static BotRequestPayload spawnPayload() {
        return new BotRequestPayload(BotRequestPayload.SPAWN, skill, armor, weapon, flags);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = Math.max(10, this.height / 2 - 106);
        int left = cx - 155;
        int right = cx + 3;

        this.addDrawableChild(new SkillSlider(left, top + 14, 310, 20));

        this.addDrawableChild(ButtonWidget.builder(weaponText(), b -> {
            weapon = (weapon + 1) % WEAPONS.length;
            b.setMessage(weaponText());
        }).dimensions(left, top + 40, 152, 20).build());

        this.addDrawableChild(ButtonWidget.builder(armorText(), b -> {
            armor = (armor + 1) % ARMORS.length;
            b.setMessage(armorText());
        }).dimensions(right, top + 40, 152, 20).build());

        addToggle("Щит", BotConfig.SHIELD, left, top + 64);
        addToggle("Лук", BotConfig.BOW, right, top + 64);
        addToggle("Паутина", BotConfig.WEB, left, top + 88);
        addToggle("Клатч: вода", BotConfig.WATER, right, top + 88);
        addToggle("Клатч: жемчуг", BotConfig.PEARL, left, top + 112);
        addToggle("Зол. яблоки", BotConfig.APPLE, right, top + 112);
        addToggle("Авто-респавн", BotConfig.RESPAWN, left, top + 136);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Заспавнить").formatted(Formatting.GREEN), b -> {
            ClientPlayNetworking.send(spawnPayload());
            this.close();
        }).dimensions(right, top + 136, 152, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Удалить всех ботов").formatted(Formatting.RED), b -> {
            ClientPlayNetworking.send(new BotRequestPayload(BotRequestPayload.CLEAR, 1, 0, 0, 0));
        }).dimensions(left, top + 160, 310, 20).build());
    }

    private void addToggle(String name, int flag, int x, int y) {
        this.addDrawableChild(ButtonWidget.builder(toggleText(name, flag), b -> {
            flags ^= flag;
            b.setMessage(toggleText(name, flag));
        }).dimensions(x, y, 152, 20).build());
    }

    private static Text toggleText(String name, int flag) {
        boolean on = (flags & flag) != 0;
        return Text.literal(name + ": ")
                .append(Text.literal(on ? "ВКЛ" : "ВЫКЛ").formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    private static Text weaponText() {
        return Text.literal("Оружие: " + WEAPONS[weapon]);
    }

    private static Text armorText() {
        return Text.literal("Броня: " + ARMORS[armor]);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int top = Math.max(10, this.height / 2 - 106);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, top, 0xFFFFFF);

        SkillProfile p = SkillProfile.of(skill);
        int y = top + 186;
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Реакция ~" + (p.reaction * 50) + " мс  ·  разброс прицела ±" + String.format("%.1f", p.aimError) + "°"),
                cx, y, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Поворот " + String.format("%.0f", p.turnSpeed) + "°/тик  ·  клатч " + Math.round(p.clutchChance * 100)
                        + "%  ·  крит-прыжки " + Math.round(p.critChance * 100) + "%"),
                cx, y + 11, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Бот бьёт тебя в режиме выживания. Макс. 6 ботов."),
                cx, y + 22, 0x777777);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static class SkillSlider extends SliderWidget {
        SkillSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Text.empty(), (skill - 1) / 9.0);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal("Скилл: " + skill + " / 10  —  " + SkillProfile.of(skill).rank()));
        }

        @Override
        protected void applyValue() {
            skill = 1 + (int) Math.round(this.value * 9.0);
            this.value = (skill - 1) / 9.0;
        }
    }
}
