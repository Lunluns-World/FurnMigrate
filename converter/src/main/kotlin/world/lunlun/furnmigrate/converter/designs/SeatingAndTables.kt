package world.lunlun.furnmigrate.converter.designs

import world.lunlun.furnmigrate.common.Axis
import world.lunlun.furnmigrate.common.Vec3
import world.lunlun.furnmigrate.converter.FaceDir
import world.lunlun.furnmigrate.converter.design

/** Seats, benches and tables. Frame: see [world.lunlun.furnmigrate.converter.Design]; front = −z. */
object SeatingAndTables {

    private val sofa = design("Sofa") {
        handles("#DYE", "不保留染色（一律白色）")
        handles("projectData.functions", "同上（染色功能）")
        // Reference: 3 blocks along −x (x −31..16), seat ≈ 6, back ≈ 12, back on +z; 3 seats.
        tex("frame", "block/light_gray_wool")
        tex("cushion", "block/white_wool")
        tex("leg", "block/dark_oak_planks")
        for (x in listOf(-31.0, 13.0)) for (z in listOf(2.5, 9.5)) box(x, 0, z, x + 2, 2, z + 2, "leg")
        box(-31, 2, 2, 15, 5, 12, "frame")                        // base
        box(-31, 5, 9.5, 15, 12, 12, "frame")                     // back
        box(-31, 5, 2, -28, 9, 9.5, "frame")                      // arms
        box(12, 5, 2, 15, 9, 9.5, "frame")
        val w = 40.0 / 3
        for (i in 0..2) {
            val x0 = -28 + i * w
            box(x0 + 0.25, 5, 2.5, x0 + w - 0.25, 7, 9.5, "cushion")     // seat cushions
            box(x0 + 0.5, 7, 8.5, x0 + w - 0.5, 11, 9.5, "cushion")      // back cushions
        }
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
        // Reference: pedestal table, top x/z 3..13 at y ≈ 13..15.
        tex("top", "block/oak_planks")
        tex("post", "block/dark_oak_log")
        tex("foot", "block/dark_oak_planks")
        box(4, 0, 4, 12, 1, 12, "foot")
        box(6, 1, 6, 10, 2, 10, "foot")
        box(7, 2, 7, 9, 13, 9, "post")
        box(5, 12, 5, 11, 13, 11, "foot")
        box(2.5, 13, 2.5, 13.5, 14.5, 13.5, "top")
    }

    private val schoolTable = design("SchoolTable") {
        handles("#ITEM", "桌上的物品由 migrator 掉在原地")
        // Reference: desk, top x/z ≈ 4..12 at y 15, legs to the floor.
        tex("top", "block/birch_planks")
        tex("metal", "block/iron_block")
        for (x in listOf(2.0, 13.0)) for (z in listOf(3.5, 11.5)) box(x, 0, z, x + 1, 13.5, z + 1, "metal")
        box(1, 13.5, 3, 15, 15, 13, "top")                     // desk top
        box(2.5, 10, 4.5, 13.5, 11, 12.5, "metal")              // book shelf under the top
        box(2.5, 11, 12, 13.5, 13.5, 12.5, "metal")             // back of the shelf
    }

    private val largeTable = design("LargeTable") {
        handles("#TELLER", "盤子位置不能再放東西，純裝飾；原本放的東西由 migrator 掉在原地")
        // Reference: 2×2 table, top x −22..8, z 9..39 at y ≈ 13..15.
        tex("top", "block/spruce_planks")
        tex("leg", "block/dark_oak_log")
        tex("cloth", "block/white_wool")
        for (x in listOf(-21.0, 5.0)) for (z in listOf(10.0, 36.0)) box(x, 0, z, x + 2, 13, z + 2, "leg")
        box(-21, 11, 10.5, 7, 13, 11.5, "top")                  // aprons
        box(-21, 11, 36.5, 7, 13, 37.5, "top")
        box(-22.5, 13, 8.5, 8.5, 14.5, 39.5, "top")             // table top
        box(-20, 14.5, 22, 6, 14.75, 26, "cloth")               // runner
    }

    private val trunk = design("Trunk") {
        handles("#TO", "座位標記；3 個座位保留")
        // Reference: log bench lying along −x, x −41..9, z 3..13, height 10; 3 seats.
        tex("bark", "block/oak_log")
        tex("end", "block/oak_log_top")
        tex("seat", "block/stripped_oak_log")
        tex("foot", "block/dark_oak_planks")
        box(-36, 0, 2, -32, 1.5, 14, "foot")
        box(0, 0, 2, 4, 1.5, 14, "foot")
        box(-40, 1, 3, 8, 9.5, 13, "bark",
            faceTex = mapOf(FaceDir.EAST to "end", FaceDir.WEST to "end"),
            uvTurn = setOf(FaceDir.NORTH, FaceDir.SOUTH, FaceDir.UP, FaceDir.DOWN))
        box(-39, 9.5, 5, 7, 10, 11, "seat", uvTurn = setOf(FaceDir.UP))   // flat seat cut
    }

    private val hammock = design("Hammock") {
        handles("#PILLAR", "柱子位置標記，只是外形")
        // Reference: posts at x 8 and x −88 (barriers there, 2 high), cloth sagging to y ≈ 12; seat in the middle.
        tex("post", "block/oak_log")
        tex("postTop", "block/oak_log_top")
        tex("rope", "block/white_wool")
        tex("cloth", "block/red_wool")
        tex("edge", "block/white_wool")
        for (cx in listOf(8.0, -88.0)) {
            box(cx - 3, 0, 5, cx + 3, 30, 11, "post", faceTex = mapOf(FaceDir.UP to "postTop", FaceDir.DOWN to "postTop"))
        }
        for (z in listOf(3.5, 11.5)) {
            slantXY(-84.0, 28.0, -68.0, 12.0, z, z + 1, 1.0, "rope")
            slantXY(-12.0, 12.0, 4.0, 28.0, z, z + 1, 1.0, "rope")
        }
        box(-68, 11.5, 3, -12, 12.5, 13, "cloth")
        box(-68, 12.5, 3, -12, 13, 4, "edge")
        box(-68, 12.5, 12, -12, 13, 13, "edge")
    }

    val all = listOf(sofa, campChair, schoolChair, table, schoolTable, largeTable, trunk, hammock)
}
