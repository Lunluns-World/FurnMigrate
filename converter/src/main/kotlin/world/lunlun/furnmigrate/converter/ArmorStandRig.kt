package world.lunlun.furnmigrate.converter

import world.lunlun.furnmigrate.common.Mat3
import world.lunlun.furnmigrate.common.Vec3

/**
 * Where an armor stand renders its head item, as transforms in block units.
 *
 * Frames:
 *  - item model space: pixels 0..16 of the vanilla item model;
 *  - stand-local: y up, origin at the stand's feet, face pointing −z (before the stand's yaw);
 *  - canonical: the .dModel frame (x east, y up, z south, origin = furniture block floor centre);
 *  - output: pixels of the Oraxen model shown by an ItemDisplay at the block centre.
 *
 * The numbers below are client rendering constants. They are NOT read from a file, so they are
 * calibration values: Phase 1 compares the result with the original furniture in game and they get
 * corrected here if anything is off. See docs/findings.md §7.
 */
data class Rig(
    /** Head pivot height of a normal stand (model translate 1.501 minus head offset 1/16). */
    val headPivotY: Double = 1.501 - 1.0 / 16.0,
    /** Head item layer: lift above the pivot and scale. */
    val headItemLift: Double = 0.25,
    val headItemScale: Double = 0.625,
    /** Scale of a small stand. */
    val smallScale: Double = 0.5,
    /** Extra yaw (degrees) between Oraxen yaw 0 and the canonical frame for the model geometry (ItemDisplay renders items turned 180°). */
    val modelYaw: Double = 180.0,
)

object ArmorStandRig {

    /** Vanilla ItemTransform.apply: translate(t/16) · rotateXYZ(r) · scale(s). Input/output in block units. */
    fun displayTransform(t: ItemTransform): Affine {
        val r = Mat3.rotXDeg(t.rotation.x) * Mat3.rotYDeg(t.rotation.y) * Mat3.rotZDeg(t.rotation.z)
        val s = Mat3.diag(t.scale.x, t.scale.y, t.scale.z)
        return Affine(r * s, t.translation * (1.0 / 16.0))
    }

    /** Armor stand pose (radians, as stored) → stand-local rotation. Model space is flipped (x, y) so X and Y change sign. */
    fun poseRotation(pose: Vec3): Mat3 = Mat3.rotZ(pose.z) * Mat3.rotY(-pose.y) * Mat3.rotX(-pose.x)

    /** Stand-local → canonical for a stand at [offset] with [yawDeg] (renderer: rotate Y by 180° − yaw). */
    fun standToCanonical(offset: Vec3, yawDeg: Double, small: Boolean, rig: Rig): Affine {
        val s = if (small) rig.smallScale else 1.0
        return Affine(Mat3.rotYDeg(180.0 - yawDeg) * s, offset)
    }

    /** Item model pixels → canonical, for an item worn on the head. */
    fun headItem(e: ModelEntity, model: ResolvedModel, rig: Rig): Affine {
        val toCentered = Affine(Mat3.IDENTITY * (1.0 / 16.0), Vec3(-0.5, -0.5, -0.5))
        val display = displayTransform(model.display["head"] ?: ItemTransform.IDENTITY)
        val layer = Affine(Mat3.IDENTITY * rig.headItemScale, Vec3(0.0, rig.headItemLift, 0.0))
        val pose = Affine(poseRotation(e.poseOf(BodyPart.HEAD)), Vec3(0.0, rig.headPivotY, 0.0))
        return standToCanonical(e.offset, e.yaw, e.small, rig) after pose after layer after display after toCentered
    }

    /** Bukkit default arm poses (degrees) used when a stand does not set them. */
    private val DEFAULT_RIGHT_ARM = Vec3(Math.toRadians(-15.0), 0.0, Math.toRadians(10.0))
    private val DEFAULT_LEFT_ARM = Vec3(Math.toRadians(-10.0), 0.0, Math.toRadians(-10.0))

    /**
     * Item model pixels → canonical, for an item held in a hand. APPROXIMATE (client constants of
     * ItemInHandLayer); only used as size reference for hand-made designs, never shipped.
     * Built in the renderer's flipped model space, then converted with F = diag(−1, −1, 1).
     */
    fun handItem(e: ModelEntity, model: ResolvedModel, leftHand: Boolean, rig: Rig): Affine {
        val side = if (leftHand) -1.0 else 1.0
        val arm = if (leftHand) e.pose[BodyPart.LEFT_ARM] ?: DEFAULT_LEFT_ARM else e.pose[BodyPart.RIGHT_ARM] ?: DEFAULT_RIGHT_ARM
        val toCentered = Affine(Mat3.IDENTITY * (1.0 / 16.0), Vec3(-0.5, -0.5, -0.5))
        val d = model.display["thirdperson_righthand"] ?: ItemTransform.IDENTITY
        val display = if (leftHand) displayTransform(d.copy(rotation = Vec3(d.rotation.x, -d.rotation.y, -d.rotation.z))) else displayTransform(d)
        val modelSpace = Affine(Mat3.IDENTITY, Vec3(-5.0 / 16.0 * side, 2.0 / 16.0, 0.0)) after
            Affine.linear(Mat3.rotZ(arm.z) * Mat3.rotY(arm.y) * Mat3.rotX(arm.x)) after
            Affine.linear(Mat3.rotXDeg(-90.0) * Mat3.rotYDeg(180.0)) after
            Affine.translate(Vec3(side / 16.0, 0.125, -0.625)) after display after toCentered
        val flip = Affine(Mat3.diag(-1.0, -1.0, 1.0), Vec3(0.0, rig.headPivotY + 1.0 / 16.0, 0.0))
        return standToCanonical(e.offset, e.yaw, e.small, rig) after flip after modelSpace
    }

    /**
     * Block model pixels → canonical, for a real block FurnitureLib places at [offset]
     * (block = floor(start + 0.5 + offset)), with blockstate rotation [xRot]/[yRot].
     */
    fun placedBlock(offset: Vec3, xRot: Int, yRot: Int): Affine {
        val minCorner = Vec3(Math.floor(0.5 + offset.x) - 0.5, Math.floor(offset.y + 1e-9), Math.floor(0.5 + offset.z) - 0.5)
        val center = Vec3(8.0, 8.0, 8.0)
        val variant = Affine(Mat3.rotYDeg(-yRot.toDouble()) * Mat3.rotXDeg(-xRot.toDouble()), Vec3.ZERO)
        val aroundCenter = Affine.translate(center) after variant after Affine.translate(-center)
        return Affine(Mat3.IDENTITY * (1.0 / 16.0), minCorner) after aroundCenter
    }

    /** Canonical → output model pixels (ItemDisplay at the block centre, display_transform NONE). */
    fun canonicalToOutput(rig: Rig): Affine {
        val rotateScale = Affine(Mat3.rotYDeg(rig.modelYaw) * 16.0, Vec3.ZERO)
        return Affine.translate(Vec3(8.0, 8.0, 8.0)) after rotateScale after Affine.translate(Vec3(0.0, -0.5, 0.0))
    }
}
