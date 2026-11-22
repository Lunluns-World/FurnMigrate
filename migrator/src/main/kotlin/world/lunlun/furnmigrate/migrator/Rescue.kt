package world.lunlun.furnmigrate.migrator

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import world.lunlun.furnmigrate.common.RecordItem
import world.lunlun.furnmigrate.common.RecordItems

/** Turns an item FurnitureLib could not load into a real ItemStack with Paper's DataFixer (UnsafeValues#deserializeItemFromJson). */
object Rescue {
    fun toItem(r: RecordItem): ItemStack? {
        val versions = listOfNotNull(
            RecordItems.guessDataVersion(r.item), Bukkit.getUnsafe().dataVersion,
            RecordItems.DATA_VERSION_1_21_4, RecordItems.DATA_VERSION_1_20_4,
        ).distinct()
        for (v in versions) {
            val json = toJson(r.item) as JsonObject
            json.addProperty("DataVersion", v)
            val item = runCatching { Bukkit.getUnsafe().deserializeItemFromJson(json) }.getOrNull()
            if (item != null && !item.type.isAir) return item
        }
        return null
    }

    fun toJson(v: Any?): JsonElement = when (v) {
        null -> JsonNull.INSTANCE
        is Map<*, *> -> JsonObject().apply { v.forEach { (k, x) -> add(k.toString(), toJson(x)) } }
        is List<*> -> JsonArray().apply { v.forEach { add(toJson(it)) } }
        is ByteArray -> JsonArray().apply { v.forEach { add(it) } }
        is IntArray -> JsonArray().apply { v.forEach { add(it) } }
        is LongArray -> JsonArray().apply { v.forEach { add(it) } }
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }
}
