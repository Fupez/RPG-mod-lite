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
abstract class AbilityHandMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void rpgplayable$windup(AbstractClientPlayerEntity player, float tickProgress, float pitch,
            Hand hand, float swing, ItemStack stack, float equip, MatrixStack matrices,
            OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        matrices.push();
        if (hand != Hand.MAIN_HAND) return;
        var pose = ClientAbilities.animation(player, tickProgress);
        if (pose.weight() <= 0) return;
        float side = player.getMainArm() == Arm.RIGHT ? 1 : -1;
        switch (pose.style()) {
            case 1 -> { // cast: palm and held catalyst extend toward the target
                matrices.translate(side * pose.offset(-.12f, -.06f), pose.offset(.12f, .03f), pose.offset(-.10f, -.40f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.offset(-15, -28)));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * pose.offset(18, 5)));
            }
            case 2 -> { // dash: brace the hand beside the body
                matrices.translate(side * pose.offset(.18f, .22f), pose.offset(-.18f, -.25f), pose.offset(.10f, .14f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * pose.offset(32, 42)));
            }
            case 3 -> { // slam: lift overhead, then drive down and recover from impact
                matrices.translate(0, pose.offset(.45f, -.12f), pose.offset(-.08f, -.30f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.offset(-85, 30)));
            }
            default -> {
                matrices.translate(side * pose.offset(.22f, -.20f), pose.offset(.15f, -.08f), pose.offset(.10f, -.30f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * pose.offset(-45, 40)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * pose.offset(-25, 30)));
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
