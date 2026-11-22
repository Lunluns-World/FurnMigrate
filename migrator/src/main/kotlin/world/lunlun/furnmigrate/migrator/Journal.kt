package world.lunlun.furnmigrate.migrator

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bukkit.inventory.ItemStack
import world.lunlun.furnmigrate.common.HeldItem
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Base64

/**
 * `plugins/FurnMigrate/journal/<time>.jsonl`: one JSON object per line, written and fsync'ed before
 * the world is touched. A BEGIN line holds the full record (place, facing, style, Oraxen id, player
 * items as Paper item bytes, kept blocks); DONE / NEEDS_MANUAL / ABORTED close it.
 */
class Journal(val file: File) {
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    fun write(type: String, id: String, fill: JsonObject.() -> Unit = {}) {
        val o = JsonObject()
        o.addProperty("type", type)
        o.addProperty("id", id)
        o.addProperty("time", Instant.now().toString())
        o.fill()
        file.parentFile.mkdirs()
        FileOutputStream(file, true).use { out ->
            out.write((gson.toJson(o) + "\n").toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
    }

    fun lines(): List<JsonObject> = read(file)

    companion object {
        fun read(file: File): List<JsonObject> = if (!file.isFile) emptyList() else
            file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.mapNotNull {
                runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull()
            }

        fun items(items: List<HeldItem<ItemStack>>): JsonArray = JsonArray().apply {
            for (h in items) add(JsonObject().apply {
                addProperty("slot", h.slot)
                addProperty("part", h.partName)
                addProperty("material", h.material)
                addProperty("amount", h.payload.amount)
                addProperty("data", encode(h.payload))
            })
        }

        fun itemArray(items: List<ItemStack>): JsonArray = JsonArray().apply {
            for (it in items) if (!it.type.isAir) add(JsonObject().apply {
                addProperty("material", it.type.key.toString())
                addProperty("amount", it.amount)
                addProperty("data", encode(it))
            })
        }

        fun encode(item: ItemStack): String = Base64.getEncoder().encodeToString(item.serializeAsBytes())

        fun decodeItems(arr: JsonArray?): List<ItemStack> = arr?.mapNotNull { e ->
            runCatching { ItemStack.deserializeBytes(Base64.getDecoder().decode(e.asJsonObject["data"].asString)) }.getOrNull()
        } ?: emptyList()
    }
}
