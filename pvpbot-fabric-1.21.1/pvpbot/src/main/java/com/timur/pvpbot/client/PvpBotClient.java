package com.timur.pvpbot.client;

import com.timur.pvpbot.PvpBotMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class PvpBotClient implements ClientModInitializer {
    public static KeyBinding OPEN_KEY;
    public static KeyBinding QUICK_KEY;

    @Override
    public void onInitializeClient() {
        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpbot.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.pvpbot"));
        QUICK_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpbot.quick", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.pvpbot"));

        EntityRendererRegistry.register(PvpBotMod.BOT, BotRenderer::new);

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
        });
    }
}
