package world.lunlun.furnmigrate.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import world.lunlun.furnmigrate.common.IVec3
import world.lunlun.furnmigrate.common.Vec3
import world.lunlun.furnmigrate.converter.designs.Designs
import world.lunlun.furnmigrate.converter.designs.NewDesigns

class NewFurnitureTest {
    private val results = NewDesigns.all.associate { it.oraxenId to NewFurniture.result(it.oraxenId, it.design) }

    @Suppress("UNCHECKED_CAST")
    private fun furniture(item: Map<String, Any>) = (item["mechanics"] as Map<String, Any>)["furniture"] as Map<String, Any>

    @Test
    fun `the new pieces exist with names, models and recipes`() {
        assertEquals(
            setOf("lun_fridge", "lun_ceiling_fan", "lun_computer", "lun_curtain", "lun_air_conditioner", "lun_toilet", "lun_sink", "lun_bathtub", "lun_wall_tv", "lun_logo", "lun_tv_cabinet"),
            results.keys,
        )
        for ((id, r) in results) {
            assertTrue(r.designed && r.elements.isNotEmpty(), id)
            assertTrue(r.model.displayName.isNotBlank(), id)
            assertTrue(Output.modelPath(id).startsWith("furniture/"), id)
        }
        assertEquals(emptyList<List<String>>(), Recipes.duplicates(NewDesigns.all.map { it.recipe }))
        for (p in NewDesigns.all) {
            val used = p.recipe.shape.joinToString("").filter { it != Recipes.EMPTY }.toSet()
            assertEquals(p.recipe.ingredients.keys, used, "${p.oraxenId}: every ingredient letter is used and defined")
        }
    }

    @Test
    fun `fan hangs from the ceiling, air conditioner on walls, curtain on both`() {
        assertEquals(Placing(floor = false, roof = true), results.getValue("lun_ceiling_fan").placing)
        assertEquals(Placing(floor = false, wall = true), results.getValue("lun_air_conditioner").placing)
        assertEquals(Placing(floor = false, wall = true), results.getValue("lun_wall_tv").placing)
        assertEquals(Placing(floor = true, wall = true), results.getValue("lun_curtain").placing)
        @Suppress("UNCHECKED_CAST")
        val lp = furniture(Output.itemConfig(results.getValue("lun_ceiling_fan"), "lunworld"))["limited_placing"] as Map<String, Any>
        assertEquals(mapOf("floor" to false, "wall" to false, "roof" to true), lp)
    }

    @Test
    fun `fridge is two barriers high with storage, toilet and bathtub have seats`() {
        val fridge = results.getValue("lun_fridge")
        assertEquals(listOf(IVec3(0, 0, 0), IVec3(0, 1, 0)), fridge.barriers)
        assertNull(fridge.hitbox)
        assertEquals(3, fridge.storage?.rows)
        assertEquals(1, results.getValue("lun_toilet").seats.size)
        assertEquals(2, results.getValue("lun_bathtub").seats.size)
    }

    @Test
    fun `the logo texture comes from the local folder and is not vanilla`() {
        val logo = NewDesigns.all.first { it.oraxenId == "lun_logo" }.design
        assertTrue(CustomTextures.isCustom("lunworld:furniture/logo") && !CustomTextures.isCustom("block/stone"))
        val dir = kotlin.io.path.createTempDirectory().toFile()
        assertEquals(listOf("lunworld:furniture/logo"), CustomTextures.missing(logo, dir))
        java.io.File(dir, "furniture").mkdirs(); java.io.File(dir, "furniture/logo.png").writeBytes(byteArrayOf(1))
        assertEquals(emptyList<String>(), CustomTextures.missing(logo, dir))
        val out = kotlin.io.path.createTempDirectory().toFile()
        CustomTextures.copy(listOf(logo), dir, out)
        assertTrue(java.io.File(out, "lunworld/textures/furniture/logo.png").isFile)
    }

    @Test
    fun `new furniture must have a name`() {
        assertThrows<IllegalArgumentException> { NewFurniture.result("lun_x", design("X") { tex("a", "block/stone"); box(0, 0, 0, 1, 1, 1, "a") }) }
    }

