package world.lunlun.furnmigrate.common

import kotlin.math.roundToInt

/** `OAK_FENCE`, `oak_fence`, `minecraft:oak_fence[...]` → `minecraft:oak_fence`. */
fun normalizeId(id: String): String {
    val base = id.substringBefore('[').trim().lowercase()
    return if (':' in base) base else "minecraft:$base"
}

/** An item found on a placed part. [payload] is whatever the caller needs back (the ItemStack). */
data class HeldItem<T>(val slot: String, val material: String, val partName: String, val payload: T)

object PlayerItems {
    /**
     * Name prefixes of the DiceFurniture parts that hold player items (Table/SchoolTable/BlackBoard
     * `#ITEM#`, WeaponStand `#SLOT1..3#`, TrashCan `#TRASH#`, LargeTable `#TELLER1..4#`, Crossbow `#ARROW#`).
     * These parts are empty in every model template.
     *
     * Every other item on a placed piece belongs to the model, even when it differs from the template:
     * the database shows state and material variants (Streetlamp switched on: redstone_torch → glowstone,
     * FlowerPot sticks, Trunk logs → redstone_block, Fence posts with stone) that DiceFurniture swaps
     * in by itself. Dropping those would hand out free items.
     */
    val HOLDER_MARKERS = listOf("#ITEM", "#SLOT", "#TRASH", "#TELLER", "#ARROW")

    fun isHolder(partName: String): Boolean = HOLDER_MARKERS.any { partName.trim().uppercase().startsWith(it) }

    fun <T> of(held: List<HeldItem<T>>): List<HeldItem<T>> = held.filter { isHolder(it.partName) }
}

/** What the migrator does with one placed piece. */
sealed class Plan {
    abstract val keepCells: List<IVec3>

    /** No mapping: leave the old piece alone and log it. */
    data class Skip(val reason: String) : Plan() {
        override val keepCells: List<IVec3> get() = emptyList()
    }

    /**
     * Remove the old piece (except [keepCells], world block positions that stay real blocks), then
     * place [oraxenId] at block [placeAt] with Oraxen yaw [oraxenYaw].
     */
    data class Replace(
        val oraxenId: String,
        val placeAt: IVec3,
        val oraxenYaw: Float,
        override val keepCells: List<IVec3>,
        val storage: Boolean,
    ) : Plan()

    /** Remove the old piece; keep [keepCells]; put [placeBlock] at its position when it is not kept. */
    data class Retire(override val keepCells: List<IVec3>, val placeBlock: Pair<IVec3, String>?) : Plan()
}

/** The parts of a placed FurnitureLib object the planner needs. */
data class PlacedPiece(
    val style: String,
    /** Block position of the start location. */
    val origin: IVec3,
    val flYaw: Double,
    /** World block position → block id of every cell in the object's blockList. */
    val blocks: Map<IVec3, String>,
)

object MigrationPlanner {
    /** World block of canonical [cell] for a piece at [origin] placed with FurnitureLib yaw [flYaw]. */
    fun worldCell(origin: IVec3, cell: IVec3, flYaw: Double): IVec3 {
        val o = Facing.flWorldOffset(Vec3(cell.x.toDouble(), cell.y.toDouble(), cell.z.toDouble()), flYaw)
        return IVec3(origin.x + o.x.roundToInt(), origin.y + o.y.roundToInt(), origin.z + o.z.roundToInt())
    }

    fun plan(entry: MappingEntry?, piece: PlacedPiece): Plan {
        if (entry == null) return Plan.Skip("style ${piece.style} has no mapping")
        return when (entry.action) {
            MappingAction.REPLACE -> {
                // Only cells that really are in the object's blockList (and hold the expected block) stay.
                val keep = entry.keepBlocks
                    .map { worldCell(piece.origin, it.cell, piece.flYaw) to normalizeId(it.blockId) }
                    .filter { (cell, id) -> piece.blocks[cell]?.let(::normalizeId) == id }
                    .map { it.first }
                Plan.Replace(
                    oraxenId = entry.oraxenId,
                    placeAt = worldCell(piece.origin, entry.anchor, piece.flYaw),
                    oraxenYaw = Facing.oraxenYaw(piece.flYaw, entry.yawOffset),
                    keepCells = keep,
                    storage = entry.storage,
                )
            }
            MappingAction.RETIRE -> {
                val block = entry.retireBlock?.let(::normalizeId)
                val atOrigin = piece.blocks[piece.origin]?.let(::normalizeId)
                when {
                    block == null -> Plan.Retire(emptyList(), null)
                    atOrigin == block -> Plan.Retire(listOf(piece.origin), null)
                    else -> Plan.Retire(emptyList(), piece.origin to block)
                }
            }
        }
    }

    /**
     * Predicts whether Oraxen will find room for the new piece once the old one is gone: every new
     * barrier cell must be replaceable now, or be an old blockList cell that is not kept.
     */
    fun blockedCells(newBarriers: List<IVec3>, piece: PlacedPiece, keepCells: List<IVec3>, isReplaceable: (IVec3) -> Boolean): List<IVec3> {
        val cleared = piece.blocks.keys - keepCells.toSet()
        return newBarriers.filter { it !in cleared && !isReplaceable(it) }
    }
}

/** "world:cx:cz"; sorted by world, then x, then z. */
data class ChunkKey(val world: String, val x: Int, val z: Int) : Comparable<ChunkKey> {
    override fun toString() = "$world:$x:$z"
    override fun compareTo(other: ChunkKey) = compareValuesBy(this, other, { it.world }, { it.x }, { it.z })

