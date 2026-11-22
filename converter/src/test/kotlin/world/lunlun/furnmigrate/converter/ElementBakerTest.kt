package world.lunlun.furnmigrate.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.lunlun.furnmigrate.common.Mat3
import world.lunlun.furnmigrate.common.Vec3

class ElementBakerTest {

    private val cube = Element(
        Vec3(2.0, 0.0, 4.0), Vec3(14.0, 3.0, 12.0), null,
        FaceDir.entries.associateWith { Face(null, "#t", 0) } + (FaceDir.NORTH to Face(listOf(0.0, 0.0, 4.0, 8.0), "#t", 90)),
    )

    private fun around8(m: Mat3, scale: Double = 1.0, shift: Vec3 = Vec3.ZERO): Affine {
        val c = Vec3(8.0, 8.0, 8.0)
        return Affine.translate(c + shift) after Affine(m * scale, Vec3.ZERO) after Affine.translate(-c)
    }

    /** World position → UV of every face corner. Baking must keep the texture glued to the same points. */
    private fun cornerUvs(from: Vec3, to: Vec3, faces: Map<FaceDir, Face>, place: (Vec3) -> Vec3): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for ((dir, f) in faces) {
            val uv = f.uv ?: ElementBaker.autoUv(dir, from, to)
            for (s in 0..1) for (t in 0..1) {
                val sign = ElementBaker.faceVertex(dir, s, t)
                val p = Vec3(if (sign[0] > 0) to.x else from.x, if (sign[1] > 0) to.y else from.y, if (sign[2] > 0) to.z else from.z)
                var st = intArrayOf(s, t)
                repeat(f.rotation / 90 % 4) { st = intArrayOf(st[1], 1 - st[0]) }
                val u = uv[0] + st[0] * (uv[2] - uv[0]); val v = uv[1] + st[1] * (uv[3] - uv[1])
                val w = place(p)
                out["%.3f,%.3f,%.3f".format(w.x, w.y, w.z)] = "%.3f,%.3f".format(u, v)
            }
        }
        return out
    }

    @Test
    fun `identity keeps the box and every face`() {
        val r = ElementBaker.bake(cube, Affine.IDENTITY) { it }
        assertEquals(cube.from, r.element.from)
        assertEquals(cube.to, r.element.to)
        assertNull(r.element.rotation)
        assertEquals(0.0, r.errorDeg, 1e-9)
        assertEquals(cornerUvs(cube.from, cube.to, cube.faces) { it }, cornerUvs(r.element.from, r.element.to, r.element.faces) { it })
    }

    @Test
    fun `90 degree rotations move faces and keep texture on the same corners`() {
        for (m in Mat3.AXIS_ALIGNED) {
            val tr = around8(m, 0.5, Vec3(1.0, 2.0, 3.0))
            val r = ElementBaker.bake(cube, tr) { it }
            assertNull(r.element.rotation, "axis-aligned rotation must be baked, not an element rotation")
            val expected = cornerUvs(cube.from, cube.to, cube.faces) { tr.apply(it) }
            val actual = cornerUvs(r.element.from, r.element.to, r.element.faces) { it }
            assertEquals(expected, actual, "rotation $m")
        }
    }

    @Test
    fun `small tilt becomes an element rotation about the transformed origin`() {
        val tr = around8(Mat3.rotXDeg(22.5))
        val r = ElementBaker.bake(cube, tr) { it }
        val rot = r.element.rotation!!
        assertEquals(22.5, rot.angle, 1e-9)
        assertTrue(rot.origin.approxEquals(Vec3(8.0, 8.0, 8.0)))
        assertEquals(0.0, r.errorDeg, 1e-6)
    }

    @Test
    fun `textures are renamed through the callback and unknown ones dropped`() {
        val el = cube.copy(faces = mapOf(FaceDir.UP to Face(null, "#a"), FaceDir.DOWN to Face(null, "#missing")))
        val r = ElementBaker.bake(el, Affine.IDENTITY) { if (it == "#a") "#0" else null }
        assertEquals(setOf(FaceDir.UP), r.element.faces.keys)
        assertEquals("#0", r.element.faces[FaceDir.UP]!!.texture)
    }
}
