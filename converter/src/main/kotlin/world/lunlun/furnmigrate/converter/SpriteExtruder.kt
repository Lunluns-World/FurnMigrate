package world.lunlun.furnmigrate.converter

import world.lunlun.furnmigrate.common.Vec3

/**
 * Turns a flat item sprite into model elements, like vanilla `item/generated`: the sprite lies in
 * x 0..16, y 0..16 (image top = y 16), 1 px thick at z 7.5..8.5, front face = south.
 */
object SpriteExtruder {
    private const val TEX = "#layer0"

    fun plainSlab() = listOf(
        Element(
            Vec3(0.0, 0.0, 7.5), Vec3(16.0, 16.0, 8.5), null,
            mapOf(
                FaceDir.SOUTH to Face(listOf(0.0, 0.0, 16.0, 16.0), TEX),
                FaceDir.NORTH to Face(listOf(16.0, 0.0, 0.0, 16.0), TEX),
            ),
        ),
    )

    fun extrude(width: Int, height: Int, opaque: (Int, Int) -> Boolean): List<Element> {
        val px = 16.0 / width
        val py = 16.0 / height
        val out = mutableListOf<Element>()
        for (row in 0 until height) {
            var x = 0
            while (x < width) {
                if (!opaque(x, row)) { x++; continue }
                val start = x
                while (x < width && opaque(x, row)) x++
                val u0 = start * px; val u1 = x * px; val v0 = row * py; val v1 = (row + 1) * py
                val y0 = 16.0 - v1; val y1 = 16.0 - v0
                out += Element(
                    Vec3(u0, y0, 7.5), Vec3(u1, y1, 8.5), null,
                    mapOf(
                        FaceDir.SOUTH to Face(listOf(u0, v0, u1, v1), TEX),
                        FaceDir.NORTH to Face(listOf(u1, v0, u0, v1), TEX),
                        FaceDir.UP to Face(listOf(u0, v0, u1, v1), TEX),
                        FaceDir.DOWN to Face(listOf(u0, v0, u1, v1), TEX),
                        FaceDir.EAST to Face(listOf(u1 - px, v0, u1, v1), TEX),
                        FaceDir.WEST to Face(listOf(u0, v0, u0 + px, v1), TEX),
                    ),
                )
            }
        }
        return out
    }
}
