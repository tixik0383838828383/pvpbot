package com.timur.pvpbot;

import com.timur.pvpbot.entity.BotEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class PvpBotMod implements ModInitializer {
    public static final String MOD_ID = "pvpbot";
    public static final int MAX_BOTS_PER_PLAYER = 10;

    public static EntityType<BotEntity> BOT;

    private static final List<PendingRespawn> PENDING = new ArrayList<>();

    private static final class PendingRespawn {
        final UUID owner;
        final BotConfig cfg;
        int ticks = 50;

        PendingRespawn(UUID owner, BotConfig cfg) {
            this.owner = owner;
            this.cfg = cfg;
        }
    }

    @Override
    public void onInitialize() {
        BOT = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(MOD_ID, "pvp_bot"),
                EntityType.Builder.create(BotEntity::new, SpawnGroup.MISC)
                        .dimensions(0.6f, 1.8f)
                        .maxTrackingRange(10)
                        .disableSaving()
                        .build("pvp_bot"));

        FabricDefaultAttributeRegistry.register(BOT, BotEntity.createBotAttributes());

        PayloadTypeRegistry.playC2S().register(BotRequestPayload.ID, BotRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BotCommandPayload.ID, BotCommandPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BotListPayload.ID, BotListPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BotRequestPayload.ID,
                (payload, context) -> handle(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(BotCommandPayload.ID,
                (payload, context) -> handleCommand(context.player(), payload));

        ServerTickEvents.END_SERVER_TICK.register(PvpBotMod::tickRespawns);
    }

    // ---------- выбор ботов, конфликты, режимы ----------

    private static int fightCounter = 0;

    public static List<BotEntity> ownerBots(ServerPlayerEntity owner) {
        ServerWorld world = owner.getServerWorld();
        return new ArrayList<>(world.getEntitiesByType(BOT, b -> owner.getUuid().equals(b.getOwnerId())));
    }

    private static List<BotEntity> selectedBots(ServerPlayerEntity owner) {
        List<BotEntity> out = new ArrayList<>();
        for (BotEntity b : ownerBots(owner)) {
            if (b.isSelected()) {
                out.add(b);
            }
        }
        return out;
    }

    private static void handleCommand(ServerPlayerEntity player, BotCommandPayload p) {
        MinecraftServer server = player.getServer();
        if (server != null && server.isDedicated() && !player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("§cНужны права оператора, чтобы управлять PvP-ботами."), true);
            return;
        }
        switch (p.cmd()) {
            case BotCommandPayload.SELECT_TOGGLE -> {
                if (player.getServerWorld().getEntityById(p.entityId()) instanceof BotEntity b
                        && player.getUuid().equals(b.getOwnerId())) {
                    b.setSelected(!b.isSelected());
                }
            }
            case BotCommandPayload.SELECT_ALL -> {
                for (BotEntity b : ownerBots(player)) {
                    b.setSelected(true);
                }
            }
            case BotCommandPayload.SELECT_NONE -> {
                for (BotEntity b : ownerBots(player)) {
                    b.setSelected(false);
                }
            }
            case BotCommandPayload.FIGHT_FFA, BotCommandPayload.FIGHT_TEAMS -> {
                List<BotEntity> sel = selectedBots(player);
                if (sel.size() < 2) {
                    player.sendMessage(Text.literal("§cВыбери минимум двух ботов."), true);
                } else {
                    int group = ++fightCounter;
                    boolean teams = p.cmd() == BotCommandPayload.FIGHT_TEAMS;
                    for (int i = 0; i < sel.size(); i++) {
                        BotEntity b = sel.get(i);
                        b.setTeam(teams ? 1 + (i % 2) : 0);
                        b.setFightGroup(group);
                        b.setMode(BotListPayload.MODE_FIGHT);
                    }
                    player.sendMessage(Text.literal(teams
                            ? "§eБои команд: красные против синих!" : "§eВсе против всех!"), true);
                }
            }
            case BotCommandPayload.PEACE -> {
                for (BotEntity b : selectedBots(player)) {
                    b.setTeam(0);
                    b.setFightGroup(0);
                    b.setMode(BotListPayload.MODE_TRAIN);
                }
            }
            case BotCommandPayload.GUARD -> {
                for (BotEntity b : selectedBots(player)) {
                    b.setTeam(0);
                    b.setFightGroup(0);
                    b.setMode(BotListPayload.MODE_GUARD);
                }
            }
            case BotCommandPayload.IDLE -> {
                for (BotEntity b : selectedBots(player)) {
                    b.setTeam(0);
                    b.setFightGroup(0);
                    b.setMode(BotListPayload.MODE_IDLE);
                }
            }
            case BotCommandPayload.SKIN -> {
                for (BotEntity b : selectedBots(player)) {
                    b.applySkin(p.text());
                }
            }
            case BotCommandPayload.REMOVE -> {
                for (BotEntity b : selectedBots(player)) {
                    b.discard();
                }
            }
            default -> {
            }
        }
        sendList(player);
    }

    public static void sendList(ServerPlayerEntity player) {
        List<BotListPayload.Info> infos = new ArrayList<>();
        for (BotEntity b : ownerBots(player)) {
            infos.add(new BotListPayload.Info(b.getId(), b.getName().getString(), (int) Math.ceil(b.getHealth()),
                    b.getTeam(), b.getMode() | (b.isSelected() ? 8 : 0)));
        }
        ServerPlayNetworking.send(player, new BotListPayload(infos));
    }

    private static void handle(ServerPlayerEntity player, BotRequestPayload p) {
        MinecraftServer server = player.getServer();
        if (server != null && server.isDedicated() && !player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("§cНужны права оператора, чтобы спавнить PvP-ботов."), true);
            return;
        }
        if (p.action() == BotRequestPayload.CLEAR) {
            int n = clearBots(player);
            player.sendMessage(Text.literal("§7Удалено ботов: " + n), true);
            return;
        }
        BotConfig cfg = new BotConfig(p.skill(), p.armor(), p.weapon(), p.flags(), p.skin());
        if (countBots(player) >= MAX_BOTS_PER_PLAYER) {
            player.sendMessage(Text.literal("§cМаксимум ботов: " + MAX_BOTS_PER_PLAYER + ". Удали старых."), true);
            return;
        }
        spawnBot(player, cfg);
        sendList(player);
    }

    public static int countBots(ServerPlayerEntity owner) {
        ServerWorld world = owner.getServerWorld();
        return world.getEntitiesByType(BOT, b -> owner.getUuid().equals(b.getOwnerId())).size();
    }

    public static int clearBots(ServerPlayerEntity owner) {
        ServerWorld world = owner.getServerWorld();
        List<BotEntity> list = new ArrayList<>(world.getEntitiesByType(BOT, b -> owner.getUuid().equals(b.getOwnerId())));
        for (BotEntity b : list) {
            b.discard();
        }
        PENDING.removeIf(r -> r.owner.equals(owner.getUuid()));
        return list.size();
    }

    public static BotEntity spawnBot(ServerPlayerEntity owner, BotConfig cfg) {
        ServerWorld world = owner.getServerWorld();
        Vec3d look = owner.getRotationVec(1.0f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        Vec3d pos = owner.getPos().add(flat.multiply(5.0));

        BotEntity bot = new BotEntity(BOT, world);
        float yaw = (float) (MathHelper.atan2(owner.getZ() - pos.z, owner.getX() - pos.x) * 57.2957795) - 90.0f;
        bot.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0.0f);
        for (int i = 0; i < 5 && !world.isSpaceEmpty(bot); i++) {
            bot.setPosition(pos.x, bot.getY() + 1.0, pos.z);
        }
        bot.configure(owner.getUuid(), cfg, world);
        world.spawnEntity(bot);
        return bot;
    }

    public static void scheduleRespawn(UUID owner, BotConfig cfg) {
        PENDING.add(new PendingRespawn(owner, cfg));
    }

    private static void tickRespawns(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        Iterator<PendingRespawn> it = PENDING.iterator();
        List<PendingRespawn> ready = new ArrayList<>();
        while (it.hasNext()) {
            PendingRespawn r = it.next();
            if (--r.ticks <= 0) {
                ready.add(r);
                it.remove();
            }
        }
        for (PendingRespawn r : ready) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(r.owner);
            if (p != null && p.isAlive() && countBots(p) < MAX_BOTS_PER_PLAYER) {
                spawnBot(p, r.cfg);
            }
        }
    }
}
