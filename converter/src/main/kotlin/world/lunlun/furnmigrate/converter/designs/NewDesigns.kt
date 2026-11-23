package world.lunlun.furnmigrate.converter.designs

import world.lunlun.furnmigrate.common.Axis
import world.lunlun.furnmigrate.common.IVec3
import world.lunlun.furnmigrate.common.Vec3
import world.lunlun.furnmigrate.converter.Design
import world.lunlun.furnmigrate.converter.FaceDir
import world.lunlun.furnmigrate.converter.OraxenShapedRecipe
import world.lunlun.furnmigrate.converter.design

/** A piece of furniture made for the server (no DiceFurniture original) and its crafting recipe. */
class NewPiece(val oraxenId: String, val design: Design, shape: List<String>, ingredients: Map<Char, String>) {
    val recipe = OraxenShapedRecipe(oraxenId, oraxenId, shape, ingredients)
}

/**
 * Household furniture added after the migration. Frame as in [Design]: block x/z 0..16, floor y 0, front
 * (towards the player who places it) −z. Oraxen offsets for seats and lights: x = (8 − px) / 16,
 * z = (pz − 8) / 16, seat y ≈ seat top / 16 − 1.75. Wall and ceiling pieces sit in the block in front of
 * the wall / under the ceiling, so they are drawn against the back (+z) / top (y 16) of the block.
 */
object NewDesigns {

    private val fridge = design("Fridge") {
        displayName("&f冰箱")
        tex("body", "block/white_concrete")
        tex("door", "block/quartz_block_bottom")
        tex("handle", "block/iron_block")
        tex("base", "block/gray_concrete")
        tex("seal", "block/light_gray_concrete")
        box(1, 1, 2, 15, 31.5, 15, "body")                      // cabinet
        box(1.5, 0, 2.5, 14.5, 1, 14.5, "base")                 // plinth
        box(1.25, 1.25, 1.5, 14.75, 19.5, 2, "seal")            // door seals
        box(1.25, 20, 1.5, 14.75, 31.25, 2, "seal")
        box(1.5, 1.5, 1, 14.5, 19.25, 1.5, "door")              // fridge door
        box(1.5, 20.25, 1, 14.5, 31, 1.5, "door")               // freezer door
        box(12, 9, 0.25, 13, 17.5, 1, "handle")                 // handles
        box(12, 21.5, 0.25, 13, 27, 1, "handle")
        box(3, 31.5, 4, 13, 32, 13, "seal")                     // top vent
        barriers(IVec3(0, 0, 0), IVec3(0, 1, 0))
        storage(3, "冰箱")
    }

    private val ceilingFan = design("CeilingFan") {
        displayName("&f吊扇")
        tex("metal", "block/white_concrete")
        tex("rod", "block/iron_block")
        tex("blade", "block/dark_oak_planks")
        tex("glow", "block/pearlescent_froglight_top")
        box(6, 15, 6, 10, 16, 10, "metal")                      // ceiling canopy
        box(7.5, 10, 7.5, 8.5, 15, 8.5, "rod")
        box(5, 7.5, 5, 11, 10, 11, "metal")                     // motor
        box(6, 5.5, 6, 10, 7.5, 10, "glow")                     // light
        box(7, 5, 7, 9, 5.5, 9, "metal")
        // four blades, slightly pitched, on short arms
        box(11, 8.25, 6.5, 26, 8.75, 9.5, "blade", rot = Axis.X to 22.5, origin = Vec3(18.5, 8.5, 8.0))
        box(-10, 8.25, 6.5, 5, 8.75, 9.5, "blade", rot = Axis.X to -22.5, origin = Vec3(-2.5, 8.5, 8.0))
        box(6.5, 8.25, 11, 9.5, 8.75, 26, "blade", rot = Axis.Z to -22.5, origin = Vec3(8.0, 8.5, 18.5))
        box(6.5, 8.25, -10, 9.5, 8.75, 5, "blade", rot = Axis.Z to 22.5, origin = Vec3(8.0, 8.5, -2.5))
        placing(floor = false, roof = true)
        lights("0,0,0 14")
        hitbox(1.0, 1.0)
    }

