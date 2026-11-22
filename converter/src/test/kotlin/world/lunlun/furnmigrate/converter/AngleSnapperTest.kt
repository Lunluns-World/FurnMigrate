package world.lunlun.furnmigrate.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import world.lunlun.furnmigrate.common.Axis
import world.lunlun.furnmigrate.common.Mat3

class AngleSnapperTest {

    @ParameterizedTest
    @CsvSource("0, 0, 0", "22.5, 22.5, 0", "30, 22.5, 7.5", "40, 45, 5", "-10, 0, 10", "-12, -22.5, 10.5", "90, 90, 0")
    fun `plain angle snaps to nearest 22_5 step`(deg: Double, snapped: Double, err: Double) {
        val (s, e) = AngleSnapper.snapAngle(deg)
        assertEquals(snapped, s, 1e-9)
        assertEquals(err, e, 1e-9)
    }

    @Test
    fun `multiples of 90 degrees are baked with no error and no element rotation`() {
        for (axis in Axis.entries) for (deg in listOf(90.0, 180.0, 270.0, -90.0)) {
            val r = AngleSnapper.snap(Mat3.rot(axis, deg))
            assertEquals(0.0, r.errorDeg, 1e-6, "$axis $deg")
            assertEquals(0.0, r.angle)
        }
    }

    @Test
    fun `30 degrees about X becomes a 22_5 element rotation with 7_5 degree error`() {
        val r = AngleSnapper.snap(Mat3.rotXDeg(30.0))
        assertEquals(Axis.X, r.axis)
        assertEquals(22.5, r.angle, 1e-9)
        assertEquals(7.5, r.errorDeg, 1e-6)
    }

    @Test
    fun `100 degrees about Z is 90 baked plus 0 or 22_5, error at most 11_25`() {
        val r = AngleSnapper.snap(Mat3.rotZDeg(100.0))
        assertEquals(10.0, r.errorDeg, 1e-6)
        assertTrue((r.elementRotation * r.baked).angleTo(Mat3.rotZDeg(100.0)) < 10.0001)
    }

    @Test
    fun `two-axis rotation is approximated and the error is reported`() {
        val q = Mat3.rotXDeg(30.0) * Mat3.rotYDeg(30.0)
        val r = AngleSnapper.snap(q)
        assertTrue(r.errorDeg > 1.0, "a two-axis rotation cannot be exact")
        assertEquals(r.errorDeg, (r.elementRotation * r.baked).angleTo(q), 1e-9)
    }

    @Test
    fun `there are 24 axis aligned rotations`() {
        assertEquals(24, Mat3.AXIS_ALIGNED.size)
    }
}
