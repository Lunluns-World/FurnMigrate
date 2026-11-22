package world.lunlun.furnmigrate.converter

import world.lunlun.furnmigrate.common.Axis
import world.lunlun.furnmigrate.common.Mat3
import world.lunlun.furnmigrate.common.Vec3
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min

/** p → linear·p + t. */
data class Affine(val linear: Mat3, val t: Vec3) {
    fun apply(p: Vec3) = linear * p + t

    /** this ∘ inner: apply [inner] first. */
    infix fun after(inner: Affine) = Affine(linear * inner.linear, linear * inner.t + t)

    companion object {
        val IDENTITY = Affine(Mat3.IDENTITY, Vec3.ZERO)
        fun translate(t: Vec3) = Affine(Mat3.IDENTITY, t)
        fun scale(s: Double) = Affine(Mat3.IDENTITY * s, Vec3.ZERO)
        fun linear(m: Mat3) = Affine(m, Vec3.ZERO)
    }
}

/** Java block models: one axis per element, angle ∈ {-45, -22.5, 0, 22.5, 45}. */
val LEGAL_ANGLES = listOf(-45.0, -22.5, 0.0, 22.5, 45.0)

/**
 * Best Java-model approximation of a rotation [q]: q ≈ E(axis, angle) · B, where B is one of the
 * 24 axis-aligned rotations (baked exactly into the box) and E is a legal element rotation.
 */
data class SnappedRotation(val baked: Mat3, val axis: Axis, val angle: Double, val errorDeg: Double) {
    val elementRotation: Mat3 get() = Mat3.rot(axis, angle)
}

object AngleSnapper {
    fun snap(q: Mat3): SnappedRotation {
        var best: SnappedRotation? = null
        for (b in Mat3.AXIS_ALIGNED) for (axis in Axis.entries) for (angle in LEGAL_ANGLES) {
            if (angle == 0.0 && axis != Axis.Y) continue
            val err = (Mat3.rot(axis, angle) * b).angleTo(q)
            val cur = best
            // Prefer no element rotation on ties so simple parts stay simple.
            if (cur == null || err < cur.errorDeg - 1e-6 || (abs(err - cur.errorDeg) <= 1e-6 && angle == 0.0 && cur.angle != 0.0)) {
                best = SnappedRotation(b, axis, angle, err)
            }
        }
        return best!!
    }

    /** Nearest legal single-axis angle for a plain angle in degrees (used by the report and tests). */
    fun snapAngle(deg: Double): Pair<Double, Double> {
        val steps = Math.round(deg / 22.5)
        val snapped = steps * 22.5
        return snapped to abs(deg - snapped)
    }
}

/** An element ready for the output model. Coordinates in output pixels; [faces] texture = output texture key. */
data class OutElement(
    val from: Vec3,
    val to: Vec3,
    val rotation: ElementRotation?,
    val faces: Map<FaceDir, Face>,
    val shade: Boolean,
)

data class BakeResult(val element: OutElement, val errorDeg: Double, val warning: String?)

object ElementBaker {

    private fun s01(b: Boolean) = if (b) 1 else -1

    /** Box vertex (as min/max sign per axis) at face corner (s, t), following Minecraft's per-face UV directions. */
    fun faceVertex(face: FaceDir, s: Int, t: Int): IntArray = when (face) {
        FaceDir.NORTH -> intArrayOf(s01(s == 0), s01(t == 0), -1)
        FaceDir.SOUTH -> intArrayOf(s01(s == 1), s01(t == 0), 1)
        FaceDir.EAST -> intArrayOf(1, s01(t == 0), s01(s == 0))
        FaceDir.WEST -> intArrayOf(-1, s01(t == 0), s01(s == 1))
        FaceDir.UP -> intArrayOf(s01(s == 1), 1, s01(t == 1))
        FaceDir.DOWN -> intArrayOf(s01(s == 1), -1, s01(t == 0))
    }

    /** Vanilla automatic UV when a face has no `uv`. */
    fun autoUv(face: FaceDir, from: Vec3, to: Vec3): List<Double> = when (face) {
        FaceDir.NORTH -> listOf(16 - to.x, 16 - to.y, 16 - from.x, 16 - from.y)
        FaceDir.SOUTH -> listOf(from.x, 16 - to.y, to.x, 16 - from.y)
        FaceDir.EAST -> listOf(16 - to.z, 16 - to.y, 16 - from.z, 16 - from.y)
        FaceDir.WEST -> listOf(from.z, 16 - to.y, to.z, 16 - from.y)
        FaceDir.UP -> listOf(from.x, from.z, to.x, to.z)
        FaceDir.DOWN -> listOf(from.x, 16 - to.z, to.x, 16 - from.z)
    }

