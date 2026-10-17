package com.timur.pvpbot.client;

import com.timur.pvpbot.SkillProfile;
import com.timur.pvpbot.entity.BotEntity;
import net.minecraft.block.Block;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;

/**
 * Режим «стать ботом»: игрок сам (на клиенте) дерётся как PvP-бот — наводится, бегает, стрейфится,
 * прыгает на криты, бьёт по кулдауну, ест золотые яблоки, поднимает щит, строит мосты и столбы.
 * Целями выбираются только PvP-боты и враждебные мобы — другие игроки никогда.
 * Управление идёт через обычные клавиши и обычные пакеты атаки, как у живого игрока.
 */
public final class ClientAutopilot {
    private static boolean active = false;
    private static final Random RND = new Random();

    private static float noiseYaw, noisePitch;
    private static int noiseTimer = 0;
    private static int strafeDir = 1, strafeTimer = 0;
    private static float nextReady = 0.9f;
    private static boolean wantCrit = false;
    private static int eatTicks = 0, eatCd = 0;
    private static int placeCd = 0;
    private static int msgTimer = 0;

    private ClientAutopilot() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void toggle(MinecraftClient client) {
        active = !active;
        if (!active) {
            releaseAll(client);
        }
        eatTicks = 0;
        if (client.player != null) {
            client.player.sendMessage(Text.literal(active
                    ? "§aАвтопилот включён: ты дерёшься как бот (клавиша ещё раз — выключить)"
                    : "§7Автопилот выключен"), true);
        }
    }

    private static void releaseAll(MinecraftClient client) {
        GameOptions o = client.options;
        o.forwardKey.setPressed(false);
        o.backKey.setPressed(false);
        o.leftKey.setPressed(false);
        o.rightKey.setPressed(false);
        o.jumpKey.setPressed(false);
        o.sprintKey.setPressed(false);
        o.useKey.setPressed(false);
    }

    /** Вызывается в начале каждого клиентского тика — до чтения клавиш игроком. */
    public static void tick(MinecraftClient client) {
        if (!active) {
            return;
        }
        ClientPlayerEntity p = client.player;
        if (p == null || client.world == null || client.interactionManager == null) {
            return;
        }
        if (!p.isAlive()) {
            active = false;
            releaseAll(client);
            return;
        }
        if (client.currentScreen != null) {
            releaseAll(client);
            return;
        }
        releaseAll(client);
        if (eatCd > 0) eatCd--;
        if (placeCd > 0) placeCd--;
        if (++msgTimer % 40 == 0) {
            p.sendMessage(Text.literal("§aАвтопилот: скилл " + BotScreen.skill()), true);
        }

        SkillProfile sk = SkillProfile.of(BotScreen.skill());
        GameOptions o = client.options;

        if (--noiseTimer <= 0) {
            noiseTimer = sk.noiseInterval + RND.nextInt(3);
            noiseYaw = (float) (RND.nextGaussian() * sk.aimError);
            noisePitch = (float) (RND.nextGaussian() * sk.aimError * 0.6);
        }
        if (--strafeTimer <= 0) {
            strafeTimer = 12 + RND.nextInt(30);
            strafeDir = RND.nextBoolean() ? 1 : -1;
        }

        LivingEntity tgt = findTarget(client, p);
        if (tgt == null) {
            return;
        }

        double hd = Math.hypot(tgt.getX() - p.getX(), tgt.getZ() - p.getZ());
        double dist = p.distanceTo(tgt);
        boolean ground = p.isOnGround();

        // ---------- еда ----------
        int apple = hotbarSlot(p, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);
        if (eatTicks > 0) {
            eatTicks--;
            aim(p, tgt, sk, true);
            o.useKey.setPressed(true);
            if (hd < 6.0) {
                o.backKey.setPressed(true);
            }
            if (!p.isUsingItem() && eatTicks < 28) {
                eatTicks = 0;
                eatCd = 160;
            }
            return;
        }
        if (apple >= 0 && eatCd <= 0 && p.getHealth() <= sk.gappleHp) {
            p.getInventory().selectedSlot = apple;
            eatTicks = 36;
            return;
        }

        // ---------- стройка ----------
        if (build(client, p, tgt, hd, sk)) {
            return;
        }

        // ---------- оружие ----------
        selectWeapon(p, p.fallDistance > 2.5f);

        // ---------- прицел ----------
        aim(p, tgt, sk, true);

        // ---------- движение ----------
        if (hd > 3.0) {
            o.forwardKey.setPressed(true);
            if (sk.level >= 2 || hd > 5.0) {
                o.sprintKey.setPressed(true);
            }
        } else if (hd < 1.4) {
            o.backKey.setPressed(true);
        }
        if (hd < 6.0 && sk.strafe > 0.2) {
            (strafeDir > 0 ? o.rightKey : o.leftKey).setPressed(true);
        }
        if (p.horizontalCollision && ground) {
            o.jumpKey.setPressed(true);
        }

        // крит-прыжок: прыгаем рядом с целью, бьём на спуске
        boolean mace = p.getMainHandStack().isOf(Items.MACE);
        float cd = p.getAttackCooldownProgress(0.5f);
        if (wantCrit && !mace && ground && hd < 3.6 && cd > 0.55f) {
            o.jumpKey.setPressed(true);
        }
        if (sk.level >= 5 && hd > 4.5 && ground && !wantCrit && RND.nextInt(12) == 0) {
            o.jumpKey.setPressed(true);
        }
        boolean airborne = !ground;
        if (airborne && wantCrit) {
            o.sprintKey.setPressed(false);
        }

        // ---------- атака ----------
        boolean critWindow = !wantCrit || mace || (airborne && p.fallDistance > 0.0f);
        boolean onCrosshair = client.targetedEntity == tgt;
        if (onCrosshair && dist < 3.3 && cd >= nextReady && critWindow) {
            client.interactionManager.attackEntity(p, tgt);
            p.swingHand(Hand.MAIN_HAND);
            wantCrit = RND.nextDouble() < sk.critChance;
            nextReady = sk.readyMin + RND.nextFloat() * (1.0f - sk.readyMin) * 0.5f;
        }

        // ---------- щит ----------
        if (sk.level >= 3 && p.getOffHandStack().isOf(Items.SHIELD) && dist < 5.5 && cd < nextReady - 0.04f) {
            o.useKey.setPressed(true);
        }
    }