    @Test
    fun `tables are solid barrier blocks with the top flush at one block`() {
        for (style in listOf("Table", "SchoolTable", "LargeTable")) {
            val d = Designs.find(style)!!
            assertTrue(d.barriers!!.isNotEmpty(), style)
            assertEquals(16.0, d.elements.filter { it.to.y <= 16.0 }.maxOf { it.to.y }, style)
        }
        // LargeTable: 3×3 under the whole top, from the old origin cell (no anchor, so tables migrated
        // before the redesign still line up with their footprint)
        val large = DesignApplier.apply(emptyResult("LargeTable"), Designs.find("LargeTable")!!)
        assertEquals(IVec3(0, 0, 0), large.anchor)
        assertEquals((0..2).flatMap { x -> (0..2).map { z -> IVec3(x, 0, z) } }.toSet(), large.barriers.toSet())
    }

    @Test
    fun `barrier grid maps design cells to Oraxen offsets`() {
        // design cell x 16..32 = Oraxen x −1; the cell in front (z −16..0) = Oraxen z −1
        val d = design("G") { tex("a", "block/stone"); box(0, 0, 0, 1, 1, 1, "a"); barrierGrid(0, 1, -1, 0) }
        assertEquals(setOf(IVec3(0, 0, -1), IVec3(0, 0, 0), IVec3(-1, 0, -1), IVec3(-1, 0, 0)), d.barriers!!.toSet())
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `Oraxen 1_221 syntax uses name, string barriers and a furniture appearance`() {
        val fridge = results.getValue("lun_fridge")
        val old = Output.itemConfig(fridge, "lunworld", OraxenFormat.V1_220)
        val new = Output.itemConfig(fridge, "lunworld", OraxenFormat.V1_221)
        assertTrue("itemname" in old && "name" !in old)
        assertTrue("name" in new && "itemname" !in new)
        assertEquals(listOf(mapOf("x" to 0, "y" to 0, "z" to 0), mapOf("x" to 0, "y" to 1, "z" to 0)), furniture(old)["barriers"])
        assertEquals(listOf("0,0,0", "0,1,0"), furniture(new)["barriers"])
        assertNull(furniture(old)["appearance"])
        assertEquals(new["pack"], furniture(new)["appearance"])
        val yaml = Recipes.toYaml(listOf(NewDesigns.all.first().recipe), OraxenFormat.V1_221)
        assertTrue("oraxen-item:" in yaml && "minecraft-type:" in yaml && "oraxen_item" !in yaml)
        assertEquals(OraxenFormat.V1_221, OraxenFormat.parse("1.221.0"))
        assertThrows<IllegalArgumentException> { OraxenFormat.parse("1.219") }
    }

    private fun emptyResult(style: String) = StyleResult(
        model = DModel(style, style, style, style, "TOP", emptyList(), emptyList(), false),
        oraxenId = "dice_" + style.lowercase(), elements = emptyList(), textures = emptyMap(), barriers = emptyList(),
        seats = emptyList(), lights = emptyList(), hitbox = null, displayScale = 1.0, converted = emptyList(),
        skipped = emptyList(), angleErrors = emptyList(), unmatchedFunctions = emptyList(), keepBlocks = emptyList(),
        warnings = emptyList(),
    )

    @Test
    fun `tv cabinet is two solid cells with the top flush and storage`() {
        val cab = NewFurniture.result("lun_tv_cabinet", NewDesigns.all.first { it.oraxenId == "lun_tv_cabinet" }.design)
        assertEquals(setOf(IVec3(1, 0, 0), IVec3(0, 0, 0)), cab.barriers.toSet())
        assertEquals(16.0, cab.elements.maxOf { it.to.y })
        assertTrue(cab.storage != null)
    }

    @Test
    fun `no model has flickering faces after separation`() {
        val all = Designs.all.values.map { DesignApplier.apply(emptyResult(it.style), it) } +
            NewDesigns.all.map { NewFurniture.result(it.oraxenId, it.design) }
        for (r in all) assertEquals(emptyList<ZFight.Clash>(), ZFight.clashes(ZFight.separate(r.elements)), r.oraxenId)
    }

    @Test
    fun `separation pushes the smaller face out`() {
        val f = Face(listOf(0.0, 0.0, 1.0, 1.0), "#a", 0)
        val faces = FaceDir.entries.associateWith { f }
        val body = OutElement(Vec3(0.0, 8.0, 0.0), Vec3(16.0, 15.0, 16.0), null, faces, true)
        val vent = OutElement(Vec3(2.0, 8.0, 0.0), Vec3(14.0, 8.5, 10.0), null, faces, true)
        val out = ZFight.separate(listOf(body, vent))
        assertEquals(body, out[0])
        assertEquals(8.0 - ZFight.EPS, out[1].from.y, 1e-9)
        assertEquals(0.0 - ZFight.EPS, out[1].from.z, 1e-9)
    }
}