    /** Face rotation by 90° clockwise: corner (s, t) shows what corner (t, 1 - s) showed. */
    private fun rot90(st: IntArray) = intArrayOf(st[1], 1 - st[0])
    private fun rot90Inv(st: IntArray) = intArrayOf(1 - st[1], st[0])

    private fun uvAt(uv: List<Double>, rotation: Int, s: Int, t: Int): DoubleArray {
        var st = intArrayOf(s, t)
        repeat(((rotation / 90) % 4 + 4) % 4) { st = rot90(st) }
        return doubleArrayOf(uv[0] + st[0] * (uv[2] - uv[0]), uv[1] + st[1] * (uv[3] - uv[1]))
    }

    /**
     * Moves [src] (in its own model's pixel space) by [transform] into output pixel space.
     * [transform] must be a similarity (rotation · uniform scale + translation).
     * [textureKey] maps a source texture reference (`#side`) to the output texture key (`#3`).
     */
    fun bake(src: Element, transform: Affine, textureKey: (String) -> String?): BakeResult {
        val lin = transform.linear
        val det = lin.det()
        val scale = cbrt(det)
        var warning: String? = null
        if (det <= 0) warning = "mirrored transform, element skipped"
        val q0 = lin * (1.0 / scale)
        if (!(q0.transpose() * q0).approxEquals(Mat3.IDENTITY, 1e-4)) warning = "non-uniform scale approximated"

        val origin = src.rotation?.origin ?: Vec3(8.0, 8.0, 8.0)
        val re = src.rotation?.let { Mat3.rot(it.axis, it.angle) } ?: Mat3.IDENTITY
        val snapped = AngleSnapper.snap(q0 * re)
        val b = snapped.baked
        val newOrigin = transform.apply(origin)

        // New axis-aligned box: corners of the source box, rotated by B only, about the rotation origin.
        var mn = Vec3(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
        var mx = Vec3(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE)
        for (sx in listOf(src.from.x, src.to.x)) for (sy in listOf(src.from.y, src.to.y)) for (sz in listOf(src.from.z, src.to.z)) {
            val p = (b * (Vec3(sx, sy, sz) - origin)) * scale + newOrigin
            mn = Vec3(min(mn.x, p.x), min(mn.y, p.y), min(mn.z, p.z))
            mx = Vec3(max(mx.x, p.x), max(mx.y, p.y), max(mx.z, p.z))
        }

        val bt = b.transpose()
        val faces = linkedMapOf<FaceDir, Face>()
        for ((dir, face) in src.faces) {
            val key = textureKey(face.texture) ?: continue
            val newDir = FaceDir.closest(b * dir.normal)
            val srcUv = face.uv ?: autoUv(dir, src.from, src.to)
            // UV at each new canonical corner, looked up through the source corner it came from.
            val uvNew = Array(2) { s -> Array(2) { t ->
                val v = faceVertex(newDir, s, t)
                val sv = bt * Vec3(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                val sign = intArrayOf(Math.round(sv.x).toInt(), Math.round(sv.y).toInt(), Math.round(sv.z).toInt())
                val (ss, tt) = sourceCorner(dir, sign)
                uvAt(srcUv, face.rotation, ss, tt)
            } }
            faces[newDir] = fitFace(uvNew, key)
        }
        val rotation = if (snapped.angle == 0.0) null else ElementRotation(newOrigin, snapped.axis, snapped.angle)
        return BakeResult(OutElement(mn, mx, rotation, faces, src.shade), snapped.errorDeg, warning)
    }

    private fun sourceCorner(face: FaceDir, sign: IntArray): Pair<Int, Int> {
        for (s in 0..1) for (t in 0..1) if (faceVertex(face, s, t).contentEquals(sign)) return s to t
        error("corner $sign is not on face $face")
    }

    /** Finds uv rect + rotation that reproduce the given per-corner UVs. */
    private fun fitFace(uvNew: Array<Array<DoubleArray>>, texture: String): Face {
        for (k in 0..3) {
            fun base(s: Int, t: Int): DoubleArray {
                var st = intArrayOf(s, t)
                repeat(k) { st = rot90Inv(st) }
                return uvNew[st[0]][st[1]]
            }
            val b00 = base(0, 0); val b10 = base(1, 0); val b01 = base(0, 1); val b11 = base(1, 1)
            if (abs(b10[1] - b00[1]) < 1e-6 && abs(b01[0] - b00[0]) < 1e-6) {
                return Face(listOf(b00[0], b00[1], b11[0], b11[1]), texture, k * 90)
            }
        }
        error("face UV could not be fitted")
    }
}
