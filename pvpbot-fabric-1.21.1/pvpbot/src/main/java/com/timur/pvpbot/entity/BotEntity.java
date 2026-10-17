package com.timur.pvpbot.entity;

import com.timur.pvpbot.BotConfig;
import com.timur.pvpbot.PvpBotMod;
import com.timur.pvpbot.SkillProfile;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.BodyControl;
import net.minecraft.entity.ai.control.LookControl;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PvP-бот. Весь ИИ написан вручную в {@link #mobTick()}: стандартные Goal-ы не используются,
 * поэтому все умения (прицел, кулдаун, крит, стрейф, клатч, лук, паутина, булава) напрямую
 * зависят от {@link SkillProfile}.
 */
public class BotEntity extends PathAwareEntity {
    private static final double REACH = 3.0;

    // ---------- конфигурация ----------
    private UUID ownerId;
    private BotConfig cfg = new BotConfig(5, 3, 0, BotConfig.ALL_DEFAULT_FLAGS);
    private SkillProfile sk = SkillProfile.of(5);

    private ItemStack weaponStack = ItemStack.EMPTY;
    private ItemStack bowStack = ItemStack.EMPTY;
    private ItemStack webStack = ItemStack.EMPTY;
    private ItemStack waterStack = ItemStack.EMPTY;
    private ItemStack pearlStack = ItemStack.EMPTY;
    private ItemStack windStack = ItemStack.EMPTY;
    private int sharpness = 0;
    private int density = 0;
    private int pearls = 0;
    private int apples = 0;

    // ---------- управление ----------
    private final BotMoveControl botMove;
    private float mvF, mvS;
    private boolean mvSprint, mvJump;

    // ---------- состояние ----------
    private int switchLock = 0;
    private int sinceAttack = 100;
    private float nextReady = 0.9f;
    private boolean wantCrit = false;

    private float noiseYaw, noisePitch;
    private int noiseTimer = 0;
    private int strafeDir = 1, strafeTimer = 0;

    private Vec3d lastTargetPos = null;
    private Vec3d targetVel = Vec3d.ZERO;
    private int ownerMissing = 0;

    // паутина
    private int webCd = 40;
    private boolean wantWeb = false;
    private int webTimer = 0;
    private final List<PlacedBlock> webs = new ArrayList<>();

    // лук
    private boolean bowMode = false;
    private int drawTicks = 0;
    private int bowCd = 0;

    // еда
    private boolean eating = false;
    private int appleCd = 0;

    // булава
    private boolean smashing = false;
    private boolean windWant = false;
    private int smashTicks = 0;
    private int windCd = 40;

    // клатч
    private int fallTicks = 0;
    private boolean clutchRolled = false;
    private boolean clutchOk = false;
    private double placeAt = 3.0;
    private BlockPos waterPos = null;
    private int waterAge = 0;
    private int pearlCd = 0;

    // застревание
    private Vec3d lastPos = null;
    private int stuckTicks = 0;
    private int navTicks = 0;

    private record PlacedBlock(BlockPos pos, int expire) {
    }

    private static class BotMoveControl extends MoveControl {
        boolean manual = true;

        BotMoveControl(MobEntity entity) {
            super(entity);
        }

        @Override
        public void tick() {
            if (!manual) {
                super.tick();
            }
        }
    }

    public BotEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.botMove = new BotMoveControl(this);
        this.moveControl = this.botMove;
        this.lookControl = new LookControl(this) {
            @Override
            public void tick() {
            }
        };
        this.setPersistent();
    }

    @Override
    protected BodyControl createBodyControl() {
        return new BodyControl(this) {
            @Override
            public void tick() {
            }
        };
    }

    public static DefaultAttributeContainer.Builder createBotAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0)
                .add(EntityAttributes.GENERIC_ATTACK_SPEED, 4.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    // =====================================================================
    //  Настройка и экипировка
    // =====================================================================

    public void configure(UUID owner, BotConfig config, ServerWorld world) {
        this.ownerId = owner;
        this.cfg = config;
        this.sk = SkillProfile.of(config.skill());
        this.setCustomName(Text.literal("PvP-бот [" + sk.level + " · " + sk.rank() + "]"));
        this.setCustomNameVisible(true);
        buildLoadout(world);
        this.webCd = 40;
        this.windCd = 40;
        this.nextReady = sk.readyMin;
        this.wantCrit = random.nextDouble() < sk.critChance;
    }

    private void buildLoadout(ServerWorld world) {
        int armor = cfg.armor();
        if (armor > 0) {
            Item[] set = switch (armor) {
                case 1 -> new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
                case 2 -> new Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
                default -> new Item[]{Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
            };
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < 4; i++) {
                ItemStack s = new ItemStack(set[i]);
                if (armor >= 4) {
                    enchant(s, world, Enchantments.PROTECTION, 4);
                    enchant(s, world, Enchantments.UNBREAKING, 3);
                    if (i == 3) {
                        enchant(s, world, Enchantments.FEATHER_FALLING, 4);
                    }
                }
                equipStack(slots[i], s);
            }
        }

        boolean netherite = armor >= 3;
        boolean diamond = armor == 2;
        switch (cfg.weapon()) {
            case BotConfig.W_AXE -> {
                weaponStack = new ItemStack(netherite ? Items.NETHERITE_AXE : diamond ? Items.DIAMOND_AXE : Items.IRON_AXE);
                if (armor >= 4) {
                    sharpness = 5;
                    enchant(weaponStack, world, Enchantments.SHARPNESS, 5);
                }
            }
            case BotConfig.W_MACE -> {
                weaponStack = new ItemStack(Items.MACE);
                if (armor >= 4) {
                    density = 5;
                    enchant(weaponStack, world, Enchantments.DENSITY, 5);
                }
            }
            default -> {
                weaponStack = new ItemStack(netherite ? Items.NETHERITE_SWORD : diamond ? Items.DIAMOND_SWORD : Items.IRON_SWORD);
                if (armor >= 4) {
                    sharpness = 5;
                    enchant(weaponStack, world, Enchantments.SHARPNESS, 5);
                }
            }
        }

        bowStack = new ItemStack(Items.BOW);
        webStack = new ItemStack(Items.COBWEB, 16);
        waterStack = new ItemStack(Items.WATER_BUCKET);
        pearlStack = new ItemStack(Items.ENDER_PEARL, 8);
        windStack = new ItemStack(Items.WIND_CHARGE, 16);
        pearls = cfg.has(BotConfig.PEARL) ? 8 : 0;
        apples = cfg.has(BotConfig.APPLE) ? 8 : 0;

        equipStack(EquipmentSlot.MAINHAND, weaponStack.copy());
        if (cfg.has(BotConfig.SHIELD)) {
            equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        }
    }

    private static void enchant(ItemStack stack, ServerWorld world, RegistryKey<Enchantment> key, int level) {
        stack.addEnchantment(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(key), level);
    }

    /** Берёт предмет в основную руку. true — предмет уже в руке и «готов» к использованию. */
    private boolean held(ItemStack want) {
        if (!getMainHandStack().isOf(want.getItem())) {
            if (isUsingItem()) {
                clearActiveItem();
            }
            equipStack(EquipmentSlot.MAINHAND, want.copy());
            switchLock = sk.reaction / 3;
            return switchLock <= 0;
        }
        return switchLock <= 0;
    }

    // =====================================================================
    //  Главный цикл ИИ
    // =====================================================================

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld world)) {
            return;
        }
        tickTimers(world);

        PlayerEntity owner = ownerId == null ? null : world.getPlayerByUuid(ownerId);
        if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.getWorld() != world) {
            if (++ownerMissing > 400) {
                discard();
            }
            resetMove();
            finishMove(null);
            return;
        }
        ownerMissing = 0;

        Vec3d tp = owner.getPos();
        targetVel = lastTargetPos == null ? Vec3d.ZERO : tp.subtract(lastTargetPos);
        lastTargetPos = tp;

        resetMove();
        if (navTicks > 0 && age % 10 == 0) {
            getNavigation().startMovingTo(owner, 1.2);
        }

        boolean handled = tickClutch(world, owner)
                || tickEat(owner)
                || tickBow(world, owner)
                || tickWebPlace(world, owner);
        if (!handled) {
            tickMelee(world, owner);
        }
        finishMove(owner);
    }

    private void tickTimers(ServerWorld world) {
        sinceAttack++;
        if (switchLock > 0) switchLock--;
        if (webCd > 0) webCd--;
        if (bowCd > 0) bowCd--;
        if (appleCd > 0) appleCd--;
        if (windCd > 0) windCd--;
        if (pearlCd > 0) pearlCd--;

        if (--noiseTimer <= 0) {
            noiseTimer = sk.noiseInterval + random.nextInt(3);
            noiseYaw = (float) (random.nextGaussian() * sk.aimError);
            noisePitch = (float) (random.nextGaussian() * sk.aimError * 0.6);
        }
        if (--strafeTimer <= 0) {
            strafeTimer = 12 + random.nextInt(30);
            strafeDir = random.nextBoolean() ? 1 : -1;
        }

        webs.removeIf(w -> {
            if (age >= w.expire()) {
                if (world.getBlockState(w.pos()).isOf(Blocks.COBWEB)) {
                    world.setBlockState(w.pos(), Blocks.AIR.getDefaultState());
                }
                return true;
            }
            return false;
        });

        if (waterPos != null) {
            waterAge++;
            boolean landed = isOnGround() || (isTouchingWater() && getVelocity().y > -0.1);
            if (waterAge > 10 && landed || waterAge > 80) {
                if (world.getBlockState(waterPos).isOf(Blocks.WATER)) {
                    world.setBlockState(waterPos, Blocks.AIR.getDefaultState());
                    playSound(SoundEvents.ITEM_BUCKET_FILL, 1.0f, 1.0f);
                }
                waterPos = null;
            }
        }
    }

    // =====================================================================
    //  Клатч: вода (MLG) и жемчуг над бездной
    // =====================================================================

    private boolean tickClutch(ServerWorld world, PlayerEntity owner) {
        boolean falling = !isOnGround() && !isTouchingWater() && !isClimbing() && getVelocity().y < -0.3;
        if (!falling) {
            fallTicks = 0;
            clutchRolled = false;
            return false;
        }
        fallTicks++;

        Vec3d from = getPos();
        BlockHitResult hit = world.raycast(new RaycastContext(from, from.add(0, -80, 0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, this));
        boolean ground = hit.getType() == HitResult.Type.BLOCK;
        double gd = ground ? from.y - hit.getPos().y : 999.0;

        if (ground && world.getFluidState(hit.getBlockPos()).isIn(FluidTags.WATER)) {
            return false;
        }
        double total = fallDistance + (ground ? gd : 100.0);
        if (total < 5.0) {
            return false;
        }

        if (!clutchRolled) {
            clutchRolled = true;
            clutchOk = random.nextDouble() < sk.clutchChance;
            placeAt = 4.0 - 1.8 * sk.t + (random.nextDouble() - 0.5) * 0.6;
        }
        if (!clutchOk) {
            return false;
        }

        if (ground && cfg.has(BotConfig.WATER)) {
            rotate(getYaw(), 90.0f);
            if (gd < 12.0 && held(waterStack) && gd <= placeAt && waterPos == null) {
                BlockPos p = hit.getBlockPos().up();
                if (world.getBlockState(p).isAir()) {
                    world.setBlockState(p, Blocks.WATER.getDefaultState());
                    waterPos = p;
                    waterAge = 0;
                    playSound(SoundEvents.ITEM_BUCKET_EMPTY, 1.0f, 1.0f);
                    swingHand(Hand.MAIN_HAND);
                }
            }
            return true;
        }

        if (!ground && cfg.has(BotConfig.PEARL) && pearls > 0 && sk.level >= 4
                && pearlCd <= 0 && fallTicks > sk.reaction + 6) {
            Vec3d tp = owner.getPos();
            double dist = from.distanceTo(tp);
            Vec3d aim = tp.add(0, 1.0 + dist * 0.12, 0);
            aimAtPoint(aim, false);
            if (held(pearlStack)) {
                throwPearl(world, aim);
            }
            return true;
        }
        return false;
    }

    private void throwPearl(ServerWorld world, Vec3d aim) {
        EnderPearlEntity pearl = new EnderPearlEntity(world, this);
        pearl.setItem(new ItemStack(Items.ENDER_PEARL));
        pearl.setPosition(getX(), getEyeY() - 0.1, getZ());
        Vec3d d = aim.subtract(getEyePos());
        pearl.setVelocity(d.x, d.y, d.z, 1.8f, 0.0f);
        world.spawnEntity(pearl);
        playSound(SoundEvents.ENTITY_ENDER_PEARL_THROW, 1.0f, 1.0f);
        swingHand(Hand.MAIN_HAND);
        pearls--;
        pearlCd = 60;
    }

    // =====================================================================
    //  Золотое яблоко
    // =====================================================================

    private boolean tickEat(PlayerEntity owner) {
        if (eating) {
            aimAtPoint(chestOf(owner), true);
            if (!isUsingItem()) {
                eating = false;
                appleCd = 160;
                return false;
            }
            mvF = horizDist(owner) < 6.0 ? -1.0f : 0.0f;
            return true;
        }
        if (cfg.has(BotConfig.APPLE) && apples > 0 && appleCd <= 0 && getHealth() <= sk.gappleHp) {
            if (isUsingItem()) {
                clearActiveItem();
            }
            apples--;
            equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_APPLE));
            setCurrentHand(Hand.MAIN_HAND);
            eating = true;
            return true;
        }
        return false;
    }

    // =====================================================================
    //  Лук
    // =====================================================================

    private boolean tickBow(ServerWorld world, PlayerEntity owner) {
        if (!cfg.has(BotConfig.BOW)) {
            return false;
        }
        double d = horizDist(owner);
        boolean sees = canSee(owner);
        if (!bowMode && d > 11.0 && sees) {
            bowMode = true;
        }
        if (bowMode && (d < 7.0 || !sees)) {
            bowMode = false;
            if (isUsingItem() && getMainHandStack().isOf(Items.BOW)) {
                clearActiveItem();
            }
            drawTicks = 0;
            return false;
        }
        if (!bowMode) {
            return false;
        }
        if (!held(bowStack)) {
            aimAtPoint(chestOf(owner), true);
            return true;
        }
        if (!isUsingItem() && bowCd <= 0) {
            setCurrentHand(Hand.MAIN_HAND);
            drawTicks = 0;
        }
        float err = aimAtPoint(bowAimPoint(owner), true);
        mvS = (float) (strafeDir * sk.strafe * 0.8);
        if (isUsingItem()) {
            drawTicks++;
            if (drawTicks >= sk.bowDraw && err < 2.5f + (float) (1.0 - sk.t) * 8.0f) {
                shootArrow(world);
            }
        }
        return true;
    }

    private Vec3d bowAimPoint(PlayerEntity owner) {
        Vec3d tp = chestOf(owner);
        double dist = getEyePos().distanceTo(tp);
        double flight = dist / 2.8;
        Vec3d lead = targetVel.multiply(flight * sk.bowLead);
        double drop = 0.025 * flight * flight * (0.6 + 0.4 * sk.t);
        return tp.add(lead).add(0, drop, 0);
    }

    private void shootArrow(ServerWorld world) {
        float pull = Math.min(1.0f, drawTicks / 20.0f);
        float power = (pull * pull + pull * 2.0f) / 3.0f;
        ArrowEntity arrow = new ArrowEntity(EntityType.ARROW, world);
        arrow.setOwner(this);
        arrow.setPosition(getX(), getEyeY() - 0.1, getZ());
        arrow.setVelocity(this, getPitch(), getYaw(), 0.0f, power * 3.0f, 1.0f + (float) (1.0 - sk.t) * 5.0f);
        if (power >= 0.95f) {
            arrow.setCritical(true);
        }
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        world.spawnEntity(arrow);
        playSound(SoundEvents.ENTITY_ARROW_SHOOT, 1.0f, 1.0f / (random.nextFloat() * 0.4f + 1.2f) + pull * 0.5f);
        clearActiveItem();
        drawTicks = 0;
        bowCd = 8 + sk.reaction * 2;
    }

    // =====================================================================
    //  Паутина
    // =====================================================================

    private boolean tickWebPlace(ServerWorld world, PlayerEntity owner) {
        if (!cfg.has(BotConfig.WEB) || sk.level < 2) {
            return false;
        }
        double d = distanceTo(owner);
        BlockPos p = owner.getBlockPos();
        if (!wantWeb) {
            boolean already = world.getBlockState(p).isOf(Blocks.COBWEB) || world.getBlockState(p.up()).isOf(Blocks.COBWEB);
            if (webCd <= 0 && d >= 1.8 && d <= 4.2 && !already && world.getBlockState(p).isAir()
                    && random.nextDouble() < sk.webUse) {
                wantWeb = true;
                webTimer = 0;
            } else {
                return false;
            }
        }
        webTimer++;
        if (d > 5.0 || webTimer > 40 || !world.getBlockState(p).isAir()) {
            wantWeb = false;
            return false;
        }
        aimAtPoint(new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5), false);
        mvF = d > 3.0 ? 1.0f : 0.0f;
        if (!held(webStack)) {
            return true;
        }
        world.setBlockState(p, Blocks.COBWEB.getDefaultState());
        webs.add(new PlacedBlock(p, age + 100 + random.nextInt(80)));
        playSound(SoundEvents.BLOCK_COBWEB_PLACE, 1.0f, 1.0f);
        swingHand(Hand.MAIN_HAND);
        webCd = sk.webCooldown;
        wantWeb = false;
        return true;
    }

    // =====================================================================
    //  Ближний бой (меч / топор / булава)
    // =====================================================================

    private void tickMelee(ServerWorld world, PlayerEntity owner) {
        double hd = horizDist(owner);
        double dist = distanceTo(owner);
        boolean sees = canSee(owner);
        boolean mace = cfg.weapon() == BotConfig.W_MACE;

        double speedAttr = getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED);
        double period = 20.0 / Math.max(0.2, speedAttr);
        double progress = MathHelper.clamp((sinceAttack + 0.5) / period, 0.0, 1.0);

        if (smashing) {
            tickSmash(world, owner);
            return;
        }

        // решение: готовиться к прыжку с ветрозарядом
        if (mace) {
            if (!windWant && windCd <= 0 && isOnGround() && sees && hd > 1.8 && hd < 9.0 && progress > 0.5
                    && random.nextDouble() < 0.04 + 0.2 * sk.t) {
                windWant = true;
            }
            if (windWant && (hd > 12.0 || !sees)) {
                windWant = false;
            }
        }

        boolean armed = held(windWant ? windStack : weaponStack);
        aimAtPoint(chestOf(owner), true);

        if (windWant && armed && isOnGround()) {
            launchSmash(owner);
            return;
        }

        // перемещение
        double ideal = 2.5;
        if (hd > ideal + 0.5) {
            mvF = 1.0f;
            mvSprint = sk.level >= 2 || hd > 5.0;
        } else if (hd < 1.4) {
            mvF = -1.0f;
        }
        if (hd < 6.0 && sk.strafe > 0.2) {
            mvS = (float) (strafeDir * sk.strafe * 0.9);
        }

        // крит-прыжок: прыгаем так, чтобы кулдаун дозрел на спуске
        int ticksToReady = (int) Math.ceil(Math.max(0.0, 0.92 - progress) * period);
        if (wantCrit && !mace && !windWant && isOnGround() && hd < 3.6 && ticksToReady <= 7 && sees) {
            mvJump = true;
        }
        boolean airborne = !isOnGround();
        if (airborne && wantCrit) {
            mvSprint = false;
        }

        // атака
        boolean fresh = progress >= nextReady;
        boolean critWindow = !wantCrit || mace || (airborne && fallDistance > 0) || sinceAttack > period + 14;
        boolean inReach = dist < 4.2 && lookingAt(owner, REACH);
        if (armed && !windWant && sees && inReach && fresh && critWindow) {
            if (isUsingItem() && getActiveHand() == Hand.OFF_HAND) {
                clearActiveItem();
            }
            attack(world, owner, progress);
            wantCrit = random.nextDouble() < sk.critChance;
            nextReady = sk.readyMin + random.nextFloat() * (1.0f - sk.readyMin) * 0.5f;
        }

        // щит: поднимаем пока кулдаун не готов
        if (cfg.has(BotConfig.SHIELD) && getOffHandStack().isOf(Items.SHIELD)) {
            boolean blocking = isUsingItem() && getActiveHand() == Hand.OFF_HAND;
            boolean want = sk.level >= 3 && dist < 5.5 && progress < nextReady - 0.04;
            if (want && !isUsingItem()) {
                setCurrentHand(Hand.OFF_HAND);
            } else if (!want && blocking) {
                clearActiveItem();
            }
        }
    }

    private void launchSmash(PlayerEntity owner) {
        Vec3d dir = new Vec3d(owner.getX() - getX(), 0, owner.getZ() - getZ());
        double len = dir.length();
        Vec3d h = len > 0.01 ? dir.multiply(1.0 / len) : Vec3d.ZERO;
        double vy = 0.75 + 0.35 * sk.t;
        setVelocity(h.x * 0.25, vy, h.z * 0.25);
        smashing = true;
        smashTicks = 0;
        windWant = false;
        windCd = 70 + random.nextInt(30);
        playSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
        if (getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 20, 0.3, 0.1, 0.3, 0.05);
        }
    }

    private void tickSmash(ServerWorld world, PlayerEntity owner) {
        smashTicks++;
        aimAtPoint(chestOf(owner), true);
        if (smashTicks > 90) {
            smashing = false;
            return;
        }
        if (!isOnGround()) {
            double lead = 4.0 * sk.bowLead;
            Vec3d to = new Vec3d(owner.getX() + targetVel.x * lead - getX(), 0, owner.getZ() + targetVel.z * lead - getZ());
            double len = to.length();
            if (len > 0.4) {
                Vec3d add = to.multiply(1.0 / len).multiply(0.045 + 0.03 * sk.t);
                Vec3d v = getVelocity().add(add.x, 0, add.z);
                double hs = Math.hypot(v.x, v.z);
                double cap = 0.42;
                if (hs > cap) {
                    v = new Vec3d(v.x / hs * cap, v.y, v.z / hs * cap);
                }
                setVelocity(v);
            }
            boolean armed = held(weaponStack);
            if (armed && fallDistance > 1.5f && lookingAt(owner, REACH + 0.5)) {
                attack(world, owner, 1.0);
                smashing = false;
                wantCrit = false;
                nextReady = sk.readyMin;
            }
        } else if (smashTicks > 6) {
            smashing = false;
        }
    }

    // =====================================================================
    //  Удар
    // =====================================================================

    private void attack(ServerWorld world, LivingEntity target, double progress) {
        float base = (float) getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (sharpness > 0) {
            base += 0.5f * sharpness + 0.5f;
        }
        boolean smash = cfg.weapon() == BotConfig.W_MACE && fallDistance > 1.5f && !isOnGround();
        float dmg = base * (0.2f + (float) (progress * progress) * 0.8f);
        boolean crit = !smash && progress > 0.9 && fallDistance > 0 && !isOnGround()
                && !isClimbing() && !isTouchingWater() && !isSprinting();
        if (crit) {
            dmg *= 1.5f;
        }
        float fd = fallDistance;
        if (smash) {
            float bonus = fd <= 3.0f ? 4.0f * fd : (fd <= 8.0f ? 12.0f + 2.0f * (fd - 3.0f) : 22.0f + (fd - 8.0f));
            bonus += 0.5f * density * fd;
            dmg += bonus;
        }

        boolean ok = target.damage(getDamageSources().mobAttack(this), dmg);
        swingHand(Hand.MAIN_HAND);
        sinceAttack = 0;
        if (!ok) {
            return;
        }
        if (crit) {
            playSound(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
            world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
        }
        if (isSprinting() && progress > 0.9) {
            float yawRad = getYaw() * 0.017453292f;
            target.takeKnockback(0.5, MathHelper.sin(yawRad), -MathHelper.cos(yawRad));
            setSprinting(false);
        }
        if (smash) {
            fallDistance = 0;
            playSound(SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
            world.spawnParticles(ParticleTypes.CLOUD, target.getX(), target.getY(), target.getZ(), 25, 0.4, 0.1, 0.4, 0.08);
        }
    }

    // =====================================================================
    //  Прицел и движение
    // =====================================================================

    private Vec3d chestOf(PlayerEntity p) {
        return p.getPos().add(0, p.getHeight() * 0.62, 0);
    }

    private double horizDist(PlayerEntity p) {
        return Math.hypot(p.getX() - getX(), p.getZ() - getZ());
    }

    /** Наводится на точку с учётом шума скилла. Возвращает угловую ошибку (градусы) до поворота. */
    private float aimAtPoint(Vec3d p, boolean noisy) {
        Vec3d e = getEyePos();
        double dx = p.x - e.x;
        double dy = p.y - e.y;
        double dz = p.z - e.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * 57.2957795) - 90.0f;
        float pitch = (float) (-(MathHelper.atan2(dy, h) * 57.2957795));
        if (noisy) {
            yaw += noiseYaw;
            pitch += noisePitch;
        }
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        float err = Math.max(Math.abs(MathHelper.wrapDegrees(yaw - getYaw())), Math.abs(pitch - getPitch()));
        rotate(yaw, pitch);
        return err;
    }

    private void rotate(float targetYaw, float targetPitch) {
        float dyaw = MathHelper.wrapDegrees(targetYaw - getYaw());
        float dpitch = targetPitch - getPitch();
        float k = 0.35f + (float) sk.t * 0.5f;
        float sy = MathHelper.clamp(dyaw * k, -sk.turnSpeed, sk.turnSpeed);
        float sp = MathHelper.clamp(dpitch * k, -sk.turnSpeed, sk.turnSpeed);
        float ny = getYaw() + sy;
        float np = MathHelper.clamp(getPitch() + sp, -90.0f, 90.0f);
        setYaw(ny);
        setPitch(np);
        setHeadYaw(ny);
        setBodyYaw(ny);
    }

    /** Попадёт ли удар: луч из глаз по текущему взгляду пересекает хитбокс цели. */
    private boolean lookingAt(LivingEntity t, double reach) {
        Vec3d eye = getEyePos();
        Vec3d end = eye.add(getRotationVec(1.0f).multiply(reach));
        Box box = t.getBoundingBox().expand(t.getTargetingMargin());
        return box.contains(eye) || box.raycast(eye, end).isPresent();
    }

    private void resetMove() {
        mvF = 0;
        mvS = 0;
        mvSprint = false;
        mvJump = false;
    }

    private void finishMove(PlayerEntity owner) {
        // запасной режим: если бот застрял, на время включаем стандартный pathfinding
        Vec3d pos = getPos();
        if (owner != null && navTicks <= 0) {
            if (lastPos != null && mvF > 0 && pos.squaredDistanceTo(lastPos) < 0.0004) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
            if (stuckTicks > 25 && horizDist(owner) > 2.5) {
                navTicks = 50;
                botMove.manual = false;
                stuckTicks = 0;
                getNavigation().startMovingTo(owner, 1.2);
            }
        }
        lastPos = pos;

        if (navTicks > 0) {
            navTicks--;
            if (navTicks == 0) {
                getNavigation().stop();
                botMove.manual = true;
            }
            return;
        }

        setSprinting(mvSprint && mvF > 0);
        if (isOnGround() && (mvJump || (horizontalCollision && mvF > 0))) {
            getJumpControl().setActive();
        }
        setForwardSpeed(mvF);
        setSidewaysSpeed(mvS);
        setMovementSpeed((float) getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
    }

    // =====================================================================
    //  Смерть, очистка, звуки
    // =====================================================================

    @Override
    public void onDeath(DamageSource source) {
        if (!getWorld().isClient && cfg.has(BotConfig.RESPAWN) && ownerId != null) {
            PvpBotMod.scheduleRespawn(ownerId, cfg);
        }
        // чтобы бот ничего не дропал
        equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
        equipStack(EquipmentSlot.LEGS, ItemStack.EMPTY);
        equipStack(EquipmentSlot.FEET, ItemStack.EMPTY);
        equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        super.onDeath(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (getWorld() instanceof ServerWorld world) {
            for (PlacedBlock w : webs) {
                if (world.getBlockState(w.pos()).isOf(Blocks.COBWEB)) {
                    world.setBlockState(w.pos(), Blocks.AIR.getDefaultState());
                }
            }
            webs.clear();
            if (waterPos != null && world.getBlockState(waterPos).isOf(Blocks.WATER)) {
                world.setBlockState(waterPos, Blocks.AIR.getDefaultState());
            }
            waterPos = null;
        }
        super.remove(reason);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_PLAYER_DEATH;
    }
}
