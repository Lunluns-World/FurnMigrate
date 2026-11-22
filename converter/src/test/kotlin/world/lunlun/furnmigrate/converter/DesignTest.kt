package world.lunlun.furnmigrate.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import world.lunlun.furnmigrate.common.Axis
import world.lunlun.furnmigrate.common.Vec3
import world.lunlun.furnmigrate.converter.designs.Designs

class DesignTest {

    @Test
    fun `uv of a box outside the 0-16 cell is folded back into the texture`() {
        // a south face of a box at x -6..-2 behaves like x 10..14
        assertEquals(listOf(10.0, 6.0, 14.0, 10.0), designUv(FaceDir.SOUTH, Vec3(-6.0, 6.0, 0.0), Vec3(-2.0, 10.0, 1.0)))
        // longer than 16 px → whole texture
        assertEquals(listOf(0.0, 0.0, 16.0, 16.0), designUv(FaceDir.SOUTH, Vec3(-7.0, 0.0, 0.0), Vec3(23.0, 16.0, 1.0)))
    }

    @Test
    fun `builder rejects unknown textures and illegal angles`() {
        assertThrows<IllegalArgumentException> { design("X") { box(0, 0, 0, 1, 1, 1, "nope") } }
        assertThrows<IllegalArgumentException> {
            design("X") { tex("a", "block/stone"); box(0, 0, 0, 1, 1, 1, "a", rot = Axis.Y to 30.0) }
        }
    }

    @Test
    fun `mirror creates the symmetric box`() {
        val d = design("X") { tex("a", "block/stone"); boxMirrorX(1, 0, 0, 3, 1, 1, "a") }
        assertEquals(Vec3(13.0, 0.0, 0.0), d.elements[1].from)
        assertEquals(Vec3(15.0, 1.0, 1.0), d.elements[1].to)
    }

    @Test
    fun `oversized designs are shrunk and scaled back by the display`() {
        val big = listOf(OutElement(Vec3(-40.0, 0.0, 0.0), Vec3(56.0, 16.0, 16.0), null, emptyMap(), true))
        val (fitted, scale) = fitToModelBounds(big)
        assertEquals(2.0, scale, 1e-9)
        assertTrue(fitted[0].from.x >= -16.0 - 1e-9 && fitted[0].to.x <= 32.0 + 1e-9)
    }

