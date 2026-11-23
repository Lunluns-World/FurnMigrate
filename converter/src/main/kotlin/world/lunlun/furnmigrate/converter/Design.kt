package world.lunlun.furnmigrate.converter

import world.lunlun.furnmigrate.common.Axis
import world.lunlun.furnmigrate.common.Facing
import world.lunlun.furnmigrate.common.IVec3
import world.lunlun.furnmigrate.common.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A hand-made replacement model for one style.
 *
 * Coordinates are **output model pixels**, the same frame the automatic conversion writes:
 * the furniture's block is x/z 0..16, the floor is y = 0, one block = 16 px, and the front of the
 * furniture (where you sit, where a screen faces) is −z (north side of the model).
 * Usable range is −16..32 on every axis (larger models are shrunk and scaled back by the display).
 *
 * Seats, lights and barriers still come from the original furniture so the migrated piece
 * behaves like the old one; a design may override them when the new shape needs it.
 */
class Design(
    val style: String,
    val textures: Map<String, String>,
    val elements: List<Element>,
    /** Oraxen seat strings ("x,y,z yaw"); null = keep the original seats. */
    val seats: List<String>? = null,
    /** Oraxen light strings ("x,y,z level"); null = keep the original lights. */
    val lights: List<String>? = null,
    /** Oraxen storage for styles whose original held player items (contents are moved in by the migrator). */
    val storage: StorageSpec? = null,
    /** DiceFurniture functions this design deliberately handles: marker text → how. */
    val handled: Map<String, String> = emptyMap(),
    /** Canonical cell of the old piece that becomes the Oraxen furniture's block (see MappingEntry.anchor). */
    val anchor: IVec3 = IVec3(0, 0, 0),
    /** Item name to use instead of the model's displayName (legacy `&` colours), e.g. to fix a typo. */
    val displayName: String? = null,
    /** Oraxen barrier offsets (Oraxen yaw-0 frame, like seats and lights); null = keep the original barriers. */
    val barriers: List<IVec3>? = null,
    /** Where players may place it; null = keep the original (floor only). */
    val placing: Placing? = null,
    /** Interaction hitbox "x,y,z w,h" instead of the one computed from the geometry (only without barriers). */
    val hitbox: String? = null,
)

/** Oraxen `limited_placing`: floor, wall and/or ceiling. */
data class Placing(val floor: Boolean = true, val wall: Boolean = false, val roof: Boolean = false) {
    init { require(floor || wall || roof) { "furniture must be placeable somewhere" } }

    companion object {
        val FLOOR = Placing()
    }
}

/** Oraxen `mechanics.furniture.storage` (type STORAGE). */
data class StorageSpec(val rows: Int, val title: String)

class DesignBuilder(private val style: String) {
    private val textures = linkedMapOf<String, String>()
    private val elements = mutableListOf<Element>()
    private var seats: List<String>? = null
    private var lights: List<String>? = null
    private var storage: StorageSpec? = null
    private val handled = linkedMapOf<String, String>()
    private var anchor = IVec3(0, 0, 0)
    private var displayName: String? = null
    private var barriers: List<IVec3>? = null
    private var placing: Placing? = null
    private var hitbox: String? = null

    /** Registers a texture under a short key, e.g. `tex("wood", "block/oak_planks")`. */
    fun tex(key: String, id: String) {
        textures[key] = namespaced(id)
    }

    /**
     * Adds a box from (x0, y0, z0) to (x1, y1, z1). [faces] limits which faces are drawn
     * (default all); [faceTex] overrides the texture for single faces; [rot] tilts the box
     * (single axis, ±22.5 / ±45) around [origin] (default: box centre).
     */
    fun box(
        x0: Number, y0: Number, z0: Number, x1: Number, y1: Number, z1: Number,
        tex: String,
        faces: Set<FaceDir> = FaceDir.entries.toSet(),
        faceTex: Map<FaceDir, String> = emptyMap(),
        rot: Pair<Axis, Double>? = null,
        origin: Vec3? = null,
        shade: Boolean = true,
        /** Faces whose texture is turned 90° (e.g. the bark of a log lying along x). */
        uvTurn: Set<FaceDir> = emptySet(),
    ) {
        require(tex in textures) { "$style: unknown texture key '$tex'" }
        faceTex.values.forEach { require(it in textures) { "$style: unknown texture key '$it'" } }
        val from = Vec3(min(x0.toDouble(), x1.toDouble()), min(y0.toDouble(), y1.toDouble()), min(z0.toDouble(), z1.toDouble()))
        val to = Vec3(max(x0.toDouble(), x1.toDouble()), max(y0.toDouble(), y1.toDouble()), max(z0.toDouble(), z1.toDouble()))
        rot?.let { require(it.second in LEGAL_ANGLES) { "$style: element angle ${it.second} is not legal in Java models" } }
        val rotation = rot?.let { ElementRotation(origin ?: (from + to) * 0.5, it.first, it.second) }
        val f = faces.associateWith { dir ->
            Face(designUv(dir, from, to), "#" + (faceTex[dir] ?: tex), if (dir in uvTurn) 90 else 0)
        }
        elements += Element(from, to, rotation, f, shade)
    }

