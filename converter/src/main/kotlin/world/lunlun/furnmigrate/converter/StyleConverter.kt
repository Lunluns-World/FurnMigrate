package world.lunlun.furnmigrate.converter

import world.lunlun.furnmigrate.common.Facing
import world.lunlun.furnmigrate.common.IVec3
import world.lunlun.furnmigrate.common.KeepBlock
import world.lunlun.furnmigrate.common.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PartNote(val part: String, val what: String)

data class AngleError(val part: String, val errorDeg: Double)

data class StyleResult(
    val model: DModel,
    val oraxenId: String,
    val elements: List<OutElement>,
    val textures: Map<String, String>,
    /** Oraxen barrier offsets (Oraxen yaw-0 frame). */
    val barriers: List<IVec3>,
    val seats: List<String>,
    val lights: List<String>,
    /** "x,y,z w,h" or null when barriers exist. */
    val hitbox: String?,
    /** Model was shrunk by 1/displayScale to fit Minecraft's ±16 px limit; the display scales it back. */
    val displayScale: Double,
    val converted: List<PartNote>,
    val skipped: List<PartNote>,
    val angleErrors: List<AngleError>,
    val unmatchedFunctions: List<String>,
    /** Functional real blocks of the original that stay real blocks (see MappingEntry.keepBlocks). */
    val keepBlocks: List<KeepBlock>,
    val warnings: List<String>,
    /** Model comes from a hand-made design (designs/) instead of the automatic conversion. */
    val designed: Boolean = false,
    /** The automatic conversion, kept as size/position reference when [designed]. */
    val referenceElements: List<OutElement> = emptyList(),
    val referenceTextures: Map<String, String> = emptyMap(),
    val storage: StorageSpec? = null,
    /** See MappingEntry.anchor; seats/lights/barriers/elements are already relative to it. */
    val anchor: IVec3 = IVec3(0, 0, 0),
) {
    /** For designed models skipped parts and angle errors of the old geometry no longer matter. */
    val maxAngleError: Double get() = if (designed) 0.0 else angleErrors.maxOfOrNull { it.errorDeg } ?: 0.0
    val needsManualDecision: Boolean
        get() = (!designed && skipped.isNotEmpty()) || maxAngleError > 10.0 || unmatchedFunctions.isNotEmpty()
}

/** Vanilla block light emission for blocks that DiceFurniture models put in their blockList. */
val LIGHT_LEVELS = mapOf(
    "minecraft:torch" to 14, "minecraft:wall_torch" to 14, "minecraft:soul_torch" to 10, "minecraft:soul_wall_torch" to 10,
    "minecraft:redstone_torch" to 7, "minecraft:redstone_wall_torch" to 7, "minecraft:lantern" to 15, "minecraft:soul_lantern" to 10,
    "minecraft:glowstone" to 15, "minecraft:sea_lantern" to 15, "minecraft:jack_o_lantern" to 15, "minecraft:campfire" to 15,
    "minecraft:end_rod" to 14, "minecraft:light" to 15,
)

/** Blocks without (meaningful) collision: no barrier is generated for them. */
private val NON_SOLID = Regex("minecraft:(.*torch|.*_sign|.*_button|lever|.*pressure_plate|.*carpet|tripwire.*|rail|.*_rail|.*flower|.*sapling|light|air|redstone_wire)")

/**
 * Functional blocks in a model's blockList stay real blocks after the migration (the migrator saves and
 * restores them with their state and contents), so they keep working: crafting, sleeping, storing.
 */
val KEEP_REAL = Regex("minecraft:(crafting_table|chest|trapped_chest|ender_chest|barrel|.*shulker_box|.*_bed|furnace|blast_furnace|smoker|" +
    "hopper|dropper|dispenser|.*anvil|enchanting_table|brewing_stand|lectern|jukebox|cartography_table|smithing_table|loom|stonecutter|grindstone)")

/** Names FurnitureLib/DiceFurniture give marker stands (findings §1). */
private val SEAT_MARKER = Regex("^#(SITZ|Mount:).*", RegexOption.IGNORE_CASE)

class StyleConverter(private val assets: VanillaAssets, private val rig: Rig = Rig()) {

