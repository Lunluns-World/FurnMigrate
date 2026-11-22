package world.lunlun.furnmigrate.converter

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import world.lunlun.furnmigrate.common.Nbt
import world.lunlun.furnmigrate.common.Vec3
import java.io.File

/** Armor stand body parts, named like Bukkit's `BodyPart` (FurnitureLib "EulerAngle" keys). */
enum class BodyPart(val mojang: String) {
    HEAD("Head"), BODY("Body"), LEFT_ARM("LeftArm"), RIGHT_ARM("RightArm"), LEFT_LEG("LeftLeg"), RIGHT_LEG("RightLeg");

    companion object {
        fun match(key: String): BodyPart? =
            entries.firstOrNull { it.name.equals(key, true) || it.mojang.equals(key, true) }
    }
}

/** An item in an armor stand slot. [raw] is the full item NBT (needed later for player heads etc.). */
data class SlotItem(val id: String, val raw: Map<String, Any>)

/** One FurnitureLib entity of a model (see findings §1). Offsets are in the canonical frame. */
data class ModelEntity(
    val index: String,
    val type: String,
    val offset: Vec3,
    val yaw: Double,
    val small: Boolean,
    val marker: Boolean,
    val invisible: Boolean,
    val name: String?,
    /** Pose per part in radians (X, Y, Z), only parts that are set. */
    val pose: Map<BodyPart, Vec3>,
    /** Slot name (HEAD, MAINHAND, OFFHAND, CHEST, LEGS, FEET) → item. */
    val items: Map<String, SlotItem>,
) {
    fun poseOf(part: BodyPart): Vec3 = pose[part] ?: Vec3.ZERO
}

/** One entry of `projectData.blockList`. [blockData] like `minecraft:torch` or `minecraft:oak_stairs[facing=east]`. */
data class ModelBlockEntry(val index: String, val offset: Vec3, val blockData: String) {
    val id: String get() = blockData.substringBefore('[')
    val properties: Map<String, String>
        get() = blockData.substringAfter('[', "").removeSuffix("]").split(',').filter { '=' in it }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
}

data class DModel(
    /** File name without extension = FurnitureLib project name = style name used in ObjIDs. */
    val style: String,
    val header: String,
    val systemId: String,
    val displayName: String,
    val placeableSide: String,
    val entities: List<ModelEntity>,
    val blocks: List<ModelBlockEntry>,
    val hasFunctions: Boolean,
)

object DModelParser {
    private fun yaml() = Yaml(SafeConstructor(LoaderOptions().apply { codePointLimit = 64 * 1024 * 1024 }))

    fun parse(file: File): DModel = parse(file.name.removeSuffix(".dModel").removeSuffix(".yml"), file.readText())

