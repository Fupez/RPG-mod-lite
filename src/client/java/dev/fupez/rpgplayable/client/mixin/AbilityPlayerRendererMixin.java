package dev.fupez.rpgplayable.client.mixin;

import dev.fupez.rpgplayable.client.AbilityAnimations;
import dev.fupez.rpgplayable.client.AbilityPoseState;
import dev.fupez.rpgplayable.client.ClientAbilities;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
abstract class AbilityPlayerRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void rpgplayable$captureAbilityPose(PlayerLikeEntity entity, PlayerEntityRenderState state,
            float tickDelta, CallbackInfo ci) {
        ((AbilityPoseState) state).rpgplayable$abilityPose(entity instanceof AbstractClientPlayerEntity player
                ? ClientAbilities.animation(player, tickDelta) : AbilityAnimations.Pose.NONE);
    }
}