    private val computer = design("Computer") {
        displayName("&f電腦")
        tex("case", "block/black_concrete")
        tex("screen", "block/light_blue_concrete")
        tex("desk", "block/gray_concrete")
        tex("key", "block/light_gray_concrete")
        tex("white", "block/white_concrete")
        tex("led", "block/lime_concrete")
        box(1.5, 4, 9.5, 12.5, 12.5, 10.5, "case")              // monitor
        box(2, 4.5, 9.25, 12, 12, 9.5, "screen", faces = FaceDir.entries.toSet() - FaceDir.SOUTH)
        box(6.5, 1, 10.5, 7.5, 5, 11.5, "desk")                 // neck
        box(4.5, 0, 9, 9.5, 1, 13, "desk")                      // foot
        box(1.5, 0, 2.5, 11.5, 0.75, 6.5, "desk")               // keyboard
        for (r in 0..2) box(2.25, 0.75, 3.25 + r * 1.1, 10.75, 1, 3.95 + r * 1.1, "key")
        box(12.5, 0, 3.5, 14, 0.75, 5.5, "white")               // mouse
        box(13.5, 0, 8, 15.75, 10, 15, "case")                  // tower
        box(13.25, 8, 8.5, 13.5, 8.75, 9.25, "led")             // power light
        hitbox(1.0, 0.8)
    }

    private val curtain = design("Curtain") {
        displayName("&f窗簾")
        tex("rod", "block/stripped_dark_oak_log")
        tex("light", "block/white_wool")
        tex("shade", "block/light_gray_wool")
        // close to the window behind it (a glass block face is at z 16), but not on it (no flicker)
        box(0, 30, 14.25, 16, 31, 15.25, "rod")
        box(-0.5, 29.5, 13.75, 0.5, 31.5, 15.75, "rod")         // finials
        box(15.5, 29.5, 13.75, 16.5, 31.5, 15.75, "rod")
        // folds: alternating strips, a little forward and back
        for (i in 0 until 8) {
            val x0 = i * 2.0
            if (i % 2 == 0) box(x0, 1, 13.75, x0 + 2, 30, 14.75, "light")
            else box(x0, 1, 14.75, x0 + 2, 30, 15.75, "shade")
        }
        box(0, 0.5, 13.75, 16, 1.5, 15.75, "shade")             // hem
        placing(floor = true, wall = true)
        hitbox(1.0, 2.0)
    }

    private val airConditioner = design("AirConditioner") {
        displayName("&f冷氣")
        tex("body", "block/white_concrete")
        tex("vent", "block/light_gray_concrete")
        tex("dark", "block/gray_concrete")
        tex("led", "block/lime_concrete")
        box(-1, 8, 10, 17, 15, 16, "body")                      // indoor unit against the wall
        box(-0.5, 9.5, 9.5, 16.5, 14.5, 10, "body")             // front panel
        box(0, 8.5, 9, 16, 9.25, 10, "vent", rot = Axis.X to -22.5, origin = Vec3(8.0, 9.0, 9.5))   // louver
        box(0.5, 8, 10, 15.5, 8.5, 15, "dark")                  // air outlet
        box(12, 12.5, 9.25, 13, 13, 9.5, "led")                 // display
        box(13.5, 12.5, 9.25, 14.5, 13, 9.5, "dark")
        placing(floor = false, wall = true)
        hitbox(1.0, 1.0)
    }

    private val toilet = design("Toilet") {
        displayName("&f馬桶")
        tex("china", "block/quartz_block_bottom")
        tex("seat", "block/white_concrete")
        tex("water", "block/light_blue_concrete")
        tex("metal", "block/iron_block")
        box(5, 0, 5, 11, 5, 11.5, "china")                      // pedestal
        box(4, 5, 2, 12, 7.5, 12, "china")                      // bowl
        box(5.5, 7, 3.5, 10.5, 7.25, 10, "water")
        box(3.75, 7.5, 1.75, 12.25, 8, 3.25, "seat")            // seat ring
        box(3.75, 7.5, 3.25, 5.25, 8, 11, "seat")
        box(10.75, 7.5, 3.25, 12.25, 8, 11, "seat")
        box(3.75, 7.5, 9.5, 12.25, 8, 11, "seat")
        box(4, 8, 10.5, 12, 17.5, 11.25, "seat", rot = Axis.X to -22.5, origin = Vec3(8.0, 8.0, 11.0))   // open lid
        box(3, 7, 11.5, 13, 18, 15.5, "china")                  // tank
        box(2.5, 18, 11, 13.5, 19, 16, "china")                 // tank lid
        box(11, 16, 11, 12.5, 16.75, 11.5, "metal")             // flush lever
        seats("0,-1.25,-0.1 0")
    }

