package world.lunlun.furnmigrate.converter.designs

import world.lunlun.furnmigrate.common.Axis
import world.lunlun.furnmigrate.common.IVec3
import world.lunlun.furnmigrate.common.Vec3
import world.lunlun.furnmigrate.converter.FaceDir
import world.lunlun.furnmigrate.converter.design

/** Seats, benches and tables. Frame: see [world.lunlun.furnmigrate.converter.Design]; front = −z. */
object SeatingAndTables {

    private val sofa = design("Sofa") {
        handles("#DYE", "不保留染色（一律白色）")
        handles("projectData.functions", "同上（染色功能）")
        // 3 blocks along −x (design x −32..16), back on +z; 3 seats (from the original).
        tex("frame", "block/stripped_dark_oak_log")
        tex("cushion", "block/white_wool")
        tex("fabric", "block/light_gray_wool")
        tex("leg", "block/dark_oak_planks")
        for (x in listOf(-31.0, 13.0)) for (z in listOf(2.0, 12.0)) box(x, 0, z, x + 2, 2, z + 2, "leg")
        box(-31.5, 2, 1.5, 15.5, 4.5, 14.5, "frame")              // wooden base
        box(-31.5, 4.5, 11.5, 15.5, 15, 14.5, "fabric")           // back
        box(-31.5, 4.5, 1.5, -28, 10, 11.5, "fabric")             // arms
        box(12, 4.5, 1.5, 15.5, 10, 11.5, "fabric")
        box(-32, 10, 1, -27.5, 11, 12, "frame")                   // arm caps
        box(11.5, 10, 1, 16, 11, 12, "frame")
        val w = 40.0 / 3
        for (i in 0..2) {
            val x0 = -28 + i * w
            box(x0 + 0.2, 4.5, 2, x0 + w - 0.2, 7.5, 11.5, "cushion")     // seat cushions
            box(x0 + 0.5, 7.5, 9.5, x0 + w - 0.5, 14, 11.5, "cushion")    // back cushions
        }
        box(-26, 7.5, 9, -22, 11.5, 9.5, "fabric", rot = Axis.X to -22.5)  // throw pillows
        box(6, 7.5, 9, 10, 11.5, 9.5, "fabric", rot = Axis.X to -22.5)
    }

    private val campChair = design("CampChair") {
        // Reference: folding stool, x/z 4..12, seat top ≈ 8.
        tex("canvas", "block/green_wool")
        tex("wood", "block/stripped_oak_log")
        for (x in listOf(4.0, 11.0)) {
            box(x, -0.5, 7.5, x + 1, 8.5, 8.5, "wood", rot = Axis.X to 45.0, origin = Vec3(x + 0.5, 4.0, 8.0))   // crossed legs
            box(x, -0.5, 7.5, x + 1, 8.5, 8.5, "wood", rot = Axis.X to -45.0, origin = Vec3(x + 0.5, 4.0, 8.0))
            box(x, 7, 4, x + 1, 8, 12, "wood")                                                                    // side rails
        }
        box(5, 7.25, 4.5, 11, 7.75, 11.5, "canvas")                                                               // canvas seat
    }

    private val schoolChair = design("SchoolChair") {
        // Reference: seat/back x 4..12, z 4..13, top ≈ 18; back on +z.
        tex("metal", "block/iron_block")
        tex("seat", "block/birch_planks")
        for (x in listOf(4.0, 11.0)) {
            box(x, 0, 4.5, x + 1, 7, 5.5, "metal")              // front legs
            box(x, 0, 11, x + 1, 18, 12, "metal")               // back legs / back posts
        }
        box(4, 7, 4, 12, 8.5, 11, "seat")                       // seat
        box(5, 12.5, 11.25, 11, 17, 12.25, "seat")              // backrest
        box(5, 2, 4.75, 11, 2.75, 5.25, "metal")                // footrest bar
    }

