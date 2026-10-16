package dev.fupez.rpgplayable.client.mixin;

import dev.fupez.rpgplayable.client.ClientCleave;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wrap only this hand's render matrix. Vanilla still owns items, maps, use poses and hit swings. */
@Mixin(HeldItemRenderer.class)
abstract class CleaveHandMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void rpgplayable$windup(AbstractClientPlayerEntity player, float tickProgress, float pitch,
            Hand hand, float swing, ItemStack stack, float equip, MatrixStack matrices,
            OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        matrices.push();
        if (hand != Hand.MAIN_HAND) return;
        float weight = ClientCleave.state().pose(tickProgress);
        float side = player.getMainArm() == Arm.RIGHT ? 1 : -1;
        matrices.translate(0.10f * side * weight, -0.08f * weight, -0.18f * weight);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-30 * side * weight));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(20 * side * weight));
    }
    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void rpgplayable$restore(AbstractClientPlayerEntity player, float tickProgress, float pitch,
            Hand hand, float swing, ItemStack stack, float equip, MatrixStack matrices,
            OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        matrices.pop();
    }
}
