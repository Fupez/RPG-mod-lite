package dev.fupez.rpgplayable.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.Arm;

/** Native keyframes in radians; the same model hook also poses worn armor. */
public final class AbilityPlayerPoses {
    private AbilityPlayerPoses() { }

    public static void apply(BipedEntityModel<?> model, BipedEntityRenderState state, AbilityAnimations.Pose pose) {
        if (pose.weight() <= 0 || state.deathTime > 0 || state.isInPose(EntityPose.SLEEPING)
                || state.isGliding || state.isSwimming || state.usingRiptide) return;
        boolean rightHanded = state.mainArm != Arm.LEFT;
        float side = rightHanded ? 1 : -1;
        ModelPart main = rightHanded ? model.rightArm : model.leftArm;
        ModelPart off = rightHanded ? model.leftArm : model.rightArm;
        float twist, lean, drop;
        switch (pose.style()) {
            case 1 -> { // Gather magic close to the chest, then extend the catalyst and free palm.
                arm(main, pose, -1.85f, -1.45f, -.48f * side, -.10f * side, -.22f * side, -.05f * side);
                arm(off, pose, -1.30f, -1.55f, .55f * side, .25f * side, .30f * side, .08f * side);
                twist = pose.offset(-.13f * side, .10f * side);
                lean = pose.offset(-.08f, .12f);
                drop = pose.offset(-.20f, .35f);
                model.head.pitch += pose.offset(-.08f, .04f);
            }
            case 2 -> { // Low coiled stance releases into a forward, arms-back sprint silhouette.
                arm(main, pose, -.95f, .65f, -.18f * side, -.10f * side, .35f * side, .14f * side);
                arm(off, pose, -.70f, .85f, .18f * side, .12f * side, -.35f * side, -.14f * side);
                twist = pose.offset(-.12f * side, .08f * side);
                lean = pose.offset(.20f, .42f);
                drop = pose.offset(.65f, .40f);
                model.head.pitch += pose.offset(-.12f, -.20f);
                if (!state.hasVehicle) {
                    model.rightLeg.pitch += pose.offset(-.18f * side, .28f * side);
                    model.leftLeg.pitch += pose.offset(.18f * side, -.28f * side);
                }
            }
            case 3 -> { // Both arms lift overhead, then drive down with the torso at impact.
                arm(main, pose, -2.85f, -.62f, -.12f * side, -.16f * side, .12f * side, .32f * side);
                arm(off, pose, -2.70f, -.70f, .12f * side, .16f * side, -.12f * side, -.32f * side);
                twist = pose.offset(-.08f * side, .10f * side);
                lean = pose.offset(-.20f, .48f);
                drop = pose.offset(-.30f, 1.15f);
                model.head.pitch += pose.offset(-.16f, .16f);
                if (!state.hasVehicle) {
                    model.rightLeg.pitch += pose.offset(.12f, -.18f);
                    model.leftLeg.pitch += pose.offset(.12f, -.18f);
                }
            }
            default -> { // Shoulder-led diagonal cut with a clear cross-body follow-through.
                arm(main, pose, -1.95f, -1.00f, -.80f * side, .65f * side, .38f * side, -.70f * side);
                arm(off, pose, -.55f, -.75f, .30f * side, .12f * side, -.22f * side, -.35f * side);
                twist = pose.offset(-.35f * side, .42f * side);
                lean = pose.offset(-.06f, .15f);
                drop = pose.offset(-.10f, .40f);
                if (!state.hasVehicle) {
                    model.rightLeg.pitch += pose.offset(-.10f * side, .16f * side);
                    model.leftLeg.pitch += pose.offset(.10f * side, -.16f * side);
                }
            }
        }
        model.body.yaw += twist;
        model.body.pitch += lean;
        // Arms are siblings of the torso in the vanilla rig: rotate their shoulder origins too.
        shoulder(model.rightArm, twist, drop, lean);
        shoulder(model.leftArm, twist, drop, lean);
        model.body.originY += drop;
        model.head.originY += drop;
        model.head.yaw -= twist * .25f;
    }

    private static void arm(ModelPart arm, AbilityAnimations.Pose pose, float prepPitch, float hitPitch,
            float prepYaw, float hitYaw, float prepRoll, float hitRoll) {
        arm.pitch += (prepPitch + (hitPitch - prepPitch) * pose.strike() - arm.pitch) * pose.weight();
        arm.yaw += (prepYaw + (hitYaw - prepYaw) * pose.strike() - arm.yaw) * pose.weight();
        arm.roll += (prepRoll + (hitRoll - prepRoll) * pose.strike() - arm.roll) * pose.weight();
    }

    private static void shoulder(ModelPart arm, float twist, float drop, float lean) {
        float x = arm.originX, z = arm.originZ;
        float cos = (float) Math.cos(twist), sin = (float) Math.sin(twist);
        arm.originX = x * cos + z * sin;
        arm.originZ = z * cos - x * sin + 2 * (float) Math.sin(lean);
        arm.originY += drop;
    }
}