    private val table = design("Table") {
        handles("#ITEM", "桌上的物品由 migrator 掉在原地")
        // Pedestal table; the top is exactly one block high and the block is a barrier, so things can stand on it.
        tex("top", "block/oak_planks")
        tex("edge", "block/stripped_oak_log")
        tex("post", "block/dark_oak_log")
        tex("foot", "block/dark_oak_planks")
        box(3, 0, 3, 13, 1, 13, "foot")
        box(5.5, 1, 5.5, 10.5, 2, 10.5, "foot")
        box(6.5, 2, 6.5, 9.5, 13.5, 9.5, "post")
        box(4.5, 13, 4.5, 11.5, 14, 11.5, "foot")
        box(0.5, 14, 0.5, 15.5, 15, 15.5, "edge")               // rim
        box(0, 15, 0, 16, 16, 16, "top")                        // top, flush with the barrier
        barriers(IVec3(0, 0, 0))
    }

    private val schoolTable = design("SchoolTable") {
        handles("#ITEM", "桌上的物品由 migrator 掉在原地")
        // School desk; top flush with the barrier block.
        tex("top", "block/birch_planks")
        tex("metal", "block/iron_block")
        tex("shelf", "block/light_gray_concrete")
        for (x in listOf(1.0, 14.0)) for (z in listOf(1.0, 14.0)) box(x, 0, z, x + 1, 14, z + 1, "metal")
        box(1, 3, 1.25, 15, 3.75, 1.75, "metal")                 // foot bars
        box(1, 3, 14.25, 15, 3.75, 14.75, "metal")
        box(0, 14, 0, 16, 16, 16, "top")                        // desk top
        box(2, 10, 3, 14, 10.75, 14, "shelf")                   // book shelf under the top
        box(2, 10.75, 13.25, 14, 14, 14, "shelf")               // back of the shelf
        box(1.5, 10, 3, 2, 14, 14, "shelf")
        box(14, 10, 3, 14.5, 14, 14, "shelf")
        barriers(IVec3(0, 0, 0))
    }

    private val largeTable = design("LargeTable") {
        handles("#TELLER", "盤子位置不能再放東西，純裝飾；原本放的東西由 migrator 掉在原地")
        // A full 3×3 table over the old footprint (design cells x −2..0, z 0..2), top flush with the
        // 3×3 barrier layer, placed and broken at the middle cell.
        anchor(1, 0, -1)
        tex("top", "block/spruce_planks")
        tex("edge", "block/stripped_spruce_log")
        tex("leg", "block/dark_oak_log")
        tex("cloth", "block/white_wool")
        tex("red", "block/red_wool")
        for (x in listOf(-31.0, 13.0)) for (z in listOf(1.0, 45.0)) box(x, 0, z, x + 2, 14, z + 2, "leg")
        box(-8.5, 0, 22.5, -7.5, 14, 25.5, "leg")                // centre support
        box(-10, 0, 21, -6, 1, 27, "leg")
        box(-29, 12.5, 1.5, 13, 14, 2.5, "edge")                 // aprons
        box(-29, 12.5, 45.5, 13, 14, 46.5, "edge")
        box(-30.5, 12.5, 3, -29.5, 14, 45, "edge")
        box(13.5, 12.5, 3, 14.5, 14, 45, "edge")
        for (cx in 0..2) for (cz in 0..2) {                       // top in block-sized tiles: planks are not stretched
            val x0 = -32.0 + 16 * cx; val z0 = 16.0 * cz
            box(x0, 14, z0, x0 + 16, 16, z0 + 16, "top")
        }
        box(-12, 16, 0, -4, 16.25, 48, "cloth")                  // table runner over the middle
        box(-12, 16, 0, -11, 16.3, 48, "red")
        box(-5, 16, 0, -4, 16.3, 48, "red")
        barrierGrid(-2, 0, 0, 2)
    }

