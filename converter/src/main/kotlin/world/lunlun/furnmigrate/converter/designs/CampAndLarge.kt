package world.lunlun.furnmigrate.converter.designs

import world.lunlun.furnmigrate.common.Axis
import world.lunlun.furnmigrate.common.Vec3
import world.lunlun.furnmigrate.converter.DesignBuilder
import world.lunlun.furnmigrate.converter.FaceDir
import world.lunlun.furnmigrate.converter.design

/** Camp fires, tents and the larger figures. Frame: see [world.lunlun.furnmigrate.converter.Design]; front = −z. */
object CampAndLarge {

    /** Stepped triangle closing the back of an A-frame tent (ridge at [cx], apex height [h], slope 45°). */
    private fun DesignBuilder.gable(cx: Double, h: Double, z0: Double, z1: Double, tex: String) {
        var y = 0.0
        while (y < h - 0.01) {
            val hw = (h - y - 2).coerceAtLeast(1.0)
            box(cx - hw, y, z0, cx + hw, minOf(y + 4, h), z1, tex)
            y += 4
        }
    }

    private val campfire2 = design("Campfire2") {
        // Reference: fire pit centred at (x 0, z 16) with a spit, stones x −8..8, z 8..24, height ≈ 15.
        tex("stone", "block/cobblestone")
        tex("log", "block/oak_log")
        tex("fire", "block/shroomlight")
        tex("stick", "block/stripped_oak_log")
        tex("food", "block/bone_block_side")
        val c = 16.0
        val ring = listOf(6.0 to 0.0, -6.0 to 0.0, 0.0 to 6.0, 0.0 to -6.0, 4.3 to 4.3, -4.3 to 4.3, 4.3 to -4.3, -4.3 to -4.3)
        for ((dx, dz) in ring) box(dx - 1.5, 0, c + dz - 1.5, dx + 1.5, 2.5, c + dz + 1.5, "stone")
        box(-4.5, 0, c - 1, 4.5, 2, c + 1, "log", uvTurn = setOf(FaceDir.NORTH, FaceDir.SOUTH, FaceDir.UP, FaceDir.DOWN))
        box(-4.5, 0, c - 1, 4.5, 2, c + 1, "log", rot = Axis.Y to 45.0, origin = Vec3(0.0, 1.0, c),
            uvTurn = setOf(FaceDir.NORTH, FaceDir.SOUTH, FaceDir.UP, FaceDir.DOWN))
        box(-2, 1.5, c - 2, 2, 4.5, c + 2, "fire")
        box(-8, 0, c - 0.5, -7, 14, c + 0.5, "stick")       // spit
        box(7, 0, c - 0.5, 8, 14, c + 0.5, "stick")
        box(-8, 13, c - 0.5, 8, 14, c + 0.5, "stick", uvTurn = setOf(FaceDir.NORTH, FaceDir.SOUTH))
        box(-2, 9.5, c - 1.5, 2, 13, c + 1.5, "food")
        lights("0,0,1 15")
    }

    private val tent1 = design("Tent1") {
        // Reference: A-frame along z (2..62), ridge at x −24 up to ≈ 37, eaves at x −60 and 12; real crafting table inside.
        anchor(2, 0, -1)          // px x −32..−16, z 16..32: under the ridge, in front of the crafting table
        tex("canvas", "block/white_wool")
        tex("pole", "block/oak_log")
        tex("poleEnd", "block/oak_log_top")
        slantXY(-60.0, 0.0, -24.0, 36.0, 2, 62, 1.0, "canvas")
        slantXY(12.0, 0.0, -24.0, 36.0, 2, 62, 1.0, "canvas")
        gable(-24.0, 36.0, 61.0, 62.0, "canvas")
        box(-26, 0, 2, -22, 36, 6, "pole")                                   // front pole
        box(-26, 0, 58, -22, 36, 62, "pole")                                 // back pole
        box(-26, 34, 1, -22, 38, 63, "pole", faceTex = mapOf(FaceDir.NORTH to "poleEnd", FaceDir.SOUTH to "poleEnd"),
            uvTurn = setOf(FaceDir.EAST, FaceDir.WEST))                       // ridge
        // The crafting table (x −32..−16, z 32..48) stays a real, usable block (mapping keep_blocks).
    }

    private val tent2 = design("Tent2") {
        // Reference: bigger A-frame along z (1..61), ridge at x −32 up to ≈ 40, eaves at x −73 and 9.
        // The bed (x −32..−16, z 16..48) and the chest (x −48..−32, z 32..48) stay real blocks (mapping keep_blocks):
        // beds can still be slept in and the chest keeps its contents without moving anything.
        handles("real container block", "箱子保留為真實方塊，內容不用搬")
        displayName("&c帳篷 #2")       // the model says 帳棚
        anchor(3, 0, -1)          // px x −48..−32, z 16..32: middle of the floor, next to the bed and chest
        tex("canvas", "block/white_wool")
        tex("pole", "block/oak_log")
        tex("poleEnd", "block/oak_log_top")
        slantXY(-73.0, 0.0, -32.0, 41.0, 2, 60, 1.0, "canvas")
        slantXY(9.0, 0.0, -32.0, 41.0, 2, 60, 1.0, "canvas")
        gable(-32.0, 41.0, 59.0, 60.0, "canvas")
        box(-34, 0, 1, -30, 41, 5, "pole")
        box(-34, 0, 56, -30, 41, 60, "pole")
        box(-34, 39, 0, -30, 43, 61, "pole", faceTex = mapOf(FaceDir.NORTH to "poleEnd", FaceDir.SOUTH to "poleEnd"),
            uvTurn = setOf(FaceDir.EAST, FaceDir.WEST))
    }

