package com.timur.pvpbot.client;

import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Клиентский кэш скинов ботов.
 * <ul>
 *   <li>пустой ключ — Стив;</li>
 *   <li>«имя.png» — файл из папки {@code config/pvpbot/skins} (64x64 PNG), каждый клиент берёт его у себя;</li>
 *   <li>любое другое — ник игрока Mojang, скин скачивается с серверов Mojang.</li>
 * </ul>
 */
public final class SkinCache {
    public static final Identifier STEVE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

    private static final Map<String, Identifier> FILES = new HashMap<>();
    private static final Map<String, Long> FILE_RETRY = new HashMap<>();
    private static final Map<String, Optional<GameProfile>> PROFILES = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();

    private SkinCache() {
    }

    public static Path skinDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("pvpbot").resolve("skins");
    }

    /** Создаёт папку скинов (если её нет) и возвращает её. */
    public static File ensureSkinDir() {
        File dir = skinDir().toFile();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /** Сбросить кэш: перечитать файлы и перекачать ники. */
    public static void reload() {
        FILES.clear();
        FILE_RETRY.clear();
        PROFILES.clear();
        LOADING.clear();
    }

    public static Identifier resolve(String key) {
        if (key == null || key.isEmpty()) {
            return STEVE;
        }
        if (key.toLowerCase().endsWith(".png")) {
            return fromFile(key);
        }
        return fromNick(key);
    }

    private static Identifier fromFile(String key) {
        Identifier cached = FILES.get(key);
        if (cached != null) {
            return cached;
        }
        long now = System.currentTimeMillis();
        Long retry = FILE_RETRY.get(key);
        if (retry != null && now < retry) {
            return STEVE;
        }
        try {
            if (key.contains("/") || key.contains("\\") || key.contains("..")) {
                throw new IllegalArgumentException("bad skin name");
            }
            Path path = skinDir().resolve(key);
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("skin file not found");
            }
            NativeImage image;
            try (InputStream in = Files.newInputStream(path)) {
                image = NativeImage.read(in);
            }
            if (image.getWidth() != 64 || image.getHeight() != 64) {
                image.close();
                throw new IllegalStateException("skin must be 64x64");
            }
            Identifier id = Identifier.of("pvpbot", "skin/" + Integer.toHexString(key.hashCode()) + "_" + key.length());
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
            FILES.put(key, id);
            return id;
        } catch (Exception e) {
            FILE_RETRY.put(key, now + 5000L);
            return STEVE;
        }
    }

    private static Identifier fromNick(String nick) {
        Optional<GameProfile> profile = PROFILES.get(nick);
        if (profile == null) {
            if (LOADING.add(nick)) {
                MinecraftClient client = MinecraftClient.getInstance();
                SkullBlockEntity.fetchProfileByName(nick).thenAccept(
                        result -> client.execute(() -> PROFILES.put(nick, result)));
            }
            return STEVE;
        }
        if (profile.isEmpty()) {
            return STEVE;
        }
        return MinecraftClient.getInstance().getSkinProvider().getSkinTextures(profile.get()).texture();
    }
}
