package com.timur.pvpbot.mixin;

import com.timur.pvpbot.client.ClientAutopilot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Автопилоту нужно «удерживать использование предмета» (есть яблоко, держать щит), а игра прекращает
 * использование, если кнопка не зажата. Чтобы не трогать реальную клавишу пользователя, на время
 * тика автопилота мы просто отвечаем игре, что кнопка «использовать» зажата.
 */
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void pvpbot$autopilotHoldUse(CallbackInfoReturnable<Boolean> cir) {
        if (ClientAutopilot.isHoldingUse() && (Object) this == MinecraftClient.getInstance().options.useKey) {
            cir.setReturnValue(true);
        }
    }
}
