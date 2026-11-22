package world.lunlun.furnmigrate.converter

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import world.lunlun.furnmigrate.common.Axis
import world.lunlun.furnmigrate.common.Vec3
import java.io.Closeable
import java.io.File
import java.util.zip.ZipFile

enum class FaceDir(val normal: Vec3) {
    DOWN(Vec3(0.0, -1.0, 0.0)), UP(Vec3(0.0, 1.0, 0.0)),
    NORTH(Vec3(0.0, 0.0, -1.0)), SOUTH(Vec3(0.0, 0.0, 1.0)),
    WEST(Vec3(-1.0, 0.0, 0.0)), EAST(Vec3(1.0, 0.0, 0.0));

    val key get() = name.lowercase()

    companion object {
        fun of(key: String) = entries.first { it.key == key }
        fun closest(v: Vec3) = entries.maxBy { it.normal.dot(v) }
    }
}

/** [uv] null = automatic UV from the element position (vanilla behaviour). */
data class Face(val uv: List<Double>?, val texture: String, val rotation: Int = 0)

data class ElementRotation(val origin: Vec3, val axis: Axis, val angle: Double)

data class Element(
    val from: Vec3,
    val to: Vec3,
    val rotation: ElementRotation?,
    val faces: Map<FaceDir, Face>,
    val shade: Boolean = true,
)

/** Vanilla `ItemTransform`: rotation in degrees (applied X, Y, Z), translation in pixels, scale. */
data class ItemTransform(val rotation: Vec3, val translation: Vec3, val scale: Vec3) {
    companion object {
        val IDENTITY = ItemTransform(Vec3.ZERO, Vec3.ZERO, Vec3(1.0, 1.0, 1.0))
    }
}

data class ResolvedModel(
    val id: String,
    val textures: Map<String, String>,
    val elements: List<Element>,
    val display: Map<String, ItemTransform>,
    /** The parent chain ends in `builtin/generated` (flat item sprite). */
    val generated: Boolean,
) {
    /** Follows `#name` references; returns a namespaced texture id or null. */
    fun resolveTexture(ref: String): String? {
        var cur = ref
        repeat(16) {
            if (!cur.startsWith("#")) return namespaced(cur)
            cur = textures[cur.substring(1)] ?: return null
        }
        return null
    }
}

/** A block model placed by a blockstate variant, with the variant's x/y rotation in degrees. */
data class PlacedBlockModel(val model: ResolvedModel, val x: Int, val y: Int)

fun namespaced(id: String): String = if (':' in id) id else "minecraft:$id"

