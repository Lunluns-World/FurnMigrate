package world.lunlun.furnmigrate.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MigrationTest {

    private fun held(slot: String, mat: String, part: String = "", tag: Int = 0) = HeldItem(slot, mat, part, tag)

    @Test
    fun `only item holder parts hold player items`() {
        val placed = listOf(
            held("HEAD", "minecraft:lever"),                          // model part
            held("MAINHAND", "minecraft:iron_sword", "#SLOT1#", 1),
            held("HEAD", "minecraft:bread", "#ITEM#", 2),
            held("HEAD", "minecraft:dirt", "#TRASH#", 3),
            held("HEAD", "minecraft:glowstone", "#LAMP#"),            // Streetlamp switched on: not a player item
            held("HEAD", "minecraft:stone"),                          // Fence post variant: not a player item
            held("HEAD", "minecraft:cake", "#TELLER2#", 4),
        )
        assertEquals(listOf(1, 2, 3, 4), PlayerItems.of(placed).map { it.payload })
    }

    @Test
    fun `ids are normalised`() {
        assertEquals("minecraft:oak_fence", normalizeId("OAK_FENCE"))
        assertEquals("minecraft:red_bed", normalizeId("minecraft:red_bed[facing=north,part=head]"))
    }

    private val tent1 = MappingEntry(
        "Tent1", "dice_tent1", anchor = IVec3(2, 0, -1),
        keepBlocks = listOf(KeepBlock(IVec3(2, 0, -2), "minecraft:crafting_table")),
    )

    @Test
    fun `replace plan in the canonical frame (FL yaw 180)`() {
        val origin = IVec3(100, 64, 200)
        val table = IVec3(102, 64, 198)
        val piece = PlacedPiece("Tent1", origin, 180.0, mapOf(table to "CRAFTING_TABLE", IVec3(100, 64, 200) to "BARRIER"))
        val p = MigrationPlanner.plan(tent1, piece) as Plan.Replace
        assertEquals(IVec3(102, 64, 199), p.placeAt)
        assertEquals(listOf(table), p.keepCells)
        assertEquals(0f, p.oraxenYaw)
    }

    @Test
    fun `anchor and kept blocks turn with the piece`() {
        for (yaw in listOf(0.0, 90.0, 180.0, 270.0)) {
            val origin = IVec3(0, 70, 0)
            val table = MigrationPlanner.worldCell(origin, IVec3(2, 0, -2), yaw)
            val piece = PlacedPiece("Tent1", origin, yaw, mapOf(table to "minecraft:crafting_table"))
            val p = MigrationPlanner.plan(tent1, piece) as Plan.Replace
            assertEquals(MigrationPlanner.worldCell(origin, IVec3(2, 0, -1), yaw), p.placeAt, "yaw $yaw")
            assertEquals(listOf(table), p.keepCells, "yaw $yaw")
            // anchor stays one block in front of the table, in every facing
            val d = (p.placeAt.x - table.x) * (p.placeAt.x - table.x) + (p.placeAt.z - table.z) * (p.placeAt.z - table.z)
            assertEquals(1, d)
        }
    }

    @Test
    fun `a kept block that is missing or different is not kept`() {
        val piece = PlacedPiece("Tent1", IVec3(0, 0, 0), 180.0, mapOf(IVec3(2, 0, -2) to "minecraft:oak_planks"))
        assertEquals(emptyList<IVec3>(), (MigrationPlanner.plan(tent1, piece) as Plan.Replace).keepCells)
    }

    @Test
    fun `retire keeps the real block or puts it back`() {
        val barrels = MappingEntry("Barrels", "", action = MappingAction.RETIRE, retireBlock = "minecraft:cauldron")
        val o = IVec3(5, 60, 5)
        assertEquals(Plan.Retire(listOf(o), null), MigrationPlanner.plan(barrels, PlacedPiece("Barrels", o, 0.0, mapOf(o to "CAULDRON"))))
        val fire = MappingEntry("Campfire1", "", action = MappingAction.RETIRE, retireBlock = "minecraft:campfire")
        assertEquals(Plan.Retire(emptyList(), o to "minecraft:campfire"), MigrationPlanner.plan(fire, PlacedPiece("Campfire1", o, 0.0, emptyMap())))
    }

    @Test
    fun `unmapped styles are skipped`() {
        assertTrue(MigrationPlanner.plan(null, PlacedPiece("Guillotine", IVec3(0, 0, 0), 0.0, emptyMap())) is Plan.Skip)
    }

    @Test
    fun `space prediction ignores cells the old piece frees`() {
        val piece = PlacedPiece("X", IVec3(0, 0, 0), 0.0, mapOf(IVec3(0, 0, 0) to "BARRIER", IVec3(0, 1, 0) to "BARRIER", IVec3(1, 0, 0) to "CHEST"))
        val solid = setOf(IVec3(0, 0, 0), IVec3(0, 1, 0), IVec3(1, 0, 0), IVec3(0, 2, 0))
        val blocked = MigrationPlanner.blockedCells(
            listOf(IVec3(0, 0, 0), IVec3(0, 1, 0), IVec3(1, 0, 0), IVec3(0, 2, 0)), piece, keepCells = listOf(IVec3(1, 0, 0)),
        ) { it !in solid }
        assertEquals(listOf(IVec3(1, 0, 0), IVec3(0, 2, 0)), blocked)
    }

    @Test
    fun `chunk keys sort and parse`() {
        val keys = listOf(ChunkKey("world", 2, 1), ChunkKey("world", -1, 5), ChunkKey("a_nether", 0, 0), ChunkKey("world", 2, -3))
        assertEquals(listOf("a_nether:0:0", "world:-1:5", "world:2:-3", "world:2:1"), keys.sorted().map { it.toString() })
        for (k in keys) assertEquals(k, ChunkKey.parse(k.toString()))
        assertEquals(ChunkKey("odd:name", -4, 7), ChunkKey.parse("odd:name:-4:7"))
    }

    @Test
    fun `run needs a second command within 30 seconds`() {
        val g = ConfirmGate()
        assertFalse(g.confirm("op", 0))
        assertTrue(g.confirm("op", 29_000))
        assertFalse(g.confirm("op", 30_000), "a confirmation is used up")
        assertFalse(g.confirm("op", 70_000), "too late: arms again")
        assertFalse(g.confirm("other", 70_500), "someone else's command does not confirm")
        assertTrue(g.confirm("op", 71_000))
    }

    @Test
    fun `interrupted objects are the ones without a terminal line`() {
        val lines = listOf(
            "BEGIN" to "a", "DONE" to "a",
            "BEGIN" to "b",
            "SKIPPED" to "c",
            "BEGIN" to "d", "NEEDS_MANUAL" to "d", "ITEMS_DROPPED" to "d",
        )
        assertEquals(listOf("b"), JournalRecovery.interrupted(lines))
    }

    // shaped like a WeaponStand record found on a real server: FurnitureLib loads the axe but not the hoe
    private val weaponStandRecord: Map<String, Any> = mapOf("entities" to mapOf(
        "1" to mapOf("CustomName" to "#SLOT1#", "Inventory" to mapOf("MAINHAND" to mapOf(
            "id" to "minecraft:iron_hoe", "count" to 1,
            "components" to mapOf("minecraft:enchantments" to mapOf("levels" to mapOf("minecraft:efficiency" to 4)))))),
        "2" to mapOf("Name" to "#SLOT2#", "Inventory" to mapOf("MAINHAND" to mapOf("id" to "minecraft:iron_axe", "count" to 1,
            "components" to mapOf("minecraft:damage" to 248)))),
        "3" to mapOf("CustomName" to "#SLOT3#"),
        "4" to mapOf("Inventory" to mapOf("HEAD" to mapOf("id" to "minecraft:oak_fence_gate", "count" to 1))),
    ))

    @Test
    fun `record holder items are read like FurnitureLib reads part names`() {
        val items = RecordItems.holders(weaponStandRecord)
        assertEquals(listOf("#SLOT1#" to "minecraft:iron_hoe", "#SLOT2#" to "minecraft:iron_axe"), items.map { it.part to it.material })
    }

    @Test
    fun `items FurnitureLib failed to load are found`() {
        val loaded = listOf(HeldItem("MAINHAND", "minecraft:iron_axe", "#SLOT2#", 0))
        assertEquals(listOf("minecraft:iron_hoe"), RecordItems.notLoaded(RecordItems.holders(weaponStandRecord), loaded).map { it.material })
    }

    @Test
    fun `old item layouts get the data version to upgrade from`() {
        val (hoe, axe) = RecordItems.holders(weaponStandRecord)
        assertEquals(RecordItems.DATA_VERSION_1_21_4, RecordItems.guessDataVersion(hoe.item))
        assertEquals(null, RecordItems.guessDataVersion(axe.item))
        assertEquals(RecordItems.DATA_VERSION_1_20_4, RecordItems.guessDataVersion(mapOf("id" to "minecraft:stone", "Count" to 1.toByte())))
    }

    @Test
    fun `plants in flower pots are given back`() {
        assertEquals("minecraft:blue_orchid", PottedPlants.content("minecraft:potted_blue_orchid"))
        assertEquals("minecraft:cherry_sapling", PottedPlants.content("POTTED_CHERRY_SAPLING"))
        assertEquals("minecraft:azalea", PottedPlants.content("minecraft:potted_azalea_bush"))
        assertEquals(null, PottedPlants.content("minecraft:flower_pot"))
        assertEquals(null, PottedPlants.content("minecraft:barrier"))
    }
}
