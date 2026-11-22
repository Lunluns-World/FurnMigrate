package world.lunlun.furnmigrate.common

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z

    fun approxEquals(o: Vec3, eps: Double = 1e-6) =
        abs(x - o.x) < eps && abs(y - o.y) < eps && abs(z - o.z) < eps

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
    }
}

/** Integer block offset. */
data class IVec3(val x: Int, val y: Int, val z: Int)

enum class Axis { X, Y, Z }

/** Row-major 3x3 matrix. Rotation helpers follow the right-handed convention (JOML / Minecraft `Axis.XP` etc.). */
class Mat3(val m: DoubleArray) {
    init {
        require(m.size == 9)
    }

    operator fun get(r: Int, c: Int) = m[r * 3 + c]

    operator fun times(o: Mat3): Mat3 {
        val r = DoubleArray(9)
        for (i in 0..2) for (j in 0..2) {
            var s = 0.0
            for (k in 0..2) s += this[i, k] * o[k, j]
            r[i * 3 + j] = s
        }
        return Mat3(r)
    }

    operator fun times(v: Vec3) = Vec3(
        this[0, 0] * v.x + this[0, 1] * v.y + this[0, 2] * v.z,
        this[1, 0] * v.x + this[1, 1] * v.y + this[1, 2] * v.z,
        this[2, 0] * v.x + this[2, 1] * v.y + this[2, 2] * v.z,
    )

    operator fun times(s: Double) = Mat3(DoubleArray(9) { m[it] * s })

    fun transpose() = Mat3(DoubleArray(9) { val r = it / 3; val c = it % 3; this[c, r] })

    fun trace() = this[0, 0] + this[1, 1] + this[2, 2]

    /** Angle in degrees between two pure rotations (this and [o]). */
    fun angleTo(o: Mat3): Double {
        val c = ((this.transpose() * o).trace() - 1.0) / 2.0
        return Math.toDegrees(acos(c.coerceIn(-1.0, 1.0)))
    }

    fun approxEquals(o: Mat3, eps: Double = 1e-9) = m.indices.all { abs(m[it] - o.m[it]) < eps }

    override fun toString() = m.joinToString(prefix = "Mat3[", postfix = "]") { "%.3f".format(it) }

    companion object {
        val IDENTITY = Mat3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))

        fun diag(x: Double, y: Double, z: Double) = Mat3(doubleArrayOf(x, 0.0, 0.0, 0.0, y, 0.0, 0.0, 0.0, z))

        fun rotX(rad: Double): Mat3 {
            val c = cos(rad); val s = sin(rad)
            return Mat3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c))
        }

        fun rotY(rad: Double): Mat3 {
            val c = cos(rad); val s = sin(rad)
            return Mat3(doubleArrayOf(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c))
        }

        fun rotZ(rad: Double): Mat3 {
            val c = cos(rad); val s = sin(rad)
            return Mat3(doubleArrayOf(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0))
        }

        fun rot(axis: Axis, deg: Double): Mat3 {
            val r = Math.toRadians(deg)
            return when (axis) {
                Axis.X -> rotX(r)
                Axis.Y -> rotY(r)
                Axis.Z -> rotZ(r)
            }
        }

        fun rotXDeg(d: Double) = rotX(Math.toRadians(d))
        fun rotYDeg(d: Double) = rotY(Math.toRadians(d))
        fun rotZDeg(d: Double) = rotZ(Math.toRadians(d))

        /** All 24 proper rotations that map axes onto axes. */
        val AXIS_ALIGNED: List<Mat3> by lazy {
            val out = mutableListOf<Mat3>()
            val perms = listOf(intArrayOf(0, 1, 2), intArrayOf(0, 2, 1), intArrayOf(1, 0, 2), intArrayOf(1, 2, 0), intArrayOf(2, 0, 1), intArrayOf(2, 1, 0))
            for (p in perms) for (sx in listOf(1.0, -1.0)) for (sy in listOf(1.0, -1.0)) for (sz in listOf(1.0, -1.0)) {
                val a = DoubleArray(9)
                val signs = doubleArrayOf(sx, sy, sz)
                for (row in 0..2) a[row * 3 + p[row]] = signs[row]
                val mat = Mat3(a)
                if (mat.det() > 0) out += mat
            }
            out
        }
    }

    fun det(): Double =
        this[0, 0] * (this[1, 1] * this[2, 2] - this[1, 2] * this[2, 1]) -
            this[0, 1] * (this[1, 0] * this[2, 2] - this[1, 2] * this[2, 0]) +
            this[0, 2] * (this[1, 0] * this[2, 1] - this[1, 1] * this[2, 0])
}

/** Normalizes an angle in degrees into [0, 360). */
fun normDeg(d: Double): Double {
    val r = d % 360.0
    return if (r < 0) r + 360.0 else r
}
