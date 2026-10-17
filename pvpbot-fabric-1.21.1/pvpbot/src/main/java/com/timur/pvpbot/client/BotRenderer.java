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

/** Рисует бота как игрока (скин Стива) с бронёй и предметами в руках. */
public class BotRenderer extends BipedEntityRenderer<BotEntity, PlayerEntityModel<BotEntity>> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

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

    @Override
    public Identifier getTexture(BotEntity entity) {
        return TEXTURE;
    }
}