    private val tent3 = design("Tent3") {
        // Reference: narrow steep tent x −4..21, z 10..49, up to ≈ 41; real bed inside (cell z 32..48); seat at z ≈ 24.
        anchor(0, 0, -1)          // px z 16..32: middle of the tent (the seat cell), in front of the bed
        tex("canvas", "block/white_wool")
        tex("pole", "block/oak_log")
        slantXY(-4.0, 0.0, 8.0, 40.0, 10, 49, 1.0, "canvas")
        slantXY(20.0, 0.0, 8.0, 40.0, 10, 49, 1.0, "canvas")
        box(7, 0, 10, 9, 41, 12, "pole")
        box(7, 0, 47, 9, 41, 49, "pole")
        box(6.5, 40, 9.5, 9.5, 42, 49.5, "pole", uvTurn = setOf(FaceDir.EAST, FaceDir.WEST))
        // The bed (cell z 32..48) stays a real bed (mapping keep_blocks).
    }

    private val crossbow = design("Crossbow") {
        handles("#ARROW", "不保留射箭功能，變成純裝飾")
        // Reference: ballista on a stone slab (x/z 0..16, y 0..8), bow at y ≈ 15, bolt pointing forward (−z).
        tex("base", "block/smooth_stone")
        tex("log", "block/oak_log")
        tex("rail", "block/stripped_oak_log")
        tex("bow", "block/oak_planks")
        tex("string", "block/white_wool")
        tex("tip", "block/iron_block")
        box(0, 0, 0, 16, 8, 16, "base")
        box(6, 8, 6, 10, 13, 10, "log")
        box(7, 13, 0, 9, 15, 20, "rail", uvTurn = setOf(FaceDir.EAST, FaceDir.WEST))
        box(-1, 14.5, 4, 17, 15.5, 5, "bow")
        box(-1, 14.5, 5, 0, 15.5, 8, "bow")            // bow tips bent back
        box(16, 14.5, 5, 17, 15.5, 8, "bow")
        box(0, 14.75, 7.5, 16, 15.25, 8, "string")
        box(7.6, 15, 1, 8.4, 15.8, 14, "bow")          // bolt
        box(7.4, 14.9, -0.5, 8.6, 15.9, 1, "tip")
    }

    private val humanSkeleton = design("HumanSkeleton") {
        // Reference: skeleton lying flat along z (4..49), skull at z ≈ 40..49, x ≈ −16..11.
        tex("bone", "block/bone_block_side")
        tex("boneEnd", "block/bone_block_top")
        tex("eye", "block/black_concrete")
        box(-7, 0, 41, 1, 7, 49, "bone", faceTex = mapOf(FaceDir.UP to "boneEnd"))      // skull, face up
        box(-6, 7, 43.5, -4, 7.1, 45.5, "eye")
        box(-2, 7, 43.5, 0, 7.1, 45.5, "eye")
        box(-4.5, 6, 46.5, -1.5, 7.05, 47.5, "eye")                                      // nose
        box(-3.75, 0, 20, -2.25, 1.5, 41, "bone")                                        // spine
        for (z in listOf(27.0, 30.0, 33.0, 36.0)) box(-9, 0, z, 3, 1.25, z + 1.25, "bone")  // ribs
        box(-8, 0, 16, 2, 2, 20, "bone")                                                 // pelvis
        box(-12, 0, 22, -10.5, 1.25, 38, "bone")                                         // arms
        box(4.5, 0, 22, 6, 1.25, 38, "bone")
        box(-7.5, 0, 4, -6, 1.5, 16, "bone")                                             // legs
        box(0, 0, 4, 1.5, 1.5, 16, "bone")
    }

    private val snowGolem = design("SnowGolem") {
        // Reference: snowman on the barrier block, up to ≈ 30, stick arms x −9..25.
        tex("snow", "block/snow")
        tex("coal", "block/coal_block")
        tex("carrot", "block/orange_concrete")
        tex("hat", "block/black_concrete")
        tex("stick", "block/stripped_oak_log")
        box(1, 0, 1, 15, 13, 15, "snow")                 // bottom
        box(3, 13, 3, 13, 22, 13, "snow")                // middle
        box(4.5, 22, 4.5, 11.5, 29, 11.5, "snow")        // head
        box(3.5, 29, 3.5, 12.5, 30, 12.5, "hat")         // hat
        box(5, 30, 5, 11, 35, 11, "hat")
        box(6, 26, 4, 7, 27, 4.5, "coal")                // eyes
        box(9, 26, 4, 10, 27, 4.5, "coal")
        box(7.5, 24.5, 1, 8.5, 25.5, 4.5, "carrot")      // nose
        for (y in listOf(15.0, 18.0)) box(7.5, y, 2.5, 8.5, y + 1, 3, "coal")   // buttons
        slantXY(3.0, 18.0, -9.0, 22.0, 7.5, 8.5, 1.0, "stick")   // arms
        slantXY(13.0, 18.0, 25.0, 22.0, 7.5, 8.5, 1.0, "stick")
    }

    val all = listOf(campfire2, tent1, tent2, tent3, crossbow, humanSkeleton, snowGolem)
}
