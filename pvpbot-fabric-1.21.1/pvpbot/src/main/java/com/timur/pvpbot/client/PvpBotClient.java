package com.timur.pvpbot.client;

import com.timur.pvpbot.BotCommandPayload;
import com.timur.pvpbot.BotListPayload;
import com.timur.pvpbot.PvpBotMod;
import com.timur.pvpbot.entity.BotEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

public class PvpBotClient implements ClientModInitializer {
    public static KeyBinding OPEN_KEY;
    public static KeyBinding QUICK_KEY;
    public static KeyBinding AUTOPILOT_KEY;
    public static KeyBinding SELECT_KEY;

    @Override
    public void onInitializeClient() {
        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpbot.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.pvpbot"));
        QUICK_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpbot.quick", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.pvpbot"));
        AUTOPILOT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpbot.autopilot", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.pvpbot"));
        SELECT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpbot.select", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.pvpbot"));

        EntityRendererRegistry.register(PvpBotMod.BOT, BotRenderer::new);

        // список ботов от сервера
        ClientPlayNetworking.registerGlobalReceiver(BotListPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    BotListScreen.LIST = new ArrayList<>(payload.bots());
                    if (context.client().currentScreen instanceof BotListScreen s) {
                        s.refresh();
                    }
                }));

        // автопилот должен выставить клавиши до того, как игрок их прочитает
        ClientTickEvents.START_CLIENT_TICK.register(ClientAutopilot::tick);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_KEY.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(new BotScreen());
                }
            }
            while (QUICK_KEY.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    ClientPlayNetworking.send(BotScreen.spawnPayload());
                }
            }
            while (AUTOPILOT_KEY.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    ClientAutopilot.toggle(client);
                }
            }
            while (SELECT_KEY.wasPressed()) {
                if (client.player != null && client.currentScreen == null
                        && client.targetedEntity instanceof BotEntity bot) {
                    ClientPlayNetworking.send(new BotCommandPayload(BotCommandPayload.SELECT_TOGGLE, bot.getId(), ""));
                }
            }
        });
    }
}
