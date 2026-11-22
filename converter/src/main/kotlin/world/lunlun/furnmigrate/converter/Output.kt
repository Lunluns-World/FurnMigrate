package world.lunlun.furnmigrate.converter

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import world.lunlun.furnmigrate.common.ConversionStatus
import world.lunlun.furnmigrate.common.Mapping
import world.lunlun.furnmigrate.common.MappingAction
import world.lunlun.furnmigrate.common.MappingEntry
import world.lunlun.furnmigrate.common.MappingIO
import world.lunlun.furnmigrate.common.Mat3
import world.lunlun.furnmigrate.common.Vec3
import java.io.File
import kotlin.math.max

object Output {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    private fun r(d: Double): Double { val v = Math.round(d * 10000.0) / 10000.0; return if (v == -0.0) 0.0 else v }
    private fun arr(vararg d: Double) = JsonArray().apply { d.forEach { add(r(it)) } }
    private fun arr(v: Vec3) = arr(v.x, v.y, v.z)

    fun modelPath(id: String) = "dice/" + id.removePrefix("dice_")

    fun modelJson(res: StyleResult): String = modelJson(res.elements, res.textures)

    /**
     * `minecraft:block/oak_log_top` → `block/oak_log_top` (same texture for the game). Oraxen's pack check
     * (PackFileCollector.java:151) accepts vanilla paths only without the namespace, or when the file name
     * happens to be an item name, so `minecraft:block/oak_log_top` was reported as an invalid texture path.
     */
    fun modelTexturePath(id: String): String = id.removePrefix("minecraft:")

    fun modelJson(elements: List<OutElement>, textures: Map<String, String>): String {
        val root = JsonObject()
        root.add("textures", JsonObject().apply {
            textures.forEach { (k, v) -> addProperty(k, modelTexturePath(v)) }
            textures.values.firstOrNull()?.let { addProperty("particle", modelTexturePath(it)) }
        })
        val els = JsonArray()
        for (e in elements) {
            els.add(JsonObject().apply {
                add("from", arr(e.from)); add("to", arr(e.to))
                if (!e.shade) addProperty("shade", false)
                e.rotation?.let { rot ->
                    add("rotation", JsonObject().apply {
                        addProperty("angle", rot.angle); addProperty("axis", rot.axis.name.lowercase()); add("origin", arr(rot.origin))
                    })
                }
                add("faces", JsonObject().apply {
                    e.faces.forEach { (dir, f) ->
                        add(dir.key, JsonObject().apply {
                            add("uv", arr(*f.uv!!.toDoubleArray()))
                            addProperty("texture", f.texture)
                            if (f.rotation != 0) addProperty("rotation", f.rotation)
                        })
                    }
                })
            })
        }
        root.add("elements", els)
        root.add("display", displayFor(elements))
        return gson.toJson(root)
    }

    /** Icons and held views; the placed furniture uses display_transform HEAD, which has no entry here (= untransformed). */
    private fun displayFor(elements: List<OutElement>): JsonObject {
        var mn = Vec3(1e9, 1e9, 1e9); var mx = Vec3(-1e9, -1e9, -1e9)
        for (e in elements) for (v in listOf(e.from, e.to)) {
            mn = Vec3(minOf(mn.x, v.x), minOf(mn.y, v.y), minOf(mn.z, v.z)); mx = Vec3(max(mx.x, v.x), max(mx.y, v.y), max(mx.z, v.z))
        }
        if (elements.isEmpty()) { mn = Vec3.ZERO; mx = Vec3(16.0, 16.0, 16.0) }
        val size = max(mx.x - mn.x, max(mx.y - mn.y, mx.z - mn.z)).coerceAtLeast(1.0)
        val centre = (mn + mx) * 0.5 - Vec3(8.0, 8.0, 8.0)
        fun ctx(rot: Vec3, fit: Double): JsonObject {
            val s = (fit * 16.0 / size).coerceAtMost(4.0)
            val rm = Mat3.rotXDeg(rot.x) * Mat3.rotYDeg(rot.y) * Mat3.rotZDeg(rot.z)
            val t = -(rm * (centre * s))
            return JsonObject().apply {
                add("rotation", arr(rot)); add("translation", arr(t.x.coerceIn(-80.0, 80.0), t.y.coerceIn(-80.0, 80.0), t.z.coerceIn(-80.0, 80.0))); add("scale", arr(s, s, s))
            }
        }
        return JsonObject().apply {
            add("gui", ctx(Vec3(30.0, 225.0, 0.0), 0.625))
            add("ground", ctx(Vec3.ZERO, 0.25))
            add("fixed", ctx(Vec3.ZERO, 0.5))
            add("thirdperson_righthand", ctx(Vec3(75.0, 45.0, 0.0), 0.375))
            add("firstperson_righthand", ctx(Vec3(0.0, 45.0, 0.0), 0.4))
        }
    }