    private val trunk = design("Trunk") {
        displayName("&c橫躺可以坐的長原木")   // the model says 座
        handles("#TO", "座位標記；3 個座位保留")
        // Rustic log bench 3 blocks along −x (design x −40..8): a log split flat on top, resting on two
        // stumps, with a branch stub and some moss. Seat height kept (3 seats from the original, y ≈ 11).
        tex("bark", "block/oak_log")
        tex("end", "block/oak_log_top")
        tex("cut", "block/stripped_oak_log")
        tex("cutTop", "block/stripped_oak_log_top")
        tex("moss", "block/moss_block")
        tex("shroom", "block/red_mushroom_block")
        tex("stem", "block/mushroom_stem")
        for (x in listOf(-34.0, -2.0)) {                         // stumps
            box(x, 0, 4, x + 8, 4, 12, "bark", faceTex = mapOf(FaceDir.UP to "end", FaceDir.DOWN to "end"))
        }
        box(-40, 4, 3, 8, 10, 13, "bark",                        // lower half of the log
            faceTex = mapOf(FaceDir.EAST to "end", FaceDir.WEST to "end"),
            uvTurn = setOf(FaceDir.NORTH, FaceDir.SOUTH, FaceDir.DOWN))
        box(-40, 10, 4, 8, 11, 12, "cut",                        // flat split seat
            faceTex = mapOf(FaceDir.EAST to "cutTop", FaceDir.WEST to "cutTop"),
            uvTurn = setOf(FaceDir.UP, FaceDir.NORTH, FaceDir.SOUTH))
        box(-40, 10, 3, 8, 10.5, 4, "bark", uvTurn = setOf(FaceDir.UP, FaceDir.NORTH, FaceDir.SOUTH))   // bark edges
        box(-40, 10, 12, 8, 10.5, 13, "bark", uvTurn = setOf(FaceDir.UP, FaceDir.NORTH, FaceDir.SOUTH))
        box(-22, 5, 13, -18, 9, 17, "bark", faceTex = mapOf(FaceDir.SOUTH to "end"))   // branch stub at the back
        box(-39, 4, 2.5, -30, 6.5, 3, "moss")                    // moss on the front
        box(-12, 9.5, 12.5, -3, 10.75, 13.5, "moss")             // and along the back edge
        box(3, 11, 9.5, 5, 13, 11.5, "stem")                      // a small mushroom at the end
        box(2, 13, 8.5, 6, 14.5, 12.5, "shroom")
    }

    private val hammock = design("Hammock") {
        handles("#PILLAR", "柱子位置標記，只是外形")
        // Posts at design x 8 and x −88 (barriers there, 2 high, from the original); the cloth sags between them.
        tex("post", "block/stripped_oak_log")
        tex("postTop", "block/stripped_oak_log_top")
        tex("foot", "block/oak_planks")
        tex("rope", "block/white_wool")
        tex("cloth", "block/red_wool")
        tex("stripe", "block/white_wool")
        tex("pillow", "block/white_wool")
        for (cx in listOf(8.0, -88.0)) {
            box(cx - 2.5, 0, 5.5, cx + 2.5, 31, 10.5, "post", faceTex = mapOf(FaceDir.UP to "postTop", FaceDir.DOWN to "postTop"))
            box(cx - 4, 0, 2, cx + 4, 1.5, 14, "foot")            // cross feet
            box(cx - 3, 29, 5, cx + 3, 30, 11, "foot")            // cap ring
        }
        for (z in listOf(3.5, 11.5)) {                            // ropes from the posts down to the spreader bars
            slantXY(-85.5, 27.0, -70.0, 16.0, z, z + 1, 0.75, "rope")
            slantXY(-10.0, 16.0, 5.5, 27.0, z, z + 1, 0.75, "rope")
        }
        box(-71, 15, 2.5, -69, 17, 13.5, "post")                  // spreader bars
        box(-11, 15, 2.5, -9, 17, 13.5, "post")
        // cloth: two slanted ends and a flat middle, so it sags (middle at the original seat height ≈ 12)
        box(-69.5, 13.5, 3, -54, 14.25, 13, "cloth", rot = Axis.Z to -22.5, origin = Vec3(-62.0, 13.75, 8.0))
        box(-26, 13.5, 3, -10.5, 14.25, 13, "cloth", rot = Axis.Z to 22.5, origin = Vec3(-18.0, 13.75, 8.0))
        box(-55, 11.25, 3, -25, 12, 13, "cloth")
        box(-55, 12, 3, -25, 12.25, 4, "stripe")
        box(-55, 12, 12, -25, 12.25, 13, "stripe")
        box(-60, 13, 4.5, -54, 15.5, 11.5, "pillow")              // pillow at one end
    }

    val all = listOf(sofa, campChair, schoolChair, table, schoolTable, largeTable, trunk, hammock)
}