    /**
     * A thin bar from (x0, y0) to (x1, y1) in the x/y plane (z from [z0] to [z1]), for ropes, sticks and
     * slanted panels. The slope is snapped to a legal Java angle; [thick] is the bar's thickness in px.
     */
    fun slantXY(x0: Double, y0: Double, x1: Double, y1: Double, z0: Number, z1: Number, thick: Double, tex: String) {
        val dx = x1 - x0; val dy = y1 - y0
        val len = Math.hypot(dx, dy)
        val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2
        // Horizontal-ish bars are laid along x, steep ones along y, then tilted by the nearest legal angle.
        val horizontal = Math.abs(dx) >= Math.abs(dy)
        // A bar has no direction: flip (dx, dy) so the main axis component is positive, keeping |angle| ≤ 45.
        val s = if (horizontal) Math.signum(dx) else Math.signum(dy)
        val raw = if (horizontal) Math.toDegrees(Math.atan2(dy * s, dx * s)) else -Math.toDegrees(Math.atan2(dx * s, dy * s))
        val angle = LEGAL_ANGLES.minBy { Math.abs(it - raw) }
        val (hx, hy) = if (horizontal) len / 2 to thick / 2 else thick / 2 to len / 2
        box(cx - hx, cy - hy, z0, cx + hx, cy + hy, z1, tex, rot = if (angle == 0.0) null else Axis.Z to angle, origin = Vec3(cx, cy, (z0.toDouble() + z1.toDouble()) / 2))
    }

    /** Same box mirrored across the model's centre line x = 8 as well (for symmetric legs etc.). */
    fun boxMirrorX(x0: Number, y0: Number, z0: Number, x1: Number, y1: Number, z1: Number, tex: String, faces: Set<FaceDir> = FaceDir.entries.toSet()) {
        box(x0, y0, z0, x1, y1, z1, tex, faces)
        box(16 - x1.toDouble(), y0, z0, 16 - x0.toDouble(), y1, z1, tex, faces)
    }

    fun seats(vararg s: String) { seats = s.toList() }
    fun lights(vararg s: String) { lights = s.toList() }
    fun storage(rows: Int, title: String) { require(rows in 1..6); storage = StorageSpec(rows, title) }

    /** Marks every unmatched DiceFurniture function containing [marker] as decided: [how]. */
    fun handles(marker: String, how: String) { handled[marker] = how }

    /**
     * Places and breaks the furniture at canonical cell ([x], [y], [z]) of the old piece instead of its
     * origin. In design pixels that cell is x 8−16·[x] ± 8, z 8−16·[z] ± 8. Coordinates in this design stay
     * relative to the old origin; the applier moves everything.
     */
    fun anchor(x: Int, y: Int, z: Int) { anchor = IVec3(x, y, z) }

    /** Item name instead of the model's displayName (legacy `&` colours like the models use). */
    fun displayName(name: String) { displayName = name }

    /**
     * Solid barrier blocks instead of the original ones, as Oraxen offsets (x = −design cell x, z = design
     * cell z: the design cell x 16..32 is Oraxen x −1, the cell in front, z −16..0, is Oraxen z −1).
     * Players can stand on them and place blocks on top; there is no interaction hitbox then.
     */
    fun barriers(vararg cells: IVec3) { barriers = cells.toList() }