    @Suppress("UNCHECKED_CAST")
    fun parse(style: String, text: String): DModel {
        val root = yaml().load<Any?>(text) as? Map<String, Any?> ?: error("$style: empty model file")
        val header = root.keys.first()
        val sec = root[header] as? Map<String, Any?> ?: error("$style: header '$header' is not a section")
        val pd = sec["projectData"] as? Map<String, Any?> ?: emptyMap()
        val ents = (pd["entities"] ?: pd["entitys"]) as? Map<Any, Any?> ?: emptyMap()
        val entities = ents.mapNotNull { (k, v) ->
            val b64 = v as? String ?: return@mapNotNull null
            parseEntity(k.toString(), Nbt.readBase64Gzip(b64))
        }
        val blockList = pd["blockList"] as? Map<Any, Any?> ?: emptyMap()
        val blocks = blockList.mapNotNull { (k, v) ->
            val m = v as? Map<String, Any?> ?: return@mapNotNull null
            parseBlock(k.toString(), m)
        }
        return DModel(
            style = style,
            header = header,
            systemId = sec["system-ID"] as? String ?: header,
            displayName = sec["displayName"] as? String ?: header,
            placeableSide = (sec["placeAbleSide"] as? String ?: "TOP").uppercase(),
            entities = entities,
            blocks = blocks,
            hasFunctions = (pd["functions"] as? List<*>)?.isNotEmpty() == true,
        )
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseEntity(index: String, nbt: Map<String, Any>): ModelEntity {
        val loc = nbt.compound("Location") ?: emptyMap()
        val pose = linkedMapOf<BodyPart, Vec3>()
        // FurnitureLib: "EulerAngle" = compound of compounds in radians; "Pose" = compound or list (list in degrees).
        nbt.compound("EulerAngle")?.forEach { (k, v) ->
            val part = BodyPart.match(k) ?: return@forEach
            val c = v as? Map<String, Any> ?: return@forEach
            pose[part] = Vec3(c.num("X") ?: 0.0, c.num("Y") ?: 0.0, c.num("Z") ?: 0.0)
        }
        if (pose.isEmpty()) nbt.compound("Pose")?.forEach { (k, v) ->
            val part = BodyPart.match(k) ?: return@forEach
            when (v) {
                is Map<*, *> -> {
                    val c = v as Map<String, Any>
                    pose[part] = Vec3(c.num("X") ?: 0.0, c.num("Y") ?: 0.0, c.num("Z") ?: 0.0)
                }
                is List<*> -> {
                    val d = v.map { (it as Number).toDouble() }
                    pose[part] = Vec3(Math.toRadians(d.getOrElse(0) { 0.0 }), Math.toRadians(d.getOrElse(1) { 0.0 }), Math.toRadians(d.getOrElse(2) { 0.0 }))
                }
            }
        }
        val items = linkedMapOf<String, SlotItem>()
        nbt.compound("Inventory")?.forEach { (slot, v) ->
            val c = v as? Map<String, Any> ?: return@forEach // "NONE" = empty
            val id = (c["id"] as? String)?.let { if (':' in it) it else "minecraft:$it" }?.lowercase() ?: return@forEach
            if (id != "minecraft:air") items[slot.uppercase()] = SlotItem(id, c)
        }
        return ModelEntity(
            index = index,
            type = (nbt["EntityType"] as? String ?: "armor_stand").lowercase(),
            offset = Vec3(loc.num("X-Offset") ?: 0.0, loc.num("Y-Offset") ?: 0.0, loc.num("Z-Offset") ?: 0.0),
            yaw = loc.num("Yaw") ?: 0.0,
            small = nbt.flag("Small"),
            marker = nbt.flag("Marker"),
            invisible = nbt.flag("Invisible"),
            name = (nbt["CustomName"] ?: nbt["Name"]) as? String,
            pose = pose,
            items = items,
        )
    }

    /** Same rules as FurnitureLib `ModelBlockAquaticUpdate(YamlConfiguration, key)`. */
    internal fun parseBlock(index: String, m: Map<String, Any?>): ModelBlockEntry? {
        val x = (m["xOffset"] as? Number)?.toDouble() ?: 0.0
        val y = (m["yOffset"] as? Number)?.toDouble() ?: 0.0
        val z = (m["zOffset"] as? Number)?.toDouble() ?: 0.0
        var data = (m["blockData"] as? String).orEmpty()
        var material = (m["material"] as? String).orEmpty()
        if (material.startsWith("WALL_SIGN")) material = material.replace("WALL_SIGN", "OAK_WALL_SIGN")
        if (data.isEmpty() && material.isNotEmpty()) {
            data = "minecraft:" + material.lowercase()
            (m["Rotation"] as? String)?.let { data += "[facing=${it.lowercase()}]" }
        }
        if (data.isEmpty()) return null
        data = data.lowercase()
        if (':' !in data.substringBefore('[')) data = "minecraft:$data"
        if (data.startsWith("minecraft:air")) return null
        return ModelBlockEntry(index, Vec3(x, y, z), data)
    }
}
