package com.timur.pvpbot.client;

import com.timur.pvpbot.BotCommandPayload;
import com.timur.pvpbot.BotListPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Список ботов игрока: выбор, стравливание между собой, режимы, смена скина выбранным. */
public class BotListScreen extends Screen {
    /** Последний список, присланный сервером. */
    public static List<BotListPayload.Info> LIST = new ArrayList<>();

    private TextFieldWidget skinField;
    private static String skinText = "";

    public BotListScreen() {
        super(Text.literal("Мои боты"));
    }

    /** Вызывается, когда пришёл свежий список от сервера. */
    public void refresh() {
        this.clearAndInit();
    }

    private static void send(int cmd, int id, String text) {
        ClientPlayNetworking.send(new BotCommandPayload(cmd, id, text));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = Math.max(8, this.height / 2 - 118);
        int left = cx - 155;
        int right = cx + 3;

        // список ботов в два столбца по 5
        for (int i = 0; i < LIST.size() && i < 10; i++) {
            BotListPayload.Info b = LIST.get(i);
            int x = i < 5 ? left : right;
            int y = top + 16 + (i % 5) * 22;
            this.addDrawableChild(ButtonWidget.builder(rowText(b), btn -> send(BotCommandPayload.SELECT_TOGGLE, b.id(), ""))
                    .dimensions(x, y, 152, 20).build());
        }

        int w = 100;
        int x1 = left, x2 = left + 105, x3 = left + 210;
        int r1 = top + 132, r2 = top + 154, r3 = top + 176, r4 = top + 200;

        addCmd("Выбрать всех", BotCommandPayload.SELECT_ALL, x1, r1, w, null);
        addCmd("Снять выбор", BotCommandPayload.SELECT_NONE, x2, r1, w, null);
        addCmd("Удалить выбр.", BotCommandPayload.REMOVE, x3, r1, w, Formatting.RED);

        addCmd("Все против всех", BotCommandPayload.FIGHT_FFA, x1, r2, w, Formatting.GOLD);
        addCmd("Две команды", BotCommandPayload.FIGHT_TEAMS, x2, r2, w, Formatting.GOLD);
        addCmd("Мир (трен.)", BotCommandPayload.PEACE, x3, r2, w, null);

        addCmd("Охранять меня", BotCommandPayload.GUARD, x1, r3, w, Formatting.GREEN);
        addCmd("Стоять", BotCommandPayload.IDLE, x2, r3, w, null);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> this.client.setScreen(new BotScreen()))
                .dimensions(x3, r3, w, 20).build());

        skinField = new TextFieldWidget(this.textRenderer, left, r4, 205, 20, Text.literal("Скин"));
        skinField.setMaxLength(48);
        skinField.setText(skinText);
        skinField.setPlaceholder(Text.literal("Ник или файл.png"));
        skinField.setChangedListener(s -> skinText = s.trim());
        this.addDrawableChild(skinField);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Скин выбранным"),
                b -> send(BotCommandPayload.SKIN, 0, skinText)).dimensions(x3, r4, w, 20).build());
    }

    private void addCmd(String label, int cmd, int x, int y, int w, Formatting color) {
        Text t = color == null ? Text.literal(label) : Text.literal(label).formatted(color);
        this.addDrawableChild(ButtonWidget.builder(t, b -> send(cmd, 0, "")).dimensions(x, y, w, 20).build());
    }

    private static Text rowText(BotListPayload.Info b) {
        String mark = b.selected() ? "[x] " : "[ ] ";
        String mode = switch (b.mode()) {
            case BotListPayload.MODE_FIGHT -> "бой";
            case BotListPayload.MODE_GUARD -> "охрана";
            case BotListPayload.MODE_IDLE -> "стоит";
            default -> "трен.";
        };
        Formatting color = b.team() == 1 ? Formatting.RED : b.team() == 2 ? Formatting.AQUA : Formatting.WHITE;
        String name = b.name().length() > 14 ? b.name().substring(0, 14) : b.name();
        return Text.literal(mark + name + " " + b.hp() + "hp " + mode).formatted(color);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int top = Math.max(8, this.height / 2 - 118);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, top, 0xFFFFFF);
        if (LIST.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Ботов пока нет — заспавни в меню."),
                    cx, top + 60, 0xAAAAAA);
        }
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Клик по строке — выбрать бота (он светится). Можно и прицелиться на бота и нажать V."),
                cx, top + 226, 0x888888);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Бой: выбери 2+ ботов -> «Все против всех» или «Две команды» (красные / синие)."),
                cx, top + 237, 0x888888);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
