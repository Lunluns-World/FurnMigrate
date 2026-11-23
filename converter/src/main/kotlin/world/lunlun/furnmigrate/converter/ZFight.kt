package world.lunlun.furnmigrate.converter

import world.lunlun.furnmigrate.common.Vec3

/**
 * Removes z-fighting: two elements with the same rotation whose faces in the same direction lie in one
 * plane and overlap flicker in game. The smaller of the two faces is pushed outward by [EPS] pixels so it
 * is drawn on top; when that would leave the legal model range (−16..32) the larger one is pulled in instead.
 * UVs are explicit, so the tiny move does not change what the faces show.
 */
object ZFight {
    const val EPS = 0.05
    private const val MIN = -16.0
    private const val MAX = 32.0
    private const val TOL = 1e-6

    /** A coplanar, same-facing, overlapping pair: element indices and the shared face. */
    data class Clash(val a: Int, val b: Int, val face: FaceDir)

    private fun axisOf(f: FaceDir) = when (f) { FaceDir.WEST, FaceDir.EAST -> 0; FaceDir.DOWN, FaceDir.UP -> 1; else -> 2 }
    private fun high(f: FaceDir) = f == FaceDir.EAST || f == FaceDir.UP || f == FaceDir.SOUTH
    private fun Vec3.get(i: Int) = when (i) { 0 -> x; 1 -> y; else -> z }
    private fun Vec3.with(i: Int, v: Double) = when (i) { 0 -> copy(x = v); 1 -> copy(y = v); else -> copy(z = v) }

    private fun plane(e: OutElement, f: FaceDir) = (if (high(f)) e.to else e.from).get(axisOf(f))

    private fun faceArea(e: OutElement, f: FaceDir): Double {
        val ax = axisOf(f)
        return (0..2).filter { it != ax }.fold(1.0) { acc, k -> acc * (e.to.get(k) - e.from.get(k)) }
    }

    fun clashes(elements: List<OutElement>): List<Clash> {
        val out = mutableListOf<Clash>()
        for (i in elements.indices) for (j in i + 1 until elements.size) {
            val a = elements[i]; val b = elements[j]
            if (a.rotation != b.rotation) continue
            for (f in FaceDir.entries) {
                if (f !in a.faces || f !in b.faces) continue
                if (kotlin.math.abs(plane(a, f) - plane(b, f)) > TOL) continue
                val ax = axisOf(f)
                val overlap = (0..2).filter { it != ax }.all { k ->
                    minOf(a.to.get(k), b.to.get(k)) - maxOf(a.from.get(k), b.from.get(k)) > TOL
                }
                if (overlap) out += Clash(i, j, f)
            }
        }
        return out
    }

    /** Moves face [f] of [e] by [d] pixels along its outward normal (negative = inward). */
    private fun move(e: OutElement, f: FaceDir, d: Double): OutElement {
        val ax = axisOf(f)
        return if (high(f)) e.copy(to = e.to.with(ax, e.to.get(ax) + d)) else e.copy(from = e.from.with(ax, e.from.get(ax) - d))
    }

    private fun inRange(e: OutElement) = (0..2).all { e.from.get(it) >= MIN - TOL && e.to.get(it) <= MAX + TOL }

    fun separate(elements: List<OutElement>): List<OutElement> {
        val els = elements.toMutableList()
        var guard = 0
        while (guard++ < 2000) {
            val c = clashes(els).firstOrNull() ?: break
            val (small, big) = if (faceArea(els[c.b], c.face) <= faceArea(els[c.a], c.face)) c.b to c.a else c.a to c.b
            val pushed = move(els[small], c.face, EPS)
            if (inRange(pushed)) els[small] = pushed else els[big] = move(els[big], c.face, -EPS)
        }
        return els
    }
}
