package dev.fupez.rpgplayable.client.mixin;

import dev.fupez.rpgplayable.client.AbilityAnimations;
import dev.fupez.rpgplayable.client.AbilityPoseState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
abstract class AbilityPlayerRenderStateMixin implements AbilityPoseState {
    @Unique private AbilityAnimations.Pose rpgplayable$abilityPose = AbilityAnimations.Pose.NONE;

    @Override public AbilityAnimations.Pose rpgplayable$abilityPose() { return rpgplayable$abilityPose; }
    @Override public void rpgplayable$abilityPose(AbilityAnimations.Pose pose) { rpgplayable$abilityPose = pose; }
}
