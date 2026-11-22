package world.lunlun.furnmigrate.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RecipesTest {
    // Recipes as they are in the server's FurnitureLib/models.
    private val chair = DiceRecipe("Chair", listOf("0+0", "0+0", "E0E"), mapOf("0" to "AIR", "+" to "OAK_TRAPDOOR", "E" to "STICK"), false)
    private val sofa = DiceRecipe("Sofa", listOf("XXX", "DEF", "GXI"),
        mapOf("D" to "WHITE_CARPET", "E" to "WHITE_CARPET", "F" to "WHITE_CARPET", "G" to "LEVER", "X" to "AIR", "I" to "LEVER"), false)

    @Test
    fun `air becomes empty slots and materials get letters`() {
        val r = Recipes.toOraxen(chair, "dice_chair")!!
        assertEquals(listOf("_A_", "_A_", "B_B"), r.shape)
        assertEquals(mapOf('A' to "OAK_TRAPDOOR", 'B' to "STICK"), r.ingredients)
    }

    @Test
    fun `the same material under several letters is merged`() {
        val r = Recipes.toOraxen(sofa, "dice_sofa")!!
        assertEquals(listOf("___", "AAA", "B_B"), r.shape)
        assertEquals(mapOf('A' to "WHITE_CARPET", 'B' to "LEVER"), r.ingredients)
    }

    @Test
    fun `disabled and empty recipes are not written`() {
        assertNull(Recipes.toOraxen(chair.copy(disabled = true), "dice_chair"))
        assertNull(Recipes.toOraxen(DiceRecipe("SnowGolem", listOf("xxx", "xxx", "xxx"), mapOf("x" to "AIR"), false), "dice_snowgolem"))
    }

    @Test
    fun `identical grids are found even when shifted`() {
        val a = Recipes.toOraxen(DiceRecipe("A", listOf("X00", "000", "000"), mapOf("X" to "CANDLE", "0" to "AIR"), false), "a")!!
        val b = Recipes.toOraxen(DiceRecipe("B", listOf("000", "0x0", "000"), mapOf("x" to "CANDLE", "0" to "AIR"), false), "b")!!
        assertEquals(listOf(listOf("a", "b")), Recipes.duplicates(listOf(a, b, Recipes.toOraxen(chair, "c")!!)))
    }

    @Test
    fun `numeric index keys (WaxCandle uses 0) are read`() {
        val f = kotlin.io.path.createTempFile(suffix = ".dModel").toFile()
        f.writeText("WaxCandle:\n  system-ID: WaxCandle\n  crafting:\n    recipe: 000,0x0,000\n    disable: false\n    index:\n      0: AIR\n      x: CANDLE\n")
        val r = Recipes.toOraxen(Recipes.read(f)!!, "dice_waxcandle")!!
        assertEquals(listOf("___", "_A_", "___"), r.shape)
    }
}