    /** `&c` legacy colour codes → MiniMessage. */
    fun legacyToMini(s: String): String {
        val codes = mapOf(
            '0' to "black", '1' to "dark_blue", '2' to "dark_green", '3' to "dark_aqua", '4' to "dark_red", '5' to "dark_purple",
            '6' to "gold", '7' to "gray", '8' to "dark_gray", '9' to "blue", 'a' to "green", 'b' to "aqua", 'c' to "red",
            'd' to "light_purple", 'e' to "yellow", 'f' to "white", 'l' to "bold", 'o' to "italic", 'n' to "underlined", 'm' to "strikethrough", 'r' to "reset",
        )
        return Regex("[&§]([0-9a-fk-or])", RegexOption.IGNORE_CASE).replace(s) { m -> codes[m.groupValues[1].lowercase()[0]]?.let { "<$it>" } ?: "" }
    }

    fun itemConfig(res: StyleResult, namespace: String): Map<String, Any> {
        val furniture = linkedMapOf<String, Any>(
            "type" to "DISPLAY_ENTITY",
            // HEAD, not NONE: Oraxen lowers barriers and seats by 0.5 × scale for NONE + scale
            // (FurnitureMechanic.java:826). Our models have no "head" transform, so HEAD renders like NONE.
            "properties" to linkedMapOf<String, Any>("display_transform" to "HEAD").apply {
                if (res.displayScale != 1.0) {
                    val s = r(res.displayScale)
                    put("scale", linkedMapOf("x" to s, "y" to s, "z" to s))
                }
            },
            "limited_placing" to linkedMapOf("floor" to true, "wall" to false, "roof" to false),
        )
        if (res.barriers.isNotEmpty()) furniture["barriers"] = res.barriers.map { linkedMapOf("x" to it.x, "y" to it.y, "z" to it.z) }
        res.hitbox?.let { furniture["hitboxes"] = listOf(it) }
        if (res.seats.isNotEmpty()) furniture["seats"] = res.seats
        if (res.lights.isNotEmpty()) furniture["lights"] = res.lights
        res.storage?.let { furniture["storage"] = linkedMapOf("type" to "STORAGE", "rows" to it.rows, "title" to it.title) }
        furniture["drop"] = linkedMapOf("silktouch" to false, "loots" to listOf(linkedMapOf("oraxen_item" to res.oraxenId, "probability" to 1.0)))
        return linkedMapOf(
            "itemname" to "<!i>" + legacyToMini(res.model.displayName),
            "material" to "PAPER",
            "pack" to linkedMapOf("generate_model" to false, "model" to "$namespace:${modelPath(res.oraxenId)}"),
            "mechanics" to linkedMapOf("furniture" to furniture),
        )
    }

    /**
     * [results] = styles that become Oraxen furniture; [retired] = style → block left behind;
     * [notOutput] = styles left out (no design / never placed), listed in the report only.
     */
    fun writeAll(outDir: File, namespace: String, results: List<StyleResult>, rig: Rig,
                 retired: Map<String, String> = emptyMap(), notOutput: List<String> = emptyList()) {
        val items = linkedMapOf<String, Any>()
        for (res in results) {
            val f = File(outDir, "oraxen/pack/assets/$namespace/models/${modelPath(res.oraxenId)}.json")
            f.parentFile.mkdirs()
            f.writeText(modelJson(res))
            items[res.oraxenId] = itemConfig(res, namespace)
            if (res.designed) {
                // Old geometry for side-by-side comparison only; not for the server.
                val ref = File(outDir, "reference/${modelPath(res.oraxenId)}.json")
                ref.parentFile.mkdirs()
                ref.writeText(modelJson(res.referenceElements, res.referenceTextures))
            }
        }
        val itemsFile = File(outDir, "oraxen/items/dicefurniture.yml").apply { parentFile.mkdirs() }
        val opts = DumperOptions().apply { defaultFlowStyle = DumperOptions.FlowStyle.BLOCK; isAllowUnicode = true; indent = 2; width = 200 }
        itemsFile.writeText("# Generated by FurnMigrate converter. Do not edit by hand; re-run the converter.\n" + Yaml(opts).dump(items))

        val mapping = Mapping(namespace, results.associate { res ->
            res.model.style to MappingEntry(
                style = res.model.style, oraxenId = res.oraxenId,
                storage = res.storage != null,
                status = if (res.needsManualDecision) ConversionStatus.PARTIAL else ConversionStatus.OK,
                keepBlocks = res.keepBlocks,
                anchor = res.anchor,
            )
        })
        val withRetired = mapping.copy(entries = mapping.entries + retired.map { (style, block) ->
            style to MappingEntry(style, "", action = MappingAction.RETIRE, retireBlock = block,
                notes = listOf("no Oraxen furniture; held items are dropped, $block stays"))
        })
        File(outDir, "mapping.yml").bufferedWriter().use { MappingIO.write(withRetired, it) }
        File(outDir, "report.md").writeText(Report.render(results, rig, retired, notOutput))
    }
}
