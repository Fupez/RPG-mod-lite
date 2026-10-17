package dev.fupez.rpgplayable.client;

/** Immutable pose snapshot carried by the vanilla player render state. */
public interface AbilityPoseState {
    AbilityAnimations.Pose rpgplayable$abilityPose();
    void rpgplayable$abilityPose(AbilityAnimations.Pose pose);
}
