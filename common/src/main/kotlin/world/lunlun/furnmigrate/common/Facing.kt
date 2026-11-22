package world.lunlun.furnmigrate.common

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Rotation rules of both plugins, copied as pure functions so they can be unit-tested
 * against each other. Sources are listed in docs/findings.md §1 and §4.
 *
 * "Canonical frame" in this project = the offsets exactly as stored in a .dModel
 * (FurnitureLib applies no rotation when the start yaw maps to direction NORTH, i.e. yaw 180).
 * x = east, y = up, z = south, relative to the furniture's block floor centre.
 */
object Facing {

    enum class Dir { NORTH, EAST, SOUTH, WEST }

    /** FurnitureLib `LocationUtil.yawToFace(yaw)`: axis[round(yaw / 90) & 3], axis = {N, E, S, W}. */
    fun flYawToFace(yaw: Double): Dir = Dir.entries[Math.round(yaw / 90.0).toInt() and 3]

    fun opposite(d: Dir): Dir = when (d) {
        Dir.NORTH -> Dir.SOUTH
        Dir.SOUTH -> Dir.NORTH
        Dir.EAST -> Dir.WEST
        Dir.WEST -> Dir.EAST
    }

    /** `ModelHandler.spawn`: direction = yawToFace(startYaw).getOppositeFace(). */
    fun flDirection(startYaw: Double): Dir = opposite(flYawToFace(startYaw))

    /**
     * `Modelschematic.rotateVector(vector, direction)` for placeAbleSide TOP.
     * Returns the rotated offset and the yaw that is added to the part's yaw.
     */
    fun flRotate(v: Vec3, direction: Dir): Pair<Vec3, Double> = when (direction) {
        Dir.SOUTH -> Vec3(-v.x, v.y, -v.z) to 180.0
        Dir.WEST -> Vec3(v.z, v.y, -v.x) to 270.0
        Dir.EAST -> Vec3(-v.z, v.y, v.x) to 90.0
        Dir.NORTH -> v to 0.0
    }

    /** World offset (relative to the block floor centre) of a canonical point for a furniture placed with [startYaw]. */
    fun flWorldOffset(canonical: Vec3, startYaw: Double): Vec3 = flRotate(canonical, flDirection(startYaw)).first

    /**
     * Oraxen `BlockLocation.groundRotate(angle)` (BlockLocation.java:100), verbatim.
     * Used for barriers, seats (FurnitureSeat matches it, see FurnitureSeatTest) and `lights`.
     */
    fun oraxenGroundRotate(x: Int, y: Int, z: Int, angle: Float): IVec3 {
        val fixedAngle = 360 - angle
        val radians = Math.toRadians(fixedAngle.toDouble())
        val ox = Math.round(cos(radians) * x - sin(radians) * z).toInt()
        var oz = Math.round(sin(radians) * x - cos(radians) * z).toInt()
        if (fixedAngle % 180 > 1) oz = -oz
        return IVec3(ox, y, oz)
    }

    /**
     * Oraxen `FurnitureMechanic.rotateGroundOffset` (used for `hitboxes`). NOTE: rotates the
     * opposite way from [oraxenGroundRotate] — converter therefore only emits hitboxes at x=z=0.
     */
    fun oraxenHitboxRotate(v: Vec3, yaw: Float): Vec3 {
        val r = Math.toRadians(-yaw.toDouble())
        return Vec3(cos(r) * v.x - sin(r) * v.z, v.y, sin(r) * v.x + cos(r) * v.z)
    }

    /** Default `yaw_offset`: Oraxen yaw 0 must show the canonical frame, which FurnitureLib uses at yaw 180. */
    const val DEFAULT_YAW_OFFSET = 180.0

    /** Oraxen yaw to use for a FurnitureLib object placed at [flYaw]. Snapped to 90° (both plugins only use 4 facings). */
    fun oraxenYaw(flYaw: Double, yawOffset: Double = DEFAULT_YAW_OFFSET): Float {
        val snapped = (normDeg(flYaw) / 90.0).roundToInt() * 90.0
        return normDeg(snapped + yawOffset).toFloat()
    }

    /**
     * Canonical offset → Oraxen barrier/seat/light config offset (the frame Oraxen uses at yaw 0).
     * Derived in docs/findings.md: groundRotate(0) maps (x, z) → (x, -z).
     */
    /** World offset from the old piece's origin to the Oraxen furniture's block for mapping `anchor`. */
    fun anchorWorldOffset(anchor: IVec3, flYaw: Double): Vec3 =
        flWorldOffset(Vec3(anchor.x.toDouble(), anchor.y.toDouble(), anchor.z.toDouble()), flYaw)

    fun canonicalToOraxenGround(c: Vec3): Vec3 = Vec3(c.x, c.y, -c.z)

    fun canonicalToOraxenBlock(c: IVec3): IVec3 = IVec3(c.x, c.y, -c.z)
}