    private val sink = design("Sink") {
        displayName("&f洗手台")
        tex("china", "block/quartz_block_bottom")
        tex("inside", "block/white_concrete")
        tex("metal", "block/iron_block")
        box(6, 0, 8, 10, 10, 12, "china")                       // pedestal
        box(5, 0, 7, 11, 1, 13, "china")
        box(2, 10, 3, 14, 11, 14, "inside")                     // basin floor
        box(2, 11, 3, 14, 14, 4, "china")                       // basin walls
        box(2, 11, 13, 14, 14, 14, "china")
        box(2, 11, 4, 3, 14, 13, "china")
        box(13, 11, 4, 14, 14, 13, "china")
        box(7.75, 11, 7.75, 8.25, 11.1, 8.25, "metal")          // drain
        box(2, 14, 13, 14, 18, 14, "china")                     // backsplash
        box(7.25, 14, 11, 8.75, 17, 13, "metal")                // tap
        box(7.25, 16, 8.5, 8.75, 17, 11, "metal")
        box(4.5, 14, 11.5, 6, 15, 12.5, "metal")                // knobs
        box(10, 14, 11.5, 11.5, 15, 12.5, "metal")
        barriers(IVec3(0, 0, 0))
    }

    private val bathtub = design("Bathtub") {
        displayName("&f浴缸")
        // 2 blocks long, centred on the block (design x −8..24); two seats inside.
        tex("china", "block/quartz_block_bottom")
        tex("gold", "block/gold_block")
        tex("metal", "block/iron_block")
        tex("water", "block/light_blue_concrete")
        for (x in listOf(-6.0, 20.0)) for (z in listOf(2.0, 12.0)) box(x, 0, z, x + 2, 1.5, z + 2, "gold")   // feet
        box(-7, 1.5, 1.5, 23, 3, 14.5, "china")                 // bottom
        box(-7, 3, 1.5, 23, 10, 3, "china")                     // sides
        box(-7, 3, 13, 23, 10, 14.5, "china")
        box(-7, 3, 3, -5.5, 10, 13, "china")
        box(21.5, 3, 3, 23, 10, 13, "china")
        box(-7.5, 10, 1, 23.5, 11, 3, "china")                  // rolled rim
        box(-7.5, 10, 13, 23.5, 11, 15, "china")
        box(-7.5, 10, 3, -5.5, 11, 13, "china")
        box(21.5, 10, 3, 23.5, 11, 13, "china")
        box(-5.5, 3, 3, 21.5, 6.5, 13, "water", faces = setOf(FaceDir.UP))
        box(21, 11, 7.25, 22.5, 14, 8.75, "metal")              // tap
        box(18.5, 13, 7.25, 22.5, 14, 8.75, "metal")
        seats("0.5,-1.5,0 0", "-0.5,-1.5,0 0")
        hitbox(2.0, 0.7)
    }

    private val tvCabinet = design("TVCabinet") {
        displayName("&f電視櫃")
        // Low cabinet 2 blocks wide (design x −16..16: this block and the one to the placer's right), top
        // flush at one block so a TV or anything else stands on the barriers. Doors either side, an open
        // shelf with a game console in the middle.
        tex("wood", "block/dark_oak_planks")
        tex("top", "block/stripped_dark_oak_log")
        tex("door", "block/spruce_planks")
        tex("inside", "block/black_concrete")
        tex("metal", "block/iron_block")
        tex("console", "block/gray_concrete")
        tex("led", "block/lime_concrete")
        for (x in listOf(-15.0, 13.5)) for (z in listOf(1.5, 14.0)) box(x, 0, z, x + 1.5, 2, z + 1.5, "wood")   // feet
        box(-16, 2, 4, 16, 15, 16, "wood", faceTex = mapOf(FaceDir.NORTH to "inside"))   // carcass, dark inside the shelf
        box(-16, 2, 1, -5, 15, 4, "wood")                       // door sections
        box(5, 2, 1, 16, 15, 4, "wood")
        box(-5, 2, 1, 5, 3, 4, "wood")                          // shelf bottom and middle board
        box(-5, 8.5, 1, 5, 9.5, 4, "wood")
        box(-16, 15, 0.5, 16, 16, 16, "top", uvTurn = setOf(FaceDir.UP, FaceDir.DOWN))   // top board
        box(-15, 3, 0.5, -5.5, 14, 1, "door")                   // doors
        box(5.5, 3, 0.5, 15, 14, 1, "door")
        box(-7, 7.5, 0, -6.5, 10, 0.5, "metal")                 // handles
        box(6.5, 7.5, 0, 7, 10, 0.5, "metal")
        box(-3.5, 3, 1.75, 3.5, 4.5, 3.75, "console")           // console with a power light
        box(2, 3.5, 1.6, 2.75, 4, 1.75, "led")
        box(-2.5, 9.5, 2, 0.5, 13, 3, "door")                   // a few books on the upper shelf
        box(0.75, 9.5, 2, 1.75, 12, 3, "inside")
        barrierGrid(-1, 0, 0, 0)
        storage(2, "電視櫃")
    }

