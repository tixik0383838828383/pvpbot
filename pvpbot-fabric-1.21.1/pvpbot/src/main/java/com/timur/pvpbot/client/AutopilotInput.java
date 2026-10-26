package com.timur.pvpbot.client;

import net.minecraft.client.input.Input;

/**
 * Собственный «ввод» игрока для автопилота. Подставляется вместо клавиатурного ввода,
 * поэтому автопилот двигает игрока сам и не трогает ни одну клавишу пользователя.
 * Значения выставляются в начале клиентского тика, а игрок читает их в своём тике.
 */
final class AutopilotInput extends Input {
    private float wantForward;
    private float wantSideways;
    private boolean wantJump;

    /** Сбросить все команды (игрок стоит на месте). */
    void clear() {
        wantForward = 0.0f;
        wantSideways = 0.0f;
        wantJump = false;
    }

    /** forward: +1 вперёд, -1 назад. sideways: +1 влево, -1 вправо (как в самой игре). */
    void setMove(float forward, float sideways) {
        wantForward = forward;
        wantSideways = sideways;
    }

    void setJump(boolean jump) {
        wantJump = jump;
    }

    float getForward() {
        return wantForward;
    }

    @Override
    public void tick(boolean slowDown, float slowDownFactor) {
        float f = wantForward;
        float s = wantSideways;
        if (slowDown) {
            f *= slowDownFactor;
            s *= slowDownFactor;
        }
        this.movementForward = f;
        this.movementSideways = s;
        this.pressingForward = wantForward > 0.0f;
        this.pressingBack = wantForward < 0.0f;
        this.pressingLeft = wantSideways > 0.0f;
        this.pressingRight = wantSideways < 0.0f;
        this.jumping = wantJump;
        this.sneaking = false;
    }
}