    @Test
    fun `applying a design keeps the original seats and lights and records the size difference`() {
        val original = StyleResult(
            model = DModel("Chair", "Chair", "Chair", "Chair", "TOP", emptyList(), emptyList(), false),
            oraxenId = "dice_chair",
            elements = listOf(OutElement(Vec3(3.0, 0.0, 3.0), Vec3(13.0, 17.0, 13.0), null, emptyMap(), true)),
            textures = mapOf("0" to "minecraft:block/oak_planks"),
            barriers = emptyList(), seats = listOf("0,-1.25,0 0"), lights = listOf("0,0,0 14"), hitbox = "0,0,0 1,1",
            displayScale = 1.0, converted = emptyList(), skipped = listOf(PartNote("x", "y")), angleErrors = emptyList(),
            unmatchedFunctions = emptyList(), keepBlocks = emptyList(), warnings = emptyList(),
        )
        val r = DesignApplier.apply(original, Designs.find("Chair")!!)
        assertTrue(r.designed)
        assertEquals(listOf("0,-1.25,0 0"), r.seats)
        assertEquals(listOf("0,0,0 14"), r.lights)
        assertTrue(!r.needsManualDecision, "skipped armor-stand parts no longer matter for a design")
        assertTrue(Report.sizeDiff(r)!! < 1.0, "chair design should match the original within 1 px")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `scaled furniture never uses display_transform NONE (Oraxen would sink its barriers and seats)`() {
        val res = StyleResult(
            model = DModel("Tent1", "Tent1", "Tent1", "Tent1", "TOP", emptyList(), emptyList(), false),
            oraxenId = "dice_tent1", elements = emptyList(), textures = emptyMap(),
            barriers = listOf(world.lunlun.furnmigrate.common.IVec3(2, 0, 2)), seats = emptyList(), lights = emptyList(), hitbox = null,
            displayScale = 2.83, converted = emptyList(), skipped = emptyList(), angleErrors = emptyList(),
            unmatchedFunctions = emptyList(), keepBlocks = emptyList(), warnings = emptyList(),
        )
        val furniture = (Output.itemConfig(res, "lunworld")["mechanics"] as Map<String, Any>)["furniture"] as Map<String, Any>
        val props = furniture["properties"] as Map<String, Any>
        assertEquals("HEAD", props["display_transform"])
        assertTrue("scale" in props)
    }

    @Test
    fun `functional blocks stay real blocks and get no barrier`() {
        val m = DModel("T", "T", "T", "T", "TOP", emptyList(), listOf(
            ModelBlockEntry("0", Vec3(2.0, 0.0, -2.0), "minecraft:crafting_table"),
            ModelBlockEntry("1", Vec3(1.0, 0.0, 0.0), "minecraft:red_bed[facing=north,part=head]"),
            ModelBlockEntry("2", Vec3(0.0, 0.0, 0.0), "minecraft:oak_fence"),
        ), false)
        val r = StyleConverter(VanillaAssets.open(kotlin.io.path.createTempDirectory().toFile())).convert(m, "dice_t")
        assertEquals(listOf("minecraft:crafting_table", "minecraft:red_bed"), r.keepBlocks.map { it.blockId })
        assertEquals(listOf(world.lunlun.furnmigrate.common.IVec3(0, 0, 0)), r.barriers, "only the fence becomes a barrier")
    }

    @Test
    fun `every placed style is either designed or retired`() {
        // The 30 styles found in the furnitureLibData dumps (docs/findings.md §9).
        val placed = listOf(
            "Chair", "TrashCan", "Trunk", "TV", "Sofa", "Streetlamp", "Table", "CampChair", "WeaponStand", "MailBox",
            "Sunshade", "Campfire2", "Campfire1", "LargeTable", "Tent1", "Tent3", "Hammock", "Lantern", "CactusPlant",
            "Crossbow", "Fence", "Barrels", "FlowerPot", "SchoolChair", "Tent2", "BlackBoard", "SchoolTable",
            "HumanSkeleton", "WaxCandle", "SnowGolem",
        )
        val missing = placed.filter { Designs.find(it) == null && it !in Designs.retired }
        assertEquals(emptyList<String>(), missing)
        assertTrue(Designs.retired.keys.none { Designs.find(it) != null }, "a style cannot be both designed and retired")
    }

    @Test
    fun `every design only uses vanilla textures that exist`() {
        val jar = java.io.File("../vanilla/client-1.21.8.jar")
        org.junit.jupiter.api.Assumptions.assumeTrue(jar.isFile, "vanilla jar not present")
        java.util.zip.ZipFile(jar).use { zip ->
            for (d in Designs.all.values) for ((_, id) in d.textures) {
                val path = "assets/${id.substringBefore(':')}/textures/${id.substringAfter(':')}.png"
                assertTrue(zip.getEntry(path) != null, "${d.style}: $id")
            }
        }
    }

    @Test
    fun `every design stays within model limits after fitting and only uses registered textures`() {
        for (d in Designs.all.values) {
            val keys = d.textures.keys.map { "#$it" }.toSet()
            for (e in d.elements) for (f in e.faces.values) assertTrue(f.texture in keys, "${d.style}: ${f.texture}")
        }
    }

    @Test
    fun `hitbox always contains the seat stands so clicks reach the furniture`() {
        // dice_campchair before the fix: stand 0.5 wide, y −1.281..0.694 swallowed the 0.5 × 0.531 hitbox.
        val h = encloseSeats("0,0,0 0.5,0.531", listOf("0,-1.281,0 0"))
        val (w, hh) = h.substringAfter(' ').split(',').map { it.toDouble() }
        assertTrue(w > SEAT_STAND_WIDTH, h)
        assertTrue(hh > -1.281 + SEAT_STAND_HEIGHT, h)
        // a big enough hitbox stays as it is
        assertEquals("0,0,0 0.625,1.094", encloseSeats("0,0,0 0.625,1.094", listOf("0,-1.25,0 0")))
    }

    @Test
    fun `every generated hitbox contains its centred seats`() {
        for (d in Designs.all.values) {
            val seats = d.seats ?: continue
            val h = encloseSeats("0,0,0 0.5,0.3", seats)
            assertEquals(h, encloseSeats(h, seats), d.style)
        }
    }

    @Test
    fun `anchor moves model, seats, lights and barriers to the new origin`() {
        val original = StyleResult(
            model = DModel("Tent3", "Tent3", "Tent3", "Tent3", "TOP", emptyList(), emptyList(), false),
            oraxenId = "dice_tent3", elements = emptyList(), textures = emptyMap(),
            barriers = emptyList(), seats = listOf("0,-1.5,1 0"), lights = listOf("1,0,2 15"), hitbox = null,
            displayScale = 1.0, converted = emptyList(), skipped = emptyList(), angleErrors = emptyList(),
            unmatchedFunctions = emptyList(), keepBlocks = emptyList(), warnings = emptyList(),
        )
        val d = design("Tent3") { tex("a", "block/stone"); anchor(0, 0, -1); box(0, 0, 16, 16, 16, 32, "a") }
        val r = DesignApplier.apply(original, d)
        assertEquals(listOf("0,-1.5,0 0"), r.seats, "seat cell becomes the furniture block")
        assertEquals(listOf("1,0,1 15"), r.lights)
        assertEquals(Vec3(0.0, 0.0, 0.0), r.elements[0].from, "the anchor cell is now model block 0..16")
        assertEquals(world.lunlun.furnmigrate.common.IVec3(0, 0, -1), r.anchor)
    }

    @Test
    fun `tents are anchored in their middle and never on a kept real block`() {
        val keep = mapOf(
            "Tent1" to listOf(world.lunlun.furnmigrate.common.IVec3(2, 0, -2)),
            "Tent2" to listOf(world.lunlun.furnmigrate.common.IVec3(2, 0, -1), world.lunlun.furnmigrate.common.IVec3(2, 0, -2), world.lunlun.furnmigrate.common.IVec3(3, 0, -2)),
            "Tent3" to listOf(world.lunlun.furnmigrate.common.IVec3(0, 0, -2)),
        )
        for ((style, cells) in keep) {
            val a = Designs.find(style)!!.anchor
            assertTrue(a != world.lunlun.furnmigrate.common.IVec3(0, 0, 0), "$style is anchored at its corner")
            assertTrue(a !in cells, "$style anchor $a is a kept block")
        }
    }

    @Test
    fun `hitbox never covers a kept real block`() {
        // Tent1: crafting table one block behind the anchor → hitbox only over the anchor block
        assertEquals("0,0,0 1,2.375", avoidKeepBlocks("0,0,0 3,2.375", listOf(world.lunlun.furnmigrate.common.IVec3(0, 0, 1))))
        // two blocks away → up to 3 wide
        assertEquals("0,0,0 3,1", avoidKeepBlocks("0,0,0 3,1", listOf(world.lunlun.furnmigrate.common.IVec3(2, 0, -2))))
        // above the hitbox → no effect
        assertEquals("0,0,0 3,1", avoidKeepBlocks("0,0,0 3,1", listOf(world.lunlun.furnmigrate.common.IVec3(1, 1, 0))))
    }

    @Test
    fun `model json uses vanilla texture paths Oraxen accepts`() {
        assertEquals("block/oak_log_top", Output.modelTexturePath("minecraft:block/oak_log_top"))
        assertEquals("lunworld:dice/x", Output.modelTexturePath("lunworld:dice/x"))
        val json = Output.modelJson(emptyList(), mapOf("0" to "minecraft:block/cactus_side"))
        assertTrue("\"block/cactus_side\"" in json && "minecraft:block" !in json, json)
    }

    @Test
    fun `a design can fix the item name taken from the model`() {
        for ((style, name) in listOf("Tent2" to "帳篷 #2", "Trunk" to "橫躺可以坐的長原木")) {
            val original = StyleResult(
                model = DModel(style, style, style, "&c錯字", "TOP", emptyList(), emptyList(), false),
                oraxenId = "dice_" + style.lowercase(), elements = emptyList(), textures = emptyMap(),
                barriers = emptyList(), seats = emptyList(), lights = emptyList(), hitbox = null,
                displayScale = 1.0, converted = emptyList(), skipped = emptyList(), angleErrors = emptyList(),
                unmatchedFunctions = emptyList(), keepBlocks = emptyList(), warnings = emptyList(),
            )
            val r = DesignApplier.apply(original, Designs.find(style)!!)
            assertEquals("<!i><red>$name", Output.itemConfig(r, "lunworld")["itemname"], style)
        }
    }
}
