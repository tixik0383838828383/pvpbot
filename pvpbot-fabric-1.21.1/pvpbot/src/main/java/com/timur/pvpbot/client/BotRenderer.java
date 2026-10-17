package com.timur.pvpbot.client;

import com.timur.pvpbot.entity.BotEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Рисует бота как игрока (скин из файла, по нику или Стив) с бронёй и предметами в руках. */
public class BotRenderer extends BipedEntityRenderer<BotEntity, PlayerEntityModel<BotEntity>> {

    public BotRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.addFeature(new ArmorFeatureRenderer<>(
                this,
                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public void render(BotEntity bot, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        BipedEntityModel.ArmPose right = BipedEntityModel.ArmPose.EMPTY;
        BipedEntityModel.ArmPose left = BipedEntityModel.ArmPose.EMPTY;

        ItemStack main = bot.getMainHandStack();
        if (!main.isEmpty()) {
            right = BipedEntityModel.ArmPose.ITEM;
            if (bot.isUsingItem() && bot.getActiveHand() == Hand.MAIN_HAND && main.isOf(Items.BOW)) {
                right = BipedEntityModel.ArmPose.BOW_AND_ARROW;
            }
        }
        ItemStack off = bot.getOffHandStack();
        if (!off.isEmpty()) {
            left = BipedEntityModel.ArmPose.ITEM;
            if (bot.isUsingItem() && bot.getActiveHand() == Hand.OFF_HAND) {
                left = BipedEntityModel.ArmPose.BLOCK;
            }
        }
        this.model.rightArmPose = right;
        this.model.leftArmPose = left;
        super.render(bot, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /** Спин (бит 1) вращает модель вокруг оси тела, сальто (бит 2) — кувыркает вперёд. Имя над головой не вращается. */
    @Override
    protected void setupTransforms(BotEntity bot, MatrixStack matrices, float animationProgress,
                                   float bodyYaw, float tickDelta, float scale) {
        super.setupTransforms(bot, matrices, animationProgress, bodyYaw, tickDelta, scale);
        int fx = bot.getFx();
        if (fx == 0) {
            return;
        }
        float t = bot.age + tickDelta;
        matrices.translate(0.0f, 0.9f, 0.0f);
        if ((fx & 1) != 0) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * 42.0f));
        }
        if ((fx & 2) != 0) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(t * 38.0f));
        }
        matrices.translate(0.0f, -0.9f, 0.0f);
    }

    @Override
    public Identifier getTexture(BotEntity entity) {
        return SkinCache.resolve(entity.getSkin());
    }
}
