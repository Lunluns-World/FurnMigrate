package world.lunlun.furnmigrate.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.lunlun.furnmigrate.common.Vec3

class DModelParserTest {
    private fun load(name: String) = DModelParser.parse(name, javaClass.getResource("/$name.dModel")!!.readText())

    @Test
    fun `chair has 7 stands, a mount marker and a rotated trapdoor`() {
        val m = load("Chair")
        assertEquals("Chair", m.style)
        assertEquals("Chair", m.systemId)
        assertEquals(7, m.entities.size)
        assertTrue(m.entities.all { it.type == "armor_stand" && it.marker && it.invisible })
        assertEquals(1, m.entities.count { it.name?.startsWith("#Mount:") == true })
        val back = m.entities.single { it.pose.containsKey(BodyPart.HEAD) }
        assertEquals("minecraft:oak_trapdoor", back.items["HEAD"]!!.id)
        assertEquals(1.57, back.poseOf(BodyPart.HEAD).x, 1e-6)
        assertTrue(back.offset.approxEquals(Vec3(0.0, -0.65, -0.25)))
        assertEquals(4, m.entities.count { it.items["HEAD"]?.id == "minecraft:lever" })
    }

    @Test
    fun `lantern torch comes from material name`() {
        val m = load("Lantern")
        assertEquals(listOf("minecraft:torch"), m.blocks.map { it.blockData })
    }

    @Test
    fun `block entries follow FurnitureLib rules`() {
        assertEquals("minecraft:oak_wall_sign[facing=north]",
            DModelParser.parseBlock("0", mapOf("material" to "WALL_SIGN", "Rotation" to "NORTH"))!!.blockData)
        assertEquals("minecraft:barrier", DModelParser.parseBlock("0", mapOf("blockData" to "minecraft:barrier"))!!.blockData)
        assertEquals(null, DModelParser.parseBlock("0", mapOf("material" to "AIR")))
        val stairs = DModelParser.parseBlock("0", mapOf("blockData" to "minecraft:oak_stairs[facing=east,half=bottom]"))!!
        assertEquals(mapOf("facing" to "east", "half" to "bottom"), stairs.properties)
    }
}
