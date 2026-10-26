package com.timur.pvpbot.client;

import com.timur.pvpbot.SkillProfile;
import com.timur.pvpbot.entity.BotEntity;
import net.minecraft.block.Block;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;

/**
 * Режим «стать ботом»: игрок сам (на клиенте) дерётся как PvP-бот — наводится, бегает, стрейфится,
 * прыгает на криты, бьёт по кулдауну, ест золотые яблоки, поднимает щит, строит мосты и столбы.
 * Целями выбираются только PvP-боты и враждебные мобы — другие игроки никогда.
 *
 * <p>Клавиши пользователя автопилот НЕ нажимает и не отпускает. Вместо этого:
 * <ul>
 *   <li>движение и прыжок идут через собственный {@link AutopilotInput}, который на время автопилота
 *       подставляется в игрока вместо клавиатурного ввода (ходьба считается по направлению на цель
 *       независимо от того, куда сейчас повёрнута камера);</li>
 *   <li>спринт включается напрямую у игрока;</li>
 *   <li>удары и постройка блоков идут обычными пакетами через interactionManager;</li>
 *   <li>удержание предмета (яблоко, щит) подсказывается игре через {@code KeyBindingMixin}
 *       только на время тика — физическая клавиша «использовать» остаётся нетронутой;</li>
 *   <li>камера поворачивается в конце тика, поэтому вид плавно интерполируется между тиками.</li>
 * </ul>
 */
public final class ClientAutopilot {
    private static final double REACH = 3.0;

    private static boolean active = false;
    private static final Random RND = new Random();

    // подменённый ввод игрока
    private static AutopilotInput ours = null;
    private static Input original = null;

    // что автопилот хочет в этом тике
    private static boolean holdUse = false;
    private static boolean wantSprint = false;
    private static LivingEntity aimTarget = null;
    private static boolean aimNoisy = true;

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

    /** Нужно ли в этом тике считать кнопку «использовать» зажатой (читается из KeyBindingMixin). */
    public static boolean isHoldingUse() {
        return active && holdUse;
    }

    public static void toggle(MinecraftClient client) {
        active = !active;
        if (!active) {
            shutdown(client);
        }
        eatTicks = 0;
        if (client.player != null) {
            client.player.sendMessage(Text.literal(active
                    ? "§aАвтопилот включён: бот сам ведёт твоего игрока, клавиши не нужны (клавиша ещё раз — выключить)"
                    : "§7Автопилот выключен"), true);
        }
    }

    /** Возвращает игроку обычное управление и сбрасывает все команды автопилота. */
    private static void shutdown(MinecraftClient client) {
        holdUse = false;
        wantSprint = false;
        aimTarget = null;
        ClientPlayerEntity p = client.player;
        if (p != null) {
            if (ours != null && p.input == ours && original != null) {
                p.input = original;
            }
            if (p.isSprinting()) {
                p.setSprinting(false);
            }
        }
        ours = null;
        original = null;
    }

    /** Подставляет наш ввод игроку (и заново, если игрок был пересоздан — смерть, смена измерения). */
    private static AutopilotInput install(ClientPlayerEntity p) {
        if (ours == null) {
            ours = new AutopilotInput();
        }
        if (p.input != ours) {
            original = p.input;
            p.input = ours;
        }
        return ours;
    }

    /** Начало каждого клиентского тика — до того, как игрок прочитает ввод и выполнит свой тик. */
    public static void tick(MinecraftClient client) {
        if (!active) {
            return;
        }
        ClientPlayerEntity p = client.player;
        if (p == null || client.world == null || client.interactionManager == null || !p.isAlive()) {
            active = false;
            shutdown(client);
            return;
        }

        AutopilotInput in = install(p);
        in.clear();
        holdUse = false;
        wantSprint = false;
        aimTarget = null;

        if (client.currentScreen == null) {
            run(client, p, in);
        }

        boolean sprint = wantSprint && in.getForward() > 0.3f && !p.isUsingItem() && !p.isTouchingWater();
        if (p.isSprinting() != sprint) {
            p.setSprinting(sprint);
        }
    }

    /** Конец тика: поворачиваем камеру (так вид интерполируется плавно) и снимаем «зажатие» предмета. */
    public static void endTick(MinecraftClient client) {
        holdUse = false;
        if (!active || aimTarget == null) {
            return;
        }
        ClientPlayerEntity p = client.player;
        if (p == null || !p.isAlive() || client.currentScreen != null) {
            return;
        }
        aim(p, aimTarget, SkillProfile.of(BotScreen.skill()), aimNoisy);
    }

    private static void run(MinecraftClient client, ClientPlayerEntity p, AutopilotInput in) {
        if (eatCd > 0) eatCd--;
        if (placeCd > 0) placeCd--;
        if (++msgTimer % 40 == 0) {
            p.sendMessage(Text.literal("§aАвтопилот: скилл " + BotScreen.skill()), true);
        }

        SkillProfile sk = SkillProfile.of(BotScreen.skill());

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
            eatTicks = 0;
            return;
        }

        double dx = tgt.getX() - p.getX();
        double dz = tgt.getZ() - p.getZ();
        double hd = Math.hypot(dx, dz);
        double ux = hd > 1.0E-4 ? dx / hd : 0.0;
        double uz = hd > 1.0E-4 ? dz / hd : 0.0;
        double dist = p.distanceTo(tgt);
        boolean ground = p.isOnGround();

