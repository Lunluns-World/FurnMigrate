package world.lunlun.furnmigrate.converter.designs

import world.lunlun.furnmigrate.converter.Design
import world.lunlun.furnmigrate.converter.FaceDir
import world.lunlun.furnmigrate.converter.design

/**
 * Hand-made replacement models. Frame: output model pixels, block = x/z 0..16, floor y = 0,
 * front of the furniture = −z (see [Design]). Sizes follow the original DiceFurniture piece
 * (the report compares them); only vanilla textures are used.
 */
object Designs {

    private val chair = design("Chair") {
        // Reference: footprint x/z 3..13, seat top y ≈ 7.9, backrest top ≈ 17.6 on the +z side.
        tex("plank", "block/oak_planks")
        tex("log", "block/stripped_oak_log")
        boxMirrorX(3.5, 0, 3.5, 5.5, 6, 5.5, "log")           // front legs
        boxMirrorX(3.5, 0, 10.5, 5.5, 17.5, 12.5, "log")      // back legs, run up into the backrest
        box(3, 6, 3, 13, 8, 10.5, "plank")                    // seat
        box(5.5, 6, 10.5, 10.5, 8, 12.5, "plank")             // seat between the back legs
        box(5.5, 2, 4, 10.5, 3, 5, "log")                     // front stretcher
        box(5.5, 2, 11, 10.5, 3, 12, "log")                   // back stretcher
        box(5.5, 9.5, 11, 10.5, 11, 12, "plank")              // backrest slats
        box(5.5, 12.5, 11, 10.5, 14, 12, "plank")
        box(3, 15.5, 10.5, 13, 17.5, 12.5, "plank")           // top rail
    }

    private val lantern = design("Lantern") {
        // Reference: base x/z 3..13 up to y ≈ 4.8, posts 4..12 up to ≈ 11, roof ≈ 11.4; torch light at the centre.
        tex("stone", "block/polished_blackstone")
        tex("stoneTop", "block/polished_blackstone_bricks")
        tex("wood", "block/dark_oak_planks")
        tex("glow", "block/glowstone")
        box(3, 0, 3, 13, 3, 13, "stone", faceTex = mapOf(FaceDir.UP to "stoneTop"))  // foot
        box(4, 3, 4, 12, 4.5, 12, "stone")                                            // plinth
        boxMirrorX(4, 4.5, 4, 5.5, 10.5, 5.5, "wood")                                 // corner posts
        boxMirrorX(4, 4.5, 10.5, 5.5, 10.5, 12, "wood")
        box(6, 4.5, 6, 10, 9.5, 10, "glow")                                           // glowing core
        box(3.5, 10.5, 3.5, 12.5, 12, 12.5, "wood")                                   // roof
        box(6, 12, 6, 10, 13.5, 10, "stone")                                          // cap
    }

    private val tv = design("TV") {
        // Reference: screen x −7..23, y 5..25, z ≈ 7.5..8.1 (2 blocks wide); stand at the centre on a small plate.
        tex("bezel", "block/gray_concrete")
        tex("screen", "block/black_concrete")
        tex("metal", "block/iron_block")
        box(-7, 5, 7.5, 23, 25, 9.5, "bezel")       // body
        box(-6, 6, 7.2, 22, 24, 7.5, "screen", faces = FaceDir.entries.toSet() - FaceDir.SOUTH)  // screen in front (−z)
        box(-2, 8, 9.5, 18, 22, 10.5, "bezel")      // back cover
        box(7, 1, 8, 9, 5, 9.5, "metal")            // neck
        box(3, 0, 5.5, 13, 1, 11.5, "metal")        // foot
    }

    val all: Map<String, Design> =
        (listOf(chair, lantern, tv) + SeatingAndTables.all + LightsAndDecor.all + CampAndLarge.all).associateBy { it.style }

    /** Styles that get no Oraxen furniture at all; the migrator retires them (see mapping `action: RETIRE`). */
    val retired: Map<String, String> = mapOf(
        "Barrels" to "minecraft:cauldron",
        "Fence" to "minecraft:oak_fence",
        "Campfire1" to "minecraft:campfire",
    )

    fun find(style: String): Design? = all[style] ?: all.values.firstOrNull { it.style.equals(style, ignoreCase = true) }
}