    /** Every cell of the design footprint [x0]..[x1] × [z0]..[z1] (design cells, as above) at heights [y0]..[y1]. */
    fun barrierGrid(x0: Int, x1: Int, z0: Int, z1: Int, y0: Int = 0, y1: Int = 0) {
        barriers = (y0..y1).flatMap { y -> (x0..x1).flatMap { x -> (z0..z1).map { z -> IVec3(-x, y, z) } } }
    }

    fun placing(floor: Boolean = true, wall: Boolean = false, roof: Boolean = false) { placing = Placing(floor, wall, roof) }

    /** Interaction hitbox (centred square [width] × [height] blocks) instead of the computed one. */
    fun hitbox(width: Double, height: Double) { hitbox = "0,0,0 ${fmt(width)},${fmt(height)}" }

    fun build() = Design(style, textures, elements.toList(), seats, lights, storage, handled, anchor, displayName, barriers, placing, hitbox)
}

fun design(style: String, block: DesignBuilder.() -> Unit): Design = DesignBuilder(style).apply(block).build()

/**
 * Texture coordinates for a designed box: vanilla's automatic UV, computed on the box moved
 * into the 0..16 cell (so neighbouring boxes line up like real blocks). Boxes longer than
 * 16 px use the whole texture.
 */
fun designUv(face: FaceDir, from: Vec3, to: Vec3): List<Double> {
    fun fold(a: Double, b: Double): Pair<Double, Double> {
        val len = b - a
        if (len >= 16.0) return 0.0 to 16.0
        var s = ((a % 16.0) + 16.0) % 16.0
        if (s + len > 16.0) s = 16.0 - len
        return s to s + len
    }
    val (fx, tx) = fold(from.x, to.x)
    val (fy, ty) = fold(from.y, to.y)
    val (fz, tz) = fold(from.z, to.z)
    return ElementBaker.autoUv(face, Vec3(fx, fy, fz), Vec3(tx, ty, tz))
}

data class Bounds(val min: Vec3, val max: Vec3) {
    val size: Vec3 get() = max - min