        // ---------- еда ----------
        int apple = hotbarSlot(p, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);
        if (eatTicks > 0) {
            eatTicks--;
            if (!p.isUsingItem() && eatTicks < 28) {
                eatTicks = 0;
                eatCd = 160;
            } else {
                lookAt(tgt, true);
                holdUse = true;
                drive(in, p, ux, uz, hd < 6.0 ? -1.0 : 0.0, 0.0);
                return;
            }
        }
        if (apple >= 0 && eatCd <= 0 && p.getHealth() <= sk.gappleHp) {
            p.getInventory().selectedSlot = apple;
            eatTicks = 36;
            return;
        }

        // ---------- стройка ----------
        if (build(client, p, in, tgt, hd, sk)) {
            return;
        }

        // ---------- оружие ----------
        selectWeapon(p, p.fallDistance > 2.5f);

        // ---------- прицел (поворот выполняется в конце тика) ----------
        lookAt(tgt, true);

        // ---------- движение ----------
        double approach = hd > 3.0 ? 1.0 : (hd < 1.4 ? -1.0 : 0.0);
        double strafe = (hd < 6.0 && sk.strafe > 0.2) ? strafeDir : 0.0;
        drive(in, p, ux, uz, approach, strafe);
        wantSprint = approach > 0.0 && (sk.level >= 2 || hd > 5.0);

        boolean jump = false;
        if (p.horizontalCollision && ground) {
            jump = true;
        }
        if (p.isTouchingWater() || p.isInLava()) {
            jump = true; // держимся на поверхности
        }

        // крит-прыжок: прыгаем рядом с целью, бьём на спуске
        boolean mace = p.getMainHandStack().isOf(Items.MACE);
        float cd = p.getAttackCooldownProgress(0.5f);
        if (wantCrit && !mace && ground && hd < 3.6 && cd > 0.55f) {
            jump = true;
        }
        if (sk.level >= 5 && hd > 4.5 && ground && !wantCrit && RND.nextInt(12) == 0) {
            jump = true;
        }
        boolean airborne = !ground;
        if (airborne && wantCrit) {
            wantSprint = false;
        }
        in.setJump(jump);

        // ---------- атака ----------
        boolean critWindow = !wantCrit || mace || (airborne && p.fallDistance > 0.0f);
        boolean inReach = p.canSee(tgt) && lookingAt(p, tgt, REACH);
        if (inReach && cd >= nextReady && critWindow) {
            if (p.isUsingItem()) {
                client.interactionManager.stopUsingItem(p); // опускаем щит перед ударом
            }
            client.interactionManager.attackEntity(p, tgt);
            p.swingHand(Hand.MAIN_HAND);
            wantCrit = RND.nextDouble() < sk.critChance;
            nextReady = sk.readyMin + RND.nextFloat() * (1.0f - sk.readyMin) * 0.5f;
        }

        // ---------- щит ----------
        if (sk.level >= 3 && p.getOffHandStack().isOf(Items.SHIELD) && dist < 5.5 && cd < nextReady - 0.04f) {
            holdUse = true;
        }
    }

    // =====================================================================

    /**
     * Ведёт игрока в мировых координатах относительно цели и переводит это в команды «вперёд/вбок»
     * с учётом того, куда сейчас повёрнут игрок. Поэтому ходьба не зависит от камеры и прицельного шума.
     *
     * @param ux,uz    единичный вектор от игрока к цели (XZ)
     * @param approach +1 — к цели, -1 — от цели, 0 — стоять на дистанции
     * @param strafe   +1 / -1 — боком по кругу вокруг цели
     */
    private static void drive(AutopilotInput in, ClientPlayerEntity p, double ux, double uz, double approach, double strafe) {
        double wx = ux * approach - uz * strafe;
        double wz = uz * approach + ux * strafe;
        double len = Math.hypot(wx, wz);
        if (len > 1.0) {
            wx /= len;
            wz /= len;
        }
        double yaw = Math.toRadians(p.getYaw());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        // вперёд по взгляду = (-sin, cos), влево = (cos, sin)
        float forward = (float) (wz * cos - wx * sin);
        float sideways = (float) (wz * sin + wx * cos);
        in.setMove(forward, sideways);
    }

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

    /** Попадёт ли удар: луч из глаз по текущему взгляду пересекает хитбокс цели. */
    private static boolean lookingAt(ClientPlayerEntity p, LivingEntity t, double reach) {
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(p.getRotationVec(1.0f).multiply(reach));
        Box box = t.getBoundingBox().expand(t.getTargetingMargin());
        return box.contains(eye) || box.raycast(eye, end).isPresent();
    }

    /** Запоминает, на кого навести камеру в конце тика. */
    private static void lookAt(LivingEntity t, boolean noisy) {
        aimTarget = t;
        aimNoisy = noisy;
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

    private static boolean build(MinecraftClient client, ClientPlayerEntity p, AutopilotInput in,
                                 LivingEntity tgt, double hd, SkillProfile sk) {
        if (sk.level < 3 || placeCd > 0) {
            return false;
        }
        int slot = blockSlot(p);
        if (slot < 0) {
            return false;
        }
        double dy = tgt.getY() - p.getY();

        // столб вверх к более высокой цели
        if (dy >= 2.5 && hd < 6.0) {
            lookAt(tgt, false);
            if (p.isOnGround()) {
                in.setJump(true);
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
                    lookAt(tgt, false);
                    drive(in, p, dir.x, dir.z, 1.0, 0.0);
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