    private class ModelCollector {
        val elements = mutableListOf<OutElement>()
        val textures = LinkedHashMap<String, String>()
        fun key(textureId: String): String =
            textures.entries.firstOrNull { it.value == textureId }?.key ?: textures.size.toString().also { textures[it] = textureId }
    }

    fun convert(model: DModel, oraxenId: String): StyleResult {
        val out = ModelCollector()
        val converted = mutableListOf<PartNote>()
        val skipped = mutableListOf<PartNote>()
        val angles = mutableListOf<AngleError>()
        val warnings = mutableListOf<String>()
        val functions = mutableListOf<String>()
        val seats = mutableListOf<String>()
        val lights = mutableListOf<String>()
        val barriers = linkedSetOf<IVec3>()
        val keep = mutableListOf<KeepBlock>()
        val toOut = ArmorStandRig.canonicalToOutput(rig)

        fun addModel(part: String, src: ResolvedModel, toCanonical: Affine): Double {
            val elements = if (src.generated) generatedSprite(src) else src.elements
            var worst = 0.0
            for (el in elements) {
                val r = ElementBaker.bake(el, toOut after toCanonical) { ref -> src.resolveTexture(ref)?.let { "#" + out.key(it) } }
                if (r.warning != null) warnings += "$part: ${r.warning}"
                if (r.warning?.startsWith("mirrored") == true) continue
                out.elements += r.element
                worst = max(worst, r.errorDeg)
            }
            return worst
        }

        for (e in model.entities) {
            val label = "entity ${e.index}"
            val name = e.name?.takeIf { it.startsWith("#") || it.startsWith("/") }
            if (e.type != "armor_stand") { skipped += PartNote(label, "entity type ${e.type} not supported"); continue }
            if (name != null) {
                when {
                    SEAT_MARKER.matches(name) -> {
                        val g = Facing.canonicalToOraxenGround(e.offset)
                        seats += "${fmt(g.x)},${fmt(g.y)},${fmt(g.z)} ${fmt(e.yaw)}"
                        converted += PartNote(label, "seat ($name) → Oraxen seat")
                    }
                    name.startsWith("#Inventory:", true) -> functions += "$label: storage $name → needs Oraxen storage (not in trial)"
                    name.startsWith("#Light:", true) -> functions += "$label: toggle light $name (needs LightAPI)"
                    name.equals("#ITEM#", true) || name.equals("#BLOCK#", true) ->
                        functions += "$label: $name holds a player item; Oraxen has no equivalent (migrator drops/stores the item)"
                    name.startsWith("#DYE_", true) -> functions += "$label: $name recolor with dye; no Oraxen equivalent (fixed colour)"
                    name.startsWith("/") -> functions += "$label: click command '$name'; use Oraxen furniture events manually"
                    else -> functions += "$label: DiceFurniture marker '$name' (Java behaviour, no Oraxen equivalent)"
                }
            }
            for ((slot, item) in e.items) {
                val part = "$label $slot ${item.id.removePrefix("minecraft:")}"
                if (slot !in setOf("HEAD", "MAINHAND", "OFFHAND")) { skipped += PartNote(part, "armor slot item not supported"); continue }
                val special = assets.specialItem(item.id)?.let { (kind, base) -> specialStandIn(item.id, kind, base) }
                val (m, why) = special?.let { it to null } ?: assets.itemModel(item.id)
                if (m == null) { skipped += PartNote(part, why ?: "no model"); continue }
                val transform = when (slot) {
                    "HEAD" -> ArmorStandRig.headItem(e, m, rig)
                    else -> ArmorStandRig.handItem(e, m, slot == "OFFHAND", rig)
                }
                val err = addModel(part, m, transform)
                angles += AngleError(part, err)
                val approx = slot != "HEAD" || special != null
                converted += PartNote(part, buildString {
                    append(if (special != null) "special item → simple stand-in shape" else if (m.generated) "flat item sprite → extruded per pixel" else "block model")
                    if (approx) append(" (approximate, size reference only)")
                })
            }
        }

        for (b in model.blocks) {
            val label = "block ${b.index} ${b.blockData.removePrefix("minecraft:")}"
            val cell = IVec3(
                Math.floor(0.5 + b.offset.x).toInt(), Math.floor(b.offset.y + 1e-9).toInt(), Math.floor(0.5 + b.offset.z).toInt(),
            )
            if (b.id == "minecraft:barrier") {
                barriers += Facing.canonicalToOraxenBlock(cell)
                converted += PartNote(label, "barrier → Oraxen barrier")
                continue
            }
            LIGHT_LEVELS[b.id]?.let { lvl ->
                val g = Facing.canonicalToOraxenBlock(cell)
                lights += "${g.x},${g.y},${g.z} $lvl"
                converted += PartNote(label, "light source → Oraxen light $lvl")
            }
            val keepReal = KEEP_REAL.matches(b.id)
            if (keepReal) {
                keep += KeepBlock(cell, b.id)
                converted += PartNote(label, "functional block → stays a real block (saved and restored by the migrator)")
            }
            val (placed, why) = assets.blockModels(b.id, b.properties)
            if (placed.isEmpty()) { skipped += PartNote(label, why ?: "no model") } else {
                var err = 0.0
                for (p in placed) err = max(err, addModel(label, p.model, ArmorStandRig.placedBlock(b.offset, p.x, p.y)))
                angles += AngleError(label, err)
                converted += PartNote(label, "real block → model geometry")
            }
            if (!keepReal && !NON_SOLID.matches(b.id)) barriers += Facing.canonicalToOraxenBlock(cell)
        }
        if (model.hasFunctions) functions += "projectData.functions present (e.g. recolor); no Oraxen equivalent"

        // Fit into Minecraft's model bounds (-16..32 px) by shrinking around the centre; the display scales back up.
        var extent = 0.0
        for (el in out.elements) for (v in listOfNotNull(el.from, el.to, el.rotation?.origin)) {
            extent = max(extent, max(abs(v.x - 8), max(abs(v.y - 8), abs(v.z - 8))))
        }
        val shrink = if (extent > 24.0) 24.0 / extent else 1.0
        val elements = if (shrink == 1.0) out.elements else out.elements.map { shrinkElement(it, shrink) }
        if (shrink != 1.0) warnings += "model is larger than 3×3×3 blocks; shrunk ×${fmt(shrink)} and scaled back by the display"

        return StyleResult(
            model = model, oraxenId = oraxenId, elements = elements, textures = out.textures,
            barriers = barriers.toList(), seats = seats, lights = lights,
            hitbox = if (barriers.isEmpty()) avoidKeepBlocks(encloseSeats(hitboxFor(out.elements), seats), keep.map { Facing.canonicalToOraxenBlock(it.cell) }) else null,
            displayScale = 1.0 / shrink,
            converted = converted, skipped = skipped, angleErrors = angles.filter { it.errorDeg > 0.05 },
            unmatchedFunctions = functions, keepBlocks = keep, warnings = warnings,
        )
    }