    private val wallTv = design("WallTV") {
        displayName("&f壁掛電視")
        // The same screen as the floor TV (2 blocks wide), hung flat against the wall at the back of the block.
        tex("bezel", "block/gray_concrete")
        tex("screen", "block/black_concrete")
        tex("metal", "block/iron_block")
        tex("led", "block/red_concrete")
        box(-7, 1, 13.5, 23, 19, 15, "bezel")                   // body
        box(-6, 2, 13.25, 22, 18, 13.5, "screen", faces = FaceDir.entries.toSet() - FaceDir.SOUTH)
        box(7.5, 1.25, 13.2, 8.5, 1.75, 13.5, "led")            // standby light
        box(2, 6, 15, 14, 14, 16, "metal")                      // wall bracket
        placing(floor = false, wall = true)
        hitbox(1.5, 1.25)
    }

    /**
     * The server logo on a 2×2 sign, standing on the floor or hung on a wall. Its texture is not in the repo:
     * `custom-textures/furniture/logo.png` (see README); without it the converter leaves this piece out.
     */
    private val logo = design("ServerLogo") {
        displayName("&d侖侖伺服器 Logo")
        tex("logo", "lunworld:furniture/logo")
        tex("frame", "block/white_concrete")
        tex("back", "block/quartz_block_bottom")
        box(-7, 1, 14, 23, 31, 15.5, "back")                    // board
        box(-7, 1, 13.9, 23, 31, 14, "logo", faces = setOf(FaceDir.NORTH))   // the logo, facing −z
        box(-8, 0, 13.5, 24, 1, 16, "frame")                    // frame
        box(-8, 31, 13.5, 24, 32, 16, "frame")
        box(-8, 1, 13.5, -7, 31, 16, "frame")
        box(23, 1, 13.5, 24, 31, 16, "frame")
        placing(floor = true, wall = true)
        hitbox(2.0, 2.0)
    }

    val all: List<NewPiece> = listOf(
        NewPiece("lun_fridge", fridge, listOf("AAA", "ABA", "ACA"), mapOf('A' to "IRON_INGOT", 'B' to "CHEST", 'C' to "SNOW_BLOCK")),
        NewPiece("lun_ceiling_fan", ceilingFan, listOf("_A_", "BCB"), mapOf('A' to "CHAIN", 'B' to "DARK_OAK_SLAB", 'C' to "GLOWSTONE")),
        NewPiece("lun_computer", computer, listOf("AAA", "ABA", "CCC"), mapOf('A' to "BLACK_STAINED_GLASS_PANE", 'B' to "REDSTONE", 'C' to "IRON_INGOT")),
        NewPiece("lun_curtain", curtain, listOf("AAA", "B_B", "B_B"), mapOf('A' to "STICK", 'B' to "WHITE_WOOL")),
        NewPiece("lun_air_conditioner", airConditioner, listOf("AAA", "BCB"), mapOf('A' to "WHITE_CONCRETE", 'B' to "SNOWBALL", 'C' to "REDSTONE")),
        NewPiece("lun_toilet", toilet, listOf("A__", "ABA", "_A_"), mapOf('A' to "QUARTZ_BLOCK", 'B' to "WATER_BUCKET")),
        NewPiece("lun_sink", sink, listOf("_C_", "ABA", "_A_"), mapOf('A' to "QUARTZ_BLOCK", 'B' to "WATER_BUCKET", 'C' to "IRON_INGOT")),
        NewPiece("lun_tv_cabinet", tvCabinet, listOf("AAA", "BCB"), mapOf('A' to "DARK_OAK_SLAB", 'B' to "DARK_OAK_PLANKS", 'C' to "CHEST")),
        NewPiece("lun_wall_tv", wallTv, listOf("AAA", "ABA", "_C_"), mapOf('A' to "GRAY_CONCRETE", 'B' to "GLASS_PANE", 'C' to "CHAIN")),
        NewPiece("lun_logo", logo, listOf("ABA", "BCB", "ABA"), mapOf('A' to "PINK_DYE", 'B' to "WHITE_WOOL", 'C' to "LIGHT_BLUE_DYE")),
        NewPiece("lun_bathtub", bathtub, listOf("ABA", "AAA"), mapOf('A' to "QUARTZ_BLOCK", 'B' to "WATER_BUCKET")),
    )
}
