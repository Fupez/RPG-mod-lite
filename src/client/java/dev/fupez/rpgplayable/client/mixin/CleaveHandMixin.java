package dev.fupez.rpgplayable.client.mixin;

import dev.fupez.rpgplayable.client.ClientAbilities;
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
        float weight = ClientAbilities.state().pose(tickProgress);
        int style = ClientAbilities.state().style();
        float side = player.getMainArm() == Arm.RIGHT ? 1 : -1;
        switch (style) {
            case 1 -> { // cast: palm and held catalyst extend toward the target
                matrices.translate(-0.12f * side * weight, 0.12f * weight, -0.36f * weight);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-28 * weight));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(18 * side * weight));
            }
            case 2 -> { // dash: brace the hand beside the body
                matrices.translate(0.18f * side * weight, -0.24f * weight, 0.12f * weight);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(38 * side * weight));
            }
            case 3 -> { // slam: lift, then drive down through the recovery envelope
                matrices.translate(0, 0.40f * weight, -0.14f * weight);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-65 * weight));
            }
            default -> {
                matrices.translate(0.10f * side * weight, -0.08f * weight, -0.18f * weight);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-30 * side * weight));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(20 * side * weight));
            }
        }
    }
    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void rpgplayable$restore(AbstractClientPlayerEntity player, float tickProgress, float pitch,
            Hand hand, float swing, ItemStack stack, float equip, MatrixStack matrices,
            OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        matrices.pop();
    }
}