    /**
     * Rough geometry for items drawn by a special renderer (heads, banners), in item model pixels,
     * keeping the base model's display transforms. Only good enough as a size reference.
     */
    private fun specialStandIn(itemId: String, kind: String, base: ResolvedModel): ResolvedModel? {
        val name = itemId.substringAfter(':')
        fun cube(f: Vec3, t: Vec3) = Element(f, t, null, FaceDir.entries.associateWith { Face(null, "#t") })
        return when (kind) {
            "minecraft:banner" -> {
                val colour = name.removeSuffix("_banner")
                // standing banner scaled 2/3: pole 28 px, cloth ≈ 13 × 27 px
                ResolvedModel("standin:$name", mapOf("t" to "minecraft:block/${colour}_wool", "p" to "minecraft:block/oak_planks"),
                    listOf(
                        Element(Vec3(7.3, 0.0, 7.3), Vec3(8.7, 28.0, 8.7), null, FaceDir.entries.associateWith { Face(null, "#p") }),
                        cube(Vec3(1.3, 1.3, 7.7), Vec3(14.7, 28.0, 8.3)),
                    ), base.display, false)
            }
            "minecraft:player_head", "minecraft:head" -> {
                val tex = if ("skeleton" in name) "minecraft:block/bone_block_side" else "minecraft:block/brown_wool"
                // skull on a head is ~0.59 blocks, sitting on the neck
                ResolvedModel("standin:$name", mapOf("t" to tex), listOf(cube(Vec3(0.4, 1.6, 0.4), Vec3(15.6, 16.8, 15.6))),
                    base.display, false)
            }
            else -> null
        }
    }