    // =====================================================================

    private static LivingEntity findTarget(MinecraftClient client, ClientPlayerEntity p) {
        List<LivingEntity> list = client.world.getEntitiesByClass(LivingEntity.class,
                p.getBoundingBox().expand(24.0),
                e -> e != p && e.isAlive() && (e instanceof BotEntity || e instanceof HostileEntity));
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            double d = e.squaredDistanceTo(p);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static void aim(ClientPlayerEntity p, LivingEntity tgt, SkillProfile sk, boolean noisy) {
        Vec3d e = p.getEyePos();
        Vec3d c = tgt.getPos().add(0, tgt.getHeight() * 0.62, 0);
        double dx = c.x - e.x, dy = c.y - e.y, dz = c.z - e.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * 57.2957795) - 90.0f;
        float pitch = (float) (-(MathHelper.atan2(dy, h) * 57.2957795));
        if (noisy) {
            yaw += noiseYaw;
            pitch += noisePitch;
        }
        float dyaw = MathHelper.wrapDegrees(yaw - p.getYaw());
        float dpitch = MathHelper.clamp(pitch, -90.0f, 90.0f) - p.getPitch();
        float k = 0.35f + (float) sk.t * 0.5f;
        float sy = MathHelper.clamp(dyaw * k, -sk.turnSpeed, sk.turnSpeed);
        float sp = MathHelper.clamp(dpitch * k, -sk.turnSpeed, sk.turnSpeed);
        p.setYaw(p.getYaw() + sy);
        p.setPitch(MathHelper.clamp(p.getPitch() + sp, -90.0f, 90.0f));
    }

    // ---------- инвентарь ----------

    private static int hotbarSlot(ClientPlayerEntity p, net.minecraft.item.Item... items) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            for (net.minecraft.item.Item it : items) {
                if (s.isOf(it)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static void selectWeapon(ClientPlayerEntity p, boolean falling) {
        int sword = -1, axe = -1, mace = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isIn(ItemTags.SWORDS) && sword < 0) sword = i;
            else if (s.isIn(ItemTags.AXES) && axe < 0) axe = i;
            else if (s.isOf(Items.MACE) && mace < 0) mace = i;
        }
        int pick = falling && mace >= 0 ? mace : sword >= 0 ? sword : axe >= 0 ? axe : mace;
        if (pick >= 0) {
            p.getInventory().selectedSlot = pick;
        }
    }

    private static int blockSlot(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.getItem() instanceof BlockItem bi) {
                Block b = bi.getBlock();
                if (!(b instanceof FallingBlock) && !(b instanceof TntBlock) && b != net.minecraft.block.Blocks.COBWEB) {
                    return i;
                }
            }
        }
        return -1;
    }

    // ---------- стройка: мосты и столбы ----------

    private static boolean build(MinecraftClient client, ClientPlayerEntity p, LivingEntity tgt, double hd, SkillProfile sk) {
        if (sk.level < 3 || placeCd > 0) {
            return false;
        }
        int slot = blockSlot(p);
        if (slot < 0) {
            return false;
        }
        GameOptions o = client.options;
        double dy = tgt.getY() - p.getY();

        // столб вверх к более высокой цели
        if (dy >= 2.5 && hd < 6.0) {
            aim(p, tgt, sk, false);
            if (p.isOnGround()) {
                o.jumpKey.setPressed(true);
                return true;
            }
            if (p.getVelocity().y < 0.0) {
                BlockPos target = p.getBlockPos().down();
                BlockPos support = target.down();
                if (client.world.getBlockState(target).isReplaceable() && !client.world.getBlockState(support).isReplaceable()) {
                    p.getInventory().selectedSlot = slot;
                    placeAgainst(client, p, support, Direction.UP);
                    placeCd = 1;
                }
            }
            return true;
        }

        // мост через пропасть к цели на похожей высоте
        if (p.isOnGround() && hd > 3.0 && dy >= -2.5) {
            Vec3d dir = new Vec3d(tgt.getX() - p.getX(), 0, tgt.getZ() - p.getZ()).normalize();
            BlockPos ahead = BlockPos.ofFloored(p.getX() + dir.x * 0.9, p.getY() - 0.5, p.getZ() + dir.z * 0.9);
            if (client.world.getBlockState(ahead).isReplaceable() && client.world.getBlockState(ahead.down()).isReplaceable()) {
                BlockPos support = p.getBlockPos().down();
                Direction face = Direction.getFacing(dir.x, 0, dir.z);
                if (!client.world.getBlockState(support).isReplaceable() && support.offset(face).equals(ahead)) {
                    p.getInventory().selectedSlot = slot;
                    aim(p, tgt, sk, false);
                    o.forwardKey.setPressed(true);
                    placeAgainst(client, p, support, face);
                    placeCd = 3;
                    return false;
                }
            }
        }
        return false;
    }

    private static void placeAgainst(MinecraftClient client, ClientPlayerEntity p, BlockPos support, Direction face) {
        Vec3d hit = Vec3d.ofCenter(support).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
        client.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hit, face, support, false));
        p.swingHand(Hand.MAIN_HAND);
    }
}
