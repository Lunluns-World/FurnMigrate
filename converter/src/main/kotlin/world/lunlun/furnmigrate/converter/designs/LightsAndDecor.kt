package world.lunlun.furnmigrate.converter.designs

import world.lunlun.furnmigrate.common.Axis
import world.lunlun.furnmigrate.common.Vec3
import world.lunlun.furnmigrate.converter.FaceDir
import world.lunlun.furnmigrate.converter.design

/** Lamps, candles, bins, boards and plants. Frame: see [world.lunlun.furnmigrate.converter.Design]; front = −z. */
object LightsAndDecor {

    private val streetlamp = design("Streetlamp") {
        handles("#LAMP", "改成常亮（Oraxen 光源 15）；原本的開關燈要靠 LightAPI")
        // Reference: pole 4 blocks high (barriers y 0..3), lamp head hanging in front (−z) at y ≈ 41..51.
        tex("base", "block/smooth_stone")
        tex("iron", "block/black_concrete")
        tex("glass", "block/glass")
        tex("glow", "block/glowstone")
        box(4, 0, 4, 12, 2, 12, "base")
        box(5, 2, 5, 11, 4, 11, "base")
        box(7, 4, 7, 9, 52, 9, "iron")                  // pole
        box(6.5, 4, 6.5, 9.5, 6, 9.5, "iron")           // collar
        box(7, 50, -6, 9, 52, 9, "iron")                // arm reaching forward
        box(7.5, 48, -5, 8.5, 50, -4, "iron")           // hanger
        box(4, 47, -9, 12, 48.5, -1, "iron")            // lamp cap
        box(6, 48.5, -7, 10, 49.5, -3, "iron")
        box(5, 40, -8, 11, 47, -2, "glass")             // glass box
        box(6.5, 41, -6.5, 9.5, 46, -3.5, "glow")       // light
        box(5, 39, -8, 11, 40, -2, "iron")              // bottom
        lights("0,2,-1 15")                              // just under the lamp head (the head cell itself is a barrier)
    }

    private val waxCandle = design("WaxCandle") {
        // Reference: candle block x/z 3..13, height ≈ 10, light 14.
        tex("plate", "block/gold_block")
        tex("wax", "block/white_concrete")
        tex("wick", "block/black_wool")
        tex("flame", "block/shroomlight")
        box(3, 0, 3, 13, 1, 13, "plate")
        box(5, 1, 5, 11, 9, 11, "wax")
        box(4.5, 7, 4.5, 6, 8.5, 6, "wax")              // drips
        box(10, 6, 10, 11.5, 8, 11.5, "wax")
        box(7.5, 9, 7.5, 8.5, 10.5, 8.5, "wick")
        box(7, 10.5, 7, 9, 13, 9, "flame")
    }

    private val trashCan = design("TrashCan") {
        handles("#TRASH", "改成 1 排儲物；裡面的東西由 migrator 搬進去")
        // Reference: bin x/z 3..13, height 10.
        tex("bin", "block/gray_concrete")
        tex("rim", "block/iron_block")
        tex("inside", "block/black_wool")
        box(4, 0, 4, 12, 1, 12, "inside")
        box(4, 0, 4, 12, 10, 5, "bin")
        box(4, 0, 11, 12, 10, 12, "bin")
        box(4, 0, 5, 5, 10, 11, "bin")
        box(11, 0, 5, 12, 10, 11, "bin")
        box(3.5, 10, 3.5, 12.5, 11, 4.5, "rim")
        box(3.5, 10, 11.5, 12.5, 11, 12.5, "rim")
        box(3.5, 10, 4.5, 4.5, 11, 11.5, "rim")
        box(11.5, 10, 4.5, 12.5, 11, 11.5, "rim")
        storage(1, "垃圾桶")                                // the original kept one item in the #TRASH# slot
    }

    private val mailBox = design("MailBox") {
        handles("#ELEMENT", "外形標記")
        // Reference: post with a box on top, x 4..12, z 0..16, top ≈ 26; barriers 2 high.
        tex("post", "block/spruce_log")
        tex("box", "block/red_concrete")
        tex("door", "block/red_terracotta")
        tex("flag", "block/yellow_concrete")
        box(7, 0, 7, 9, 17, 9, "post")
        box(6, 16, 3, 10, 17, 13, "post")               // bracket
        box(4.5, 17, 1, 11.5, 23, 15, "box")
        box(5.5, 23, 1, 10.5, 25, 15, "box")            // rounded top
        box(5, 17.5, 0.5, 11, 24, 1, "door")            // front door (−z)
        box(12, 19, 9, 12.5, 26, 10, "flag")            // flag post
        box(12, 24, 10, 12.5, 26, 13, "flag")
    }