    /** item/generated: extrude the sprite like vanilla does — one 1 px thick box per run of opaque pixels in a row. */
    private fun generatedSprite(m: ResolvedModel): List<Element> {
        val texId = m.resolveTexture("#layer0") ?: return emptyList()
        val img = assets.texture(texId) ?: return SpriteExtruder.plainSlab()
        return SpriteExtruder.extrude(img.width, img.height) { x, y -> (img.getRGB(x, y) ushr 24) != 0 }
    }

    private fun shrinkElement(e: OutElement, k: Double): OutElement {
        val c = Vec3(8.0, 8.0, 8.0)
        fun s(v: Vec3) = (v - c) * k + c
        return e.copy(from = s(e.from), to = s(e.to), rotation = e.rotation?.let { it.copy(origin = s(it.origin)) })
    }

}

/** Centred interaction box covering the model (output px → blocks above the floor). */
internal fun hitboxFor(elements: List<OutElement>): String {
    run {
        if (elements.isEmpty()) return "0,0,0 1,1"
        var half = 0.0; var top = 0.0
        for (el in elements) for (v in listOf(el.from, el.to)) {
            half = max(half, max(abs(v.x - 8), abs(v.z - 8)) / 16.0)
            top = max(top, (v.y - 8) / 16.0 + 0.5)
        }
        val w = (half * 2).coerceIn(0.5, 3.0)
        val h = top.coerceIn(0.3, 3.0)
        return "0,0,0 ${fmt(w)},${fmt(h)}"
    }
}

/** Height of the invisible armor stand Oraxen spawns for every seat (non-marker, 0.5 wide). */
const val SEAT_STAND_HEIGHT = 1.975
const val SEAT_STAND_WIDTH = 0.5

/**
 * Grows [hitbox] ("x,y,z w,h", centred) so it fully contains every seat stand standing inside its
 * footprint. Otherwise the stand catches the player's click and the furniture can neither be sat on
 * nor broken (dice_campchair: 0.5 × 0.531 hitbox inside a 0.5 × 1.975 stand starting at y −1.281).
 */
internal fun encloseSeats(hitbox: String, seats: List<String>): String {
    val (pos, size) = hitbox.trim().split(Regex("\\s+"))
    val (w0, h0) = size.split(',').map { it.toDouble() }
    var w = w0; var h = h0
    for (seat in seats) {
        val (sx, sy, sz) = seat.trim().split(Regex("\\s+"))[0].split(',').map { it.toDouble() }
        val reach = max(abs(sx), abs(sz)) + SEAT_STAND_WIDTH / 2
        if (reach > w0 / 2 + 1e-9) continue          // stand outside the hitbox: it does not cover it
        w = max(w, min(3.0, 2 * reach + 0.125))
        h = max(h, sy + SEAT_STAND_HEIGHT + 0.05)
    }
    return "$pos ${fmt(w)},${fmt(h)}"
}

/**
 * Shrinks the centred [hitbox] so it stays off every kept real block ([keepCells]: Oraxen block offsets
 * relative to the furniture block). An Interaction entity over a crafting table / bed / chest takes the
 * player's clicks, so the block could neither be used nor placed (Tent1 crafting table).
 */
internal fun avoidKeepBlocks(hitbox: String, keepCells: List<IVec3>): String {
    val (pos, size) = hitbox.trim().split(Regex("\\s+"))
    var (w, h) = size.split(',').map { it.toDouble() }
    for (c in keepCells) {
        if (c.y < 0 || c.y >= h) continue
        val d = max(abs(c.x), abs(c.z))
        require(d > 0) { "kept block $c is the furniture's own block" }
        w = min(w, 2.0 * d - 1.0)
    }
    return "$pos ${fmt(w)},${fmt(h)}"
}

internal fun fmt(d: Double): String {
    val r = (d * 1000.0).roundToInt() / 1000.0
    val v = if (r == -0.0) 0.0 else r
    return if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}

