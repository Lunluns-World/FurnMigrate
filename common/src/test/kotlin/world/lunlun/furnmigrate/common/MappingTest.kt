package world.lunlun.furnmigrate.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.StringReader
import java.io.StringWriter

class MappingTest {
    private val sample = Mapping(
        "lunworld",
        linkedMapOf(
            "Chair" to MappingEntry("Chair", "dice_chair"),
            "Barrels" to MappingEntry("Barrels", "", action = MappingAction.RETIRE, retireBlock = "minecraft:cauldron", notes = listOf("just a cauldron")),
            "Tent2" to MappingEntry(
                "Tent2", "dice_tent2", storage = true, status = ConversionStatus.PARTIAL,
                keepBlocks = listOf(KeepBlock(IVec3(1, 0, -2), "minecraft:chest"), KeepBlock(IVec3(2, 0, -1), "minecraft:red_bed")),
                anchor = IVec3(3, 0, -1),
                notes = listOf("bed and chest stay real blocks"),
            ),
        ),
    )

    @Test
    fun `round trip keeps every field`() {
        val w = StringWriter()
        MappingIO.write(sample, w)
        val back = MappingIO.read(StringReader(w.toString()))
        assertEquals(sample.namespace, back.namespace)
        assertEquals(sample.entries.values.toSet(), back.entries.values.toSet())
    }

    @Test
    fun `lookup is exact then case-insensitive, and lists missing styles`() {
        assertEquals("dice_chair", sample.find("Chair")?.oraxenId)
        assertEquals("dice_chair", sample.find("chair")?.oraxenId)
        assertNull(sample.find("Sofa"))
        assertEquals(listOf("Lantern", "Sofa"), sample.missing(listOf("Sofa", "Chair", "Lantern", "Sofa")))
    }

    @Test
    fun `defaults apply when optional keys are missing`() {
        val m = MappingIO.read(StringReader("format: 1\nnamespace: lunworld\nstyles:\n  TV:\n    oraxen_id: dice_tv\n"))
        assertEquals(MappingEntry("TV", "dice_tv"), m.find("TV"))
    }

    @Test
    fun `wrong format is rejected`() {
        assertThrows<IllegalArgumentException> { MappingIO.read(StringReader("format: 2\nnamespace: x\n")) }
    }

    @Test
    fun `oraxen id rule`() {
        assertEquals("dice_chair", oraxenIdFor("Chair"))
        assertEquals("dice_log_copy", oraxenIdFor("log_copy"))
    }

    @Test
    fun `anchor is written only when set`() {
        val w = StringWriter()
        MappingIO.write(sample, w)
        val text = w.toString()
        assertEquals(1, Regex("anchor:").findAll(text).count(), text)
        assertEquals(IVec3(3, 0, -1), MappingIO.read(StringReader(text)).find("Tent2")!!.anchor)
    }

    @Test
    fun `anchor world offset follows the FurnitureLib rotation`() {
        val a = IVec3(2, 0, -1)
        // FL yaw 180 is the canonical frame
        assertEquals(Vec3(2.0, 0.0, -1.0), Facing.anchorWorldOffset(a, 180.0))
        val all = listOf(0.0, 90.0, 180.0, 270.0).map { Facing.anchorWorldOffset(a, it) }
        assertEquals(4, all.toSet().size)
        for (v in all) assertEquals(5.0, v.x * v.x + v.z * v.z, 1e-9)
        // same rotation as the old piece's own blocks
        for (yaw in listOf(0.0, 90.0, 270.0)) assertEquals(Facing.flWorldOffset(Vec3(2.0, 0.0, -1.0), yaw), Facing.anchorWorldOffset(a, yaw))
    }
}
