package world.lunlun.furnmigrate.common

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.Reader
import java.io.Writer

/** What the migrator does with placed pieces of a style. */
enum class MappingAction {
    /** Replace with the Oraxen furniture [MappingEntry.oraxenId]. */
    REPLACE,
    /** No Oraxen furniture: drop held items, remove the old piece, leave/put [MappingEntry.retireBlock]. */
    RETIRE,
}

/** How well a style was converted. */
enum class ConversionStatus {
    /** Every visible part and function converted. */
    OK,
    /** Converted, but something was skipped or approximated (see report.md). */
    PARTIAL,
}

/**
 * One FurnitureLib style (dModel header / ObjectID project name) → one Oraxen furniture.
 *
 * @property keepBlocks real functional blocks of the old piece (crafting table, bed, chest …) that stay
 *   real blocks: the migrator saves them (state + contents) before FurnitureLib turns them into AIR and
 *   puts them back afterwards. Canonical cell + block id, relative to the old piece's origin.
 * @property anchor canonical cell of the old piece where the Oraxen furniture's own block goes, so
 *   that a large piece (tent) is placed and broken at its middle. The migrator places the furniture at
 *   old origin + FurnitureLib-rotated [anchor]; (0,0,0) = the old origin.
 */
data class MappingEntry(
    val style: String,
    /** Empty for [MappingAction.RETIRE]. */
    val oraxenId: String,
    val action: MappingAction = MappingAction.REPLACE,
    /** Block left at the furniture's own block when retired, e.g. `minecraft:cauldron`. */
    val retireBlock: String? = null,
    val yawOffset: Double = Facing.DEFAULT_YAW_OFFSET,
    val storage: Boolean = false,
    val status: ConversionStatus = ConversionStatus.OK,
    val keepBlocks: List<KeepBlock> = emptyList(),
    val anchor: IVec3 = IVec3(0, 0, 0),
    val notes: List<String> = emptyList(),
)

internal fun parseCell(s: String, style: String, what: String): IVec3 {
    val xyz = s.split(',').mapNotNull { it.trim().toIntOrNull() }
    require(xyz.size == 3) { "mapping.yml: style '$style' $what '$s' must be 'x,y,z'" }
    return IVec3(xyz[0], xyz[1], xyz[2])
}

/** A real block of the old piece that stays a real block ("x,y,z minecraft:id" in mapping.yml). */
data class KeepBlock(val cell: IVec3, val blockId: String) {
    companion object {
        fun parse(s: String, style: String): KeepBlock {
            val parts = s.trim().split(Regex("\\s+"))
            val xyz = parts.getOrNull(0)?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
            require(parts.size == 2 && xyz?.size == 3) { "mapping.yml: style '$style' keep_blocks entry '$s' must be 'x,y,z minecraft:id'" }
            return KeepBlock(IVec3(xyz[0], xyz[1], xyz[2]), parts[1])
        }
    }
}

data class Mapping(
    val namespace: String,
    val entries: Map<String, MappingEntry>,
) {
    /** Lookup by FurnitureLib project name. Exact match first, then case-insensitive. */
    fun find(style: String): MappingEntry? =
        entries[style] ?: entries.values.firstOrNull { it.style.equals(style, ignoreCase = true) }

    /** Styles from [placed] that have no mapping, sorted. */
    fun missing(placed: Collection<String>): List<String> = placed.filter { find(it) == null }.distinct().sorted()

    companion object {
        const val FORMAT = 1
    }
}

object MappingIO {
    private fun yaml(): Yaml {
        val dump = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            indent = 2
            isAllowUnicode = true
            width = 200
        }
        return Yaml(SafeConstructor(LoaderOptions()), org.yaml.snakeyaml.representer.Representer(dump), dump)
    }

    fun write(mapping: Mapping, out: Writer) {
        val styles = linkedMapOf<String, Any>()
        for (e in mapping.entries.values.sortedBy { it.style.lowercase() }) {
            val m = linkedMapOf<String, Any>()
            m["action"] = e.action.name
            if (e.action == MappingAction.RETIRE) {
                e.retireBlock?.let { m["retire_block"] = it }
                if (e.notes.isNotEmpty()) m["notes"] = e.notes
                styles[e.style] = m
                continue
            }
            m.putAll(linkedMapOf<String, Any>(
                "oraxen_id" to e.oraxenId,
                "yaw_offset" to e.yawOffset,
                "storage" to e.storage,
                "status" to e.status.name,
            ))
            if (e.anchor != IVec3(0, 0, 0)) m["anchor"] = "${e.anchor.x},${e.anchor.y},${e.anchor.z}"
            if (e.keepBlocks.isNotEmpty()) m["keep_blocks"] = e.keepBlocks.map { "${it.cell.x},${it.cell.y},${it.cell.z} ${it.blockId}" }
            if (e.notes.isNotEmpty()) m["notes"] = e.notes
            styles[e.style] = m
        }
        val root = linkedMapOf<String, Any>(
            "format" to Mapping.FORMAT,
            "namespace" to mapping.namespace,
            "styles" to styles,
        )
        out.write("# FurnMigrate mapping table: FurnitureLib style -> Oraxen furniture ID.\n")
        out.write("# Generated by the converter; read by the migrator. yaw_offset: Oraxen yaw = FurnitureLib yaw + yaw_offset.\n")
        yaml().dump(root, out)
    }

    @Suppress("UNCHECKED_CAST")
    fun read(input: Reader): Mapping {
        val root = yaml().load<Any?>(input) as? Map<String, Any?> ?: error("mapping.yml is empty or not a map")
        val format = (root["format"] as? Number)?.toInt() ?: error("mapping.yml: missing 'format'")
        require(format == Mapping.FORMAT) { "mapping.yml: unsupported format $format (expected ${Mapping.FORMAT})" }
        val namespace = root["namespace"] as? String ?: error("mapping.yml: missing 'namespace'")
        val styles = root["styles"] as? Map<String, Any?> ?: emptyMap()
        val entries = linkedMapOf<String, MappingEntry>()
        for ((style, raw) in styles) {
            val m = raw as? Map<String, Any?> ?: error("mapping.yml: style '$style' is not a map")
            val action = (m["action"] as? String)?.let { MappingAction.valueOf(it) } ?: MappingAction.REPLACE
            val id = if (action == MappingAction.RETIRE) (m["oraxen_id"] as? String).orEmpty()
            else m["oraxen_id"] as? String ?: error("mapping.yml: style '$style' has no oraxen_id")
            entries[style] = MappingEntry(
                style = style,
                oraxenId = id,
                action = action,
                retireBlock = m["retire_block"] as? String,
                yawOffset = (m["yaw_offset"] as? Number)?.toDouble() ?: Facing.DEFAULT_YAW_OFFSET,
                storage = m["storage"] as? Boolean ?: false,
                status = (m["status"] as? String)?.let { ConversionStatus.valueOf(it) } ?: ConversionStatus.OK,
                keepBlocks = (m["keep_blocks"] as? List<String>).orEmpty().map { KeepBlock.parse(it, style) },
                anchor = (m["anchor"] as? String)?.let { parseCell(it, style, "anchor") } ?: IVec3(0, 0, 0),
                notes = (m["notes"] as? List<String>).orEmpty(),
            )
        }
        return Mapping(namespace, entries)
    }
}

/** Oraxen item ID rule from CLAUDE.md: `dice_<original model name lowercased>`. */
fun oraxenIdFor(style: String): String = "dice_" + style.lowercase().replace(Regex("[^a-z0-9_]"), "_")
