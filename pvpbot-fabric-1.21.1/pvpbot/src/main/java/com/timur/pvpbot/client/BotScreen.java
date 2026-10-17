package com.timur.pvpbot.client;

import com.timur.pvpbot.BotCommandPayload;
import com.timur.pvpbot.BotConfig;
import com.timur.pvpbot.BotRequestPayload;
import com.timur.pvpbot.SkillProfile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

/** GUI: скилл, оружие, броня, умения, скин. Настройки запоминаются до перезапуска игры. */
public class BotScreen extends Screen {
    private static int skill = 5;
    private static int armor = 3;
    private static int weapon = BotConfig.W_SWORD;
    private static int flags = BotConfig.ALL_DEFAULT_FLAGS;
    private static String skin = "";

    private static final String[] WEAPONS = {"Меч", "Топор", "Булава"};
    private static final String[] ARMORS = {"Без брони", "Железо", "Алмаз", "Незерит", "Незерит + чары"};

    private TextFieldWidget skinField;

    public BotScreen() {
        super(Text.literal("PvP-тренер"));
    }

    public static int skill() {
        return skill;
    }

    public static String skin() {
        return skin;
    }

    public static BotRequestPayload spawnPayload() {
        return new BotRequestPayload(BotRequestPayload.SPAWN, skill, armor, weapon, flags, skin);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = Math.max(8, this.height / 2 - 128);
        int left = cx - 155;
        int right = cx + 3;

        this.addDrawableChild(new SkillSlider(left, top + 14, 310, 20));

        this.addDrawableChild(ButtonWidget.builder(weaponText(), b -> {
            weapon = (weapon + 1) % WEAPONS.length;
            b.setMessage(weaponText());
        }).dimensions(left, top + 38, 152, 20).build());

        this.addDrawableChild(ButtonWidget.builder(armorText(), b -> {
            armor = (armor + 1) % ARMORS.length;
            b.setMessage(armorText());
        }).dimensions(right, top + 38, 152, 20).build());

        addToggle("Щит", BotConfig.SHIELD, left, top + 60);
        addToggle("Лук", BotConfig.BOW, right, top + 60);
        addToggle("Паутина", BotConfig.WEB, left, top + 82);
        addToggle("Клатч: вода", BotConfig.WATER, right, top + 82);
        addToggle("Клатч: жемчуг", BotConfig.PEARL, left, top + 104);
        addToggle("Зол. яблоки", BotConfig.APPLE, right, top + 104);
        addToggle("Авто-респавн", BotConfig.RESPAWN, left, top + 126);
        addToggle("Стройка", BotConfig.BUILD, right, top + 126);
        addToggle("Спин-бот (вращение)", BotConfig.SPIN, left, top + 148);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Папка скинов"), b -> {
            Util.getOperatingSystem().open(SkinCache.ensureSkinDir());
        }).dimensions(right, top + 148, 152, 20).build());

        skinField = new TextFieldWidget(this.textRenderer, left, top + 172, 310, 20, Text.literal("Скин"));
        skinField.setMaxLength(48);
        skinField.setText(skin);
        skinField.setPlaceholder(Text.literal("Скин: ник игрока или файл.png (пусто = Стив)"));
        skinField.setChangedListener(s -> skin = s.trim());
        this.addDrawableChild(skinField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Заспавнить").formatted(Formatting.GREEN), b -> {
            ClientPlayNetworking.send(spawnPayload());
            this.close();
        }).dimensions(left, top + 196, 152, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Мои боты / бои..."), b -> {
            ClientPlayNetworking.send(new BotCommandPayload(BotCommandPayload.REQUEST_LIST, 0, ""));
            this.client.setScreen(new BotListScreen());
        }).dimensions(right, top + 196, 152, 20).build());

        this.addDrawableChild(ButtonWidget.builder(autopilotText(), b -> {
            ClientAutopilot.toggle(this.client);
            this.close();
        }).dimensions(left, top + 218, 152, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Удалить всех ботов").formatted(Formatting.RED), b -> {
            ClientPlayNetworking.send(new BotRequestPayload(BotRequestPayload.CLEAR, 1, 0, 0, 0, ""));
        }).dimensions(right, top + 218, 152, 20).build());
    }

    private static Text autopilotText() {
        return Text.literal(ClientAutopilot.isActive() ? "Стать ботом: ВЫКЛЮЧИТЬ" : "Стать ботом (автопилот)")
                .formatted(ClientAutopilot.isActive() ? Formatting.RED : Formatting.AQUA);
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
        int top = Math.max(8, this.height / 2 - 128);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, top, 0xFFFFFF);

        SkillProfile p = SkillProfile.of(skill);
        int y = top + 242;
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Реакция ~" + (p.reaction * 50) + " мс  ·  разброс прицела ±" + String.format("%.1f", p.aimError) + "°"),
                cx, y, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Поворот " + String.format("%.0f", p.turnSpeed) + "°/тик  ·  клатч " + Math.round(p.clutchChance * 100)
                        + "%  ·  крит-прыжки " + Math.round(p.critChance * 100) + "%"),
                cx, y + 11, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Макс. 10 ботов. Автопилот бьёт только ботов и мобов, не игроков."),
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