    private val weaponStand = design("WeaponStand") {
        handles("#SLOT", "改成 1 排儲物；架上的武器由 migrator 搬進去")
        // Reference: rack x 0..16, z 4..12, height ≈ 15.
        tex("wood", "block/spruce_planks")
        tex("log", "block/spruce_log")
        box(1, 0, 4, 15, 2, 12, "wood")                 // base
        box(1, 2, 7, 3, 15, 9, "log")                   // uprights
        box(13, 2, 7, 15, 15, 9, "log")
        box(1, 13, 6.5, 15, 15, 9.5, "wood")            // top bar
        box(3, 7, 7.25, 13, 8.5, 8.75, "wood")          // middle bar
        for (x in listOf(4.5, 7.5, 10.5)) {             // slots
            box(x, 2, 5, x + 1, 3, 6, "log")
            box(x, 2, 10, x + 1, 3, 11, "log")
        }
        storage(1, "武器架")                                // the original held 3 weapons (#SLOT1..3)
    }

    private val blackBoard = design("BlackBoard") {
        handles("#ITEM", "不能再放東西，純裝飾；原本放的東西由 migrator 掉在原地")
        // Reference: 3 blocks wide along −x (x −32..17), board at the back (z ≈ 16), height ≈ 25.
        tex("frame", "block/spruce_planks")
        tex("board", "block/black_concrete")
        tex("tray", "block/oak_planks")
        tex("chalk", "block/white_concrete")
        box(-31, 0, 13, -29, 24, 16, "frame")           // legs
        box(13, 0, 13, 15, 24, 16, "frame")
        box(-31, 0, 10, -29, 1, 17, "frame")            // feet
        box(13, 0, 10, 15, 1, 17, "frame")
        box(-29, 6, 14, 13, 7, 16, "frame")             // bottom of the frame
        box(-29, 23, 14, 13, 24, 16, "frame")           // top of the frame
        box(-29, 7, 14.5, 13, 23, 15.5, "board")        // the board, facing −z
        box(-29, 6, 12.5, 13, 6.75, 14, "tray")         // chalk tray
        box(-20, 6.75, 13, -17, 7.25, 13.5, "chalk")
    }

    private val flowerPot = design("FlowerPot") {
        // Reference: pot x/z 5..11 with a plant up to ≈ 17; barrier on the block.
        tex("pot", "block/terracotta")
        tex("soil", "block/dirt")
        tex("trunk", "block/oak_log")
        tex("leaves", "block/azalea_leaves")
        box(5, 0, 5, 11, 6, 11, "pot")
        box(4.5, 5, 4.5, 11.5, 6, 11.5, "pot")          // rim
        box(5.5, 5.5, 5.5, 10.5, 5.75, 10.5, "soil")
        box(7.5, 5.75, 7.5, 8.5, 11, 8.5, "trunk")
        box(3.5, 10, 3.5, 12.5, 15, 12.5, "leaves")
        box(5, 15, 5, 11, 17, 11, "leaves")
    }

    private val cactusPlant = design("CactusPlant") {
        // Reference: potted cactus up to ≈ 36 (2+ blocks), x/z 3..13.
        tex("pot", "block/terracotta")
        tex("side", "block/cactus_side")
        tex("top", "block/cactus_top")
        val ends = mapOf(FaceDir.UP to "top", FaceDir.DOWN to "top")
        box(4, 0, 4, 12, 5, 12, "pot")
        box(5, 5, 5, 11, 34, 11, "side", faceTex = ends)                // trunk
        box(11, 15, 6.5, 14, 18, 9.5, "side", faceTex = ends)            // right arm
        box(12, 18, 6.5, 15, 27, 9.5, "side", faceTex = ends)
        box(2, 11, 6.5, 5, 14, 9.5, "side", faceTex = ends)              // left arm
        box(1, 14, 6.5, 4, 22, 9.5, "side", faceTex = ends)
    }

    private val sunshade = design("Sunshade") {
        handles("#ELEMENT", "外形標記")
        handles("#TOP", "外形標記")
        // Reference: parasol up to ≈ 41 over x/z −1..17; barrier 2 blocks up (pole).
        tex("base", "block/smooth_stone")
        tex("pole", "block/white_concrete")
        tex("green", "block/lime_wool")
        tex("white", "block/white_wool")
        tex("tip", "block/oak_planks")
        box(3, 0, 3, 13, 1.5, 13, "base")
        box(5.5, 1.5, 5.5, 10.5, 3, 10.5, "base")
        box(7.5, 3, 7.5, 8.5, 39, 8.5, "pole")
        box(7, 22, 7, 9, 23.5, 9, "pole")                    // runner on the pole
        // canopy: four panels sloping down from the top, striped green/white
        val o = Vec3(8.0, 38.0, 8.0)
        box(-4, 37, -4, 20, 38, 8, "green", rot = Axis.X to -22.5, origin = o)   // front
        box(-4, 37, 8, 20, 38, 20, "white", rot = Axis.X to 22.5, origin = o)    // back
        box(8, 37.25, -3, 20, 38.25, 19, "green", rot = Axis.Z to -22.5, origin = o)   // +x side
        box(-4, 37.25, -3, 8, 38.25, 19, "white", rot = Axis.Z to 22.5, origin = o)    // −x side
        box(7, 38, 7, 9, 40, 9, "tip")
    }

    val all = listOf(streetlamp, waxCandle, trashCan, mailBox, weaponStand, blackBoard, flowerPot, cactusPlant, sunshade)
}