    companion object {
        fun of(elements: List<OutElement>): Bounds? {
            if (elements.isEmpty()) return null
            var mn = Vec3(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
            var mx = Vec3(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE)
            for (e in elements) for (v in listOf(e.from, e.to)) {
                mn = Vec3(min(mn.x, v.x), min(mn.y, v.y), min(mn.z, v.z))
                mx = Vec3(max(mx.x, v.x), max(mx.y, v.y), max(mx.z, v.z))
            }
            return Bounds(mn, mx)
        }
    }
}

/** Largest per-axis difference (px) between two bounding boxes, comparing both min and max corners. */
fun boundsDifference(a: Bounds, b: Bounds): Double = listOf(
    abs(a.min.x - b.min.x), abs(a.min.y - b.min.y), abs(a.min.z - b.min.z),
    abs(a.max.x - b.max.x), abs(a.max.y - b.max.y), abs(a.max.z - b.max.z),
).max()

/** Shrinks around the model centre when anything is outside −16..32 px; returns the display scale to undo it. */
fun fitToModelBounds(elements: List<OutElement>): Pair<List<OutElement>, Double> {
    var extent = 0.0
    for (el in elements) for (v in listOfNotNull(el.from, el.to, el.rotation?.origin)) {
        extent = max(extent, max(abs(v.x - 8), max(abs(v.y - 8), abs(v.z - 8))))
    }
    if (extent <= 24.0) return elements to 1.0
    val k = 24.0 / extent
    val c = Vec3(8.0, 8.0, 8.0)
    fun s(v: Vec3) = (v - c) * k + c
    return elements.map { e -> e.copy(from = s(e.from), to = s(e.to), rotation = e.rotation?.let { it.copy(origin = s(it.origin)) }) } to 1.0 / k
}

object DesignApplier {
    /** Replaces the converted geometry of [res] with [design]; keeps the conversion as reference. */
    fun apply(res: StyleResult, design: Design): StyleResult {
        val a = design.anchor
        // design px of a canonical cell c: x = 8 − 16·c.x, z = 8 − 16·c.z → moving the origin to a shifts by +16·a.
        val shift = Vec3(16.0 * a.x, -16.0 * a.y, 16.0 * a.z)
        fun mv(e: OutElement) = e.copy(from = e.from + shift, to = e.to + shift, rotation = e.rotation?.let { it.copy(origin = it.origin + shift) })
        val raw = design.elements.map { e ->
            mv(OutElement(e.from, e.to, e.rotation, e.faces.mapValues { (_, f) -> f.copy(texture = f.texture) }, e.shade))
        }
        val o = Facing.canonicalToOraxenBlock(a)   // the same anchor in Oraxen offsets
        val seats = (design.seats ?: res.seats).map { shiftOffset(it, o) }
        val lights = (design.lights ?: res.lights).map { shiftOffset(it, o) }
        val barriers = (design.barriers ?: res.barriers).map { IVec3(it.x - o.x, it.y - o.y, it.z - o.z) }
        val (elements, scale) = fitToModelBounds(raw)
        val warnings = res.warnings.filterNot { it.startsWith("model is larger") }.toMutableList()
        if (scale != 1.0) warnings += "design is larger than 3×3×3 blocks; shrunk ×${fmt(1.0 / scale)} and scaled back by the display"
        // Reference geometry in true size (undo the conversion's own shrink) for the comparison.
        val reference = (if (res.displayScale == 1.0) res.elements else res.elements.map { e ->
            val c = Vec3(8.0, 8.0, 8.0); val k = res.displayScale
            fun s(v: Vec3) = (v - c) * k + c
            e.copy(from = s(e.from), to = s(e.to), rotation = e.rotation?.let { it.copy(origin = s(it.origin)) })
        }).map(::mv)
        val (decided, open) = res.unmatchedFunctions.partition { f -> design.handled.keys.any { f.contains(it, ignoreCase = true) } }
        val decidedNotes = design.handled.entries
            .filter { (marker, _) -> decided.any { it.contains(marker, ignoreCase = true) } }
            .map { (marker, how) -> PartNote(marker, "已決定：$how") }
        return res.copy(
            model = design.displayName?.let { res.model.copy(displayName = it) } ?: res.model,
            converted = res.converted + decidedNotes,
            unmatchedFunctions = open,
            elements = elements,
            textures = design.textures.mapKeys { it.key },
            barriers = barriers,
            seats = seats,
            lights = lights,
            hitbox = if (barriers.isEmpty()) avoidKeepBlocks(
                encloseSeats(design.hitbox ?: hitboxFor(raw), seats),
                res.keepBlocks.map { k -> Facing.canonicalToOraxenBlock(IVec3(k.cell.x - a.x, k.cell.y - a.y, k.cell.z - a.z)) },
            ) else null,
            anchor = a,
            displayScale = scale,
            warnings = warnings,
            designed = true,
            storage = design.storage,
            placing = design.placing ?: res.placing,
            referenceElements = reference,
            referenceTextures = res.textures,
        )
    }
}

/** Moves an Oraxen "x,y,z rest" offset string by −[o]. */
internal fun shiftOffset(s: String, o: IVec3): String {
    if (o == IVec3(0, 0, 0)) return s
    val parts = s.trim().split(Regex("\\s+"), limit = 2)
    val (x, y, z) = parts[0].split(',').map { it.toDouble() }
    val pos = "${fmt(x - o.x)},${fmt(y - o.y)},${fmt(z - o.z)}"
    return if (parts.size > 1) "$pos ${parts[1]}" else pos
}

/**
 * Furniture made for the server with no DiceFurniture original: only the design (which must set
 * displayName) and its Oraxen id. Nothing is migrated to it, so it is not in mapping.yml.
 */
object NewFurniture {
    fun result(oraxenId: String, design: Design): StyleResult {
        val name = requireNotNull(design.displayName) { "${design.style}: new furniture needs displayName(...)" }
        require(design.anchor == IVec3(0, 0, 0)) { "${design.style}: anchors only make sense for migrated furniture" }
        val base = StyleResult(
            model = DModel(design.style, design.style, design.style, name, "TOP", emptyList(), emptyList(), false),
            oraxenId = oraxenId, elements = emptyList(), textures = emptyMap(), barriers = emptyList(),
            seats = emptyList(), lights = emptyList(), hitbox = null, displayScale = 1.0, converted = emptyList(),
            skipped = emptyList(), angleErrors = emptyList(), unmatchedFunctions = emptyList(), keepBlocks = emptyList(),
            warnings = emptyList(),
        )
        return DesignApplier.apply(base, design)
    }
}
