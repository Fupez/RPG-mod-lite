package dev.fupez.rpgplayable.client.mixin;

import dev.fupez.rpgplayable.client.AbilityPlayerPoses;
import dev.fupez.rpgplayable.client.AbilityPoseState;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs for the player model and its armor, after vanilla resets and poses each model. */
@Mixin(BipedEntityModel.class)
abstract class AbilityBipedModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("TAIL"))
    private void rpgplayable$applyAbilityPose(BipedEntityRenderState state, CallbackInfo ci) {
        if (state instanceof AbilityPoseState poseState)
            AbilityPlayerPoses.apply((BipedEntityModel<?>) (Object) this, state, poseState.rpgplayable$abilityPose());
    }
}