    companion object {
        /** Splits from the right, so a world name containing ':' still works. */
        fun parse(s: String): ChunkKey {
            val iz = s.lastIndexOf(':')
            val ix = if (iz > 0) s.lastIndexOf(':', iz - 1) else -1
            require(ix > 0) { "bad chunk key '$s'" }
            return ChunkKey(s.substring(0, ix), s.substring(ix + 1, iz).toInt(), s.substring(iz + 1).toInt())
        }
    }
}

/** Second `/furnmigrate run` within [windowMillis] of the first confirms. */
class ConfirmGate(private val windowMillis: Long = 30_000) {
    private val pending = mutableMapOf<String, Long>()

    /** Returns true when [who] confirmed (asked before within the window); otherwise arms the gate. */
    fun confirm(who: String, now: Long): Boolean {
        val at = pending[who]
        if (at != null && now - at <= windowMillis) {
            pending.remove(who)
            return true
        }
        pending[who] = now
        return false
    }
}

/** Journal line types. */
object JournalType {
    const val BEGIN = "BEGIN"
    const val DONE = "DONE"
    const val NEEDS_MANUAL = "NEEDS_MANUAL"
    const val SKIPPED = "SKIPPED"
    const val ABORTED = "ABORTED"
    const val ITEMS_DROPPED = "ITEMS_DROPPED"
    /** Re-run of a piece whose new furniture is already in the saved world: only its database row is removed. */
    const val CLEANUP = "CLEANUP"
    /** Per chunk, after World#save(true): the listed objects are in the saved world; their rows get deleted next. */
    const val COMMIT = "COMMIT"

    val TERMINAL = setOf(DONE, NEEDS_MANUAL, ABORTED, CLEANUP)
}

object JournalRecovery {
    /** Object ids that have a BEGIN line but no terminal line after it (the server stopped mid-object). */
    fun interrupted(lines: List<Pair<String, String>>): List<String> {
        val open = linkedSetOf<String>()
        for ((type, id) in lines) {
            when (type) {
                JournalType.BEGIN -> open += id
                in JournalType.TERMINAL -> open -= id
            }
        }
        return open.toList()
    }
}

/** An item stored in a FurnitureLib database record: holder part, equipment slot, raw item compound. */
data class RecordItem(val part: String, val slot: String, val item: Map<String, Any>) {
    val material: String get() = normalizeId(item["id"] as? String ?: "air")
}

/**
 * Items FurnitureLib cannot load. FurnitureLib reads the item NBT of its records without upgrading it,
 * so an item written by an older Minecraft version can come back empty on 1.21.8 (seen on a real server: an
 * iron hoe with 1.21.4-style `enchantments: {levels: {...}}`). Such an item is invisible in game but
 * still in the database; the migrator reads it from the record and lets Paper's DataFixer upgrade it.
 */
object RecordItems {
    /** Minecraft data versions used to upgrade old item NBT. */
    const val DATA_VERSION_1_20_4 = 3700
    const val DATA_VERSION_1_21_4 = 4189

    /** Holder-part items of a record (`Data` column, decoded); part names read like FurnitureLib: CustomName, else Name. */
    @Suppress("UNCHECKED_CAST")
    fun holders(record: Map<String, Any>): List<RecordItem> {
        val entities = record["entities"] as? Map<String, Any> ?: return emptyList()
        return entities.values.mapNotNull { it as? Map<String, Any> }.flatMap { e ->
            val name = (e["CustomName"] ?: e["Name"])?.toString() ?: ""
            if (!PlayerItems.isHolder(name)) return@flatMap emptyList()
            val inv = e["Inventory"] as? Map<String, Any> ?: return@flatMap emptyList()
            inv.mapNotNull { (slot, v) ->
                val item = v as? Map<String, Any> ?: return@mapNotNull null
                RecordItem(name, slot.uppercase(), item).takeIf { it.material != "minecraft:air" }
            }
        }
    }

    /** Record items whose part + slot holds nothing in FurnitureLib's memory, i.e. it failed to load them. */
    fun <T> notLoaded(record: List<RecordItem>, loaded: List<HeldItem<T>>): List<RecordItem> {
        val have = loaded.map { it.partName.trim().uppercase() to it.slot.uppercase() }.toSet()
        return record.filter { (it.part.trim().uppercase() to it.slot) !in have }
    }

    /**
     * The data version the item was most likely written with: pre-1.20.5 layout (`Count`/`tag`), 1.21.4
     * component details changed in 1.21.5 (`levels`, `show_in_tooltip`), otherwise null = current.
     */
    @Suppress("UNCHECKED_CAST")
    fun guessDataVersion(item: Map<String, Any>): Int? {
        if ("tag" in item || "Count" in item) return DATA_VERSION_1_20_4
        fun has(o: Any?, keys: Set<String>): Boolean = when (o) {
            is Map<*, *> -> o.keys.any { it in keys } || o.values.any { has(it, keys) }
            is List<*> -> o.any { has(it, keys) }
            else -> false
        }
        return if (has(item["components"], setOf("levels", "show_in_tooltip"))) DATA_VERSION_1_21_4 else null
    }
}

/**
 * DiceFurniture's FlowerPot is a real flower pot block; players plant things in it (`potted_*`). Removing
 * the old piece turns that block into air, so the plant is handed back like any other player item.
 */
object PottedPlants {
    private val special = mapOf(
        "minecraft:potted_azalea_bush" to "minecraft:azalea",
        "minecraft:potted_flowering_azalea_bush" to "minecraft:flowering_azalea",
    )

    /** Item planted in a potted block (`minecraft:potted_blue_orchid` → `minecraft:blue_orchid`); null if none. */
    fun content(blockId: String): String? {
        val id = normalizeId(blockId)
        special[id]?.let { return it }
        val name = id.substringAfter(':')
        return if (name.startsWith("potted_")) "minecraft:" + name.removePrefix("potted_") else null
    }
}
