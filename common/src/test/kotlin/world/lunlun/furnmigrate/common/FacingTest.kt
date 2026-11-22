package world.lunlun.furnmigrate.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class FacingTest {

    @ParameterizedTest
    @CsvSource("0, SOUTH", "90, WEST", "180, NORTH", "270, EAST", "-90, EAST", "44, SOUTH", "46, WEST", "360, SOUTH")
    fun `FurnitureLib direction follows yawToFace opposite`(yaw: Double, expected: Facing.Dir) {
        assertEquals(expected, Facing.flDirection(yaw))
    }

    @Test
    fun `canonical frame is FurnitureLib yaw 180`() {
        val v = Vec3(1.0, 2.0, 3.0)
        assertEquals(v, Facing.flWorldOffset(v, 180.0))
    }

    @ParameterizedTest
    @CsvSource(
        // values from Oraxen FurnitureSeatTest (seats must match barriers)
        "0, 1, 0, 1, 0", "90, 1, 0, 0, 1", "180, 1, 0, -1, 0", "270, 1, 0, 0, -1",
        "0, 0, 1, 0, -1", "90, 0, 1, 1, 0", "180, 0, 1, 0, 1", "270, 0, 1, -1, 0",
    )
    fun `groundRotate copy matches Oraxen's own test vectors`(yaw: Float, x: Int, z: Int, ex: Int, ez: Int) {
        val r = Facing.oraxenGroundRotate(x, 0, z, yaw)
        assertEquals(ex, r.x)
        assertEquals(ez, r.z)
    }

    /**
     * The key invariant for barriers/seats/lights: a block that FurnitureLib puts at canonical offset c
     * must be where Oraxen puts the converted barrier, for every facing.
     */
    @ParameterizedTest
    @ValueSource(doubles = [0.0, 90.0, 180.0, 270.0, -90.0, -180.0])
    fun `relative barrier positions match FurnitureLib for every facing`(flYaw: Double) {
        for (x in -2..2) for (y in -1..2) for (z in -2..2) {
            val c = IVec3(x, y, z)
            val fl = Facing.flWorldOffset(Vec3(x.toDouble(), y.toDouble(), z.toDouble()), flYaw)
            val b = Facing.canonicalToOraxenBlock(c)
            val ox = Facing.oraxenGroundRotate(b.x, b.y, b.z, Facing.oraxenYaw(flYaw))
            assertEquals(IVec3(Math.round(fl.x).toInt(), Math.round(fl.y).toInt(), Math.round(fl.z).toInt()), ox, "c=$c flYaw=$flYaw")
        }
    }

    @ParameterizedTest
    @CsvSource("0, 180", "90, 270", "180, 0", "270, 90", "-90, 90", "89.6, 270", "359, 180")
    fun `oraxen yaw is FurnitureLib yaw plus offset, snapped`(flYaw: Double, expected: Float) {
        assertEquals(expected, Facing.oraxenYaw(flYaw))
    }

    @Test
    fun `hitbox rotation is the mirror of groundRotate at 90 degrees`() {
        // Documents why converter only uses centred hitboxes.
        val h = Facing.oraxenHitboxRotate(Vec3(1.0, 0.0, 0.0), 90f)
        val b = Facing.oraxenGroundRotate(1, 0, 0, 90f)
        assertEquals(0.0, h.x, 1e-9); assertEquals(-1.0, h.z, 1e-9)
        assertEquals(0, b.x); assertEquals(1, b.z)
    }
}