/** Reads models, item definitions and blockstates from a Minecraft client jar (or an extracted folder). */
class VanillaAssets private constructor(
    private val readBytes: (String) -> ByteArray?,
    private val closer: Closeable?,
) : Closeable {

    private val read: (String) -> String? = { p -> readBytes(p)?.decodeToString() }

    private val cache = HashMap<String, ResolvedModel?>()

    override fun close() {
        closer?.close()
    }

    private fun json(path: String): JsonObject? = read(path)?.let { JsonParser.parseString(it).asJsonObject }

    private val textureCache = HashMap<String, java.awt.image.BufferedImage?>()

    /** Texture image for an id like `minecraft:block/lever` (first animation frame if animated). */
    fun texture(id: String): java.awt.image.BufferedImage? = textureCache.getOrPut(namespaced(id)) {
        val ns = namespaced(id)
        val bytes = readBytes("assets/${ns.substringBefore(':')}/textures/${ns.substringAfter(':')}.png") ?: return@getOrPut null
        val img = javax.imageio.ImageIO.read(bytes.inputStream()) ?: return@getOrPut null
        if (img.height > img.width) img.getSubimage(0, 0, img.width, img.width) else img
    }

    private fun pathOf(kind: String, id: String): String {
        val ns = namespaced(id)
        return "assets/${ns.substringBefore(':')}/$kind/${ns.substringAfter(':')}.json"
    }

    /** Resolves a model id like `minecraft:block/oak_trapdoor_bottom` with its whole parent chain. */
    fun model(id: String): ResolvedModel? = cache.getOrPut(namespaced(id)) { loadModel(namespaced(id), 0) }

    private fun loadModel(id: String, depth: Int): ResolvedModel? {
        require(depth < 32) { "model parent chain too deep at $id" }
        if (id == "minecraft:builtin/generated") return ResolvedModel(id, emptyMap(), emptyList(), emptyMap(), true)
        if (id.startsWith("minecraft:builtin/")) return ResolvedModel(id, emptyMap(), emptyList(), emptyMap(), false)
        val o = json(pathOf("models", id)) ?: return null
        val parent = o.get("parent")?.asString?.let { loadModel(namespaced(it), depth + 1) }
        val textures = LinkedHashMap(parent?.textures ?: emptyMap())
        o.getAsJsonObject("textures")?.entrySet()?.forEach { (k, v) -> textures[k] = v.asString }
        val elements = o.getAsJsonArray("elements")?.map { parseElement(it.asJsonObject) } ?: parent?.elements ?: emptyList()
        val display = LinkedHashMap(parent?.display ?: emptyMap())
        o.getAsJsonObject("display")?.entrySet()?.forEach { (k, v) -> display[k] = parseTransform(v.asJsonObject) }
        val generated = if (o.has("elements")) false else parent?.generated ?: false
        return ResolvedModel(id, textures, elements, display, generated)
    }

    /**
     * Item model for an item id via `assets/<ns>/items/<id>.json` (1.21.4+ item definitions).
     * Returns null with a reason when the definition is not a plain model (player heads, banners, …).
     */
    fun itemModel(itemId: String): Pair<ResolvedModel?, String?> {
        val def = json(pathOf("items", itemId)) ?: return null to "no item definition for $itemId"
        val ref = plainModelRef(def.getAsJsonObject("model")) ?: return null to "item $itemId uses a special/conditional renderer"
        val m = model(ref) ?: return null to "model $ref not found"
        return m to null
    }

    /**
     * For `minecraft:special` items (banners, heads): the kind of special renderer and the base
     * model that carries the display transforms, or null when the item is not special.
     */
    fun specialItem(itemId: String): Pair<String, ResolvedModel>? {
        val def = json(pathOf("items", itemId)) ?: return null
        val o = def.getAsJsonObject("model") ?: return null
        if (o.get("type")?.asString?.let(::namespaced) != "minecraft:special") return null
        val kind = o.getAsJsonObject("model")?.get("type")?.asString?.let(::namespaced) ?: return null
        val base = o.get("base")?.asString?.let(::model) ?: return null
        return kind to base
    }

    private fun plainModelRef(o: JsonObject?): String? {
        if (o == null) return null
        return when (o.get("type")?.asString?.let(::namespaced)) {
            "minecraft:model" -> o.get("model")?.asString
            "minecraft:condition" -> plainModelRef(o.getAsJsonObject("on_false"))
            else -> o.getAsJsonObject("fallback")?.let(::plainModelRef)
        }
    }

    /** Models placed for a block state (variants or multipart), or null with a reason. */
    fun blockModels(blockId: String, props: Map<String, String>): Pair<List<PlacedBlockModel>, String?> {
        val bs = json(pathOf("blockstates", blockId)) ?: return emptyList<PlacedBlockModel>() to "no blockstate for $blockId"
        val chosen = mutableListOf<JsonObject>()
        bs.getAsJsonObject("variants")?.let { variants ->
            val match = variants.entrySet().firstOrNull { (key, _) -> variantMatches(key, props) }
                ?: variants.entrySet().firstOrNull()
            match?.value?.let { chosen += firstVariant(it) }
        }
        bs.getAsJsonArray("multipart")?.forEach { part ->
            val p = part.asJsonObject
            val w = p.getAsJsonObject("when")
            if (w == null || whenMatches(w, props)) chosen += firstVariant(p.get("apply"))
        }
        val placed = chosen.mapNotNull { v ->
            val m = model(v.get("model").asString) ?: return@mapNotNull null
            PlacedBlockModel(m, v.get("x")?.asInt ?: 0, v.get("y")?.asInt ?: 0)
        }
        return placed to (if (placed.isEmpty()) "no model for $blockId$props" else null)
    }

    private fun firstVariant(e: JsonElement): JsonObject = if (e.isJsonArray) e.asJsonArray[0].asJsonObject else e.asJsonObject

    private fun variantMatches(key: String, props: Map<String, String>): Boolean {
        if (key.isEmpty()) return true
        return key.split(',').all { kv ->
            val k = kv.substringBefore('='); val v = kv.substringAfter('=')
            props[k]?.equals(v, true) ?: false
        }
    }

    private fun whenMatches(w: JsonObject, props: Map<String, String>): Boolean {
        w.getAsJsonArray("OR")?.let { arr -> return arr.any { whenMatches(it.asJsonObject, props) } }
        w.getAsJsonArray("AND")?.let { arr -> return arr.all { whenMatches(it.asJsonObject, props) } }
        return w.entrySet().all { (k, v) -> props[k]?.let { it in v.asString.split('|') } ?: false }
    }

    private fun vec(o: JsonElement?, def: Vec3 = Vec3.ZERO): Vec3 {
        val a = o?.takeIf { it.isJsonArray }?.asJsonArray ?: return def
        return Vec3(a[0].asDouble, a[1].asDouble, a[2].asDouble)
    }

    private fun parseTransform(o: JsonObject) =
        ItemTransform(vec(o.get("rotation")), vec(o.get("translation")), vec(o.get("scale"), Vec3(1.0, 1.0, 1.0)))

    private fun parseElement(o: JsonObject): Element {
        val rot = o.getAsJsonObject("rotation")?.let {
            ElementRotation(vec(it.get("origin"), Vec3(8.0, 8.0, 8.0)), Axis.valueOf(it.get("axis").asString.uppercase()), it.get("angle")?.asDouble ?: 0.0)
        }
        val faces = linkedMapOf<FaceDir, Face>()
        o.getAsJsonObject("faces")?.entrySet()?.forEach { (k, v) ->
            val f = v.asJsonObject
            faces[FaceDir.of(k)] = Face(
                uv = f.getAsJsonArray("uv")?.map { it.asDouble },
                texture = f.get("texture").asString,
                rotation = f.get("rotation")?.asInt ?: 0,
            )
        }
        return Element(vec(o.get("from")), vec(o.get("to")), rot, faces, o.get("shade")?.asBoolean ?: true)
    }

    companion object {
        fun open(file: File): VanillaAssets {
            if (file.isDirectory) return VanillaAssets({ p -> File(file, p).takeIf { it.isFile }?.readBytes() }, null)
            val zip = ZipFile(file)
            return VanillaAssets({ p -> zip.getEntry(p)?.let { e -> zip.getInputStream(e).use { it.readBytes() } } }, zip)
        }
    }
}
