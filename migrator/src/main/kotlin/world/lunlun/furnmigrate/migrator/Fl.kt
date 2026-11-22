package world.lunlun.furnmigrate.migrator

import de.Ste3et_C0st.FurnitureLib.Database.Database
import de.Ste3et_C0st.FurnitureLib.main.FurnitureLib
import de.Ste3et_C0st.FurnitureLib.main.ObjectID
import de.Ste3et_C0st.FurnitureLib.main.Type
import de.Ste3et_C0st.FurnitureLib.main.entity.fContainerEntity
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.inventory.ItemStack
import world.lunlun.furnmigrate.common.ChunkKey
import world.lunlun.furnmigrate.common.HeldItem
import world.lunlun.furnmigrate.common.IVec3
import world.lunlun.furnmigrate.common.Nbt
import world.lunlun.furnmigrate.common.PlacedPiece

/**
 * Everything the migrator does with FurnitureLib 3.2.9.2 (signatures checked with javap against
 * libs/FurnitureLib-3.2.9.2.jar, see docs/findings.md §3 and §12).
 */
object Fl {
    private val lib: FurnitureLib get() = FurnitureLib.getInstance()
    private val manager get() = lib.furnitureManager

    /** Every object still alive (storage-methode LEGACY keeps all of them in memory). */
    fun liveObjects(): List<ObjectID> = manager.objectList.filter { it.sqlAction != Type.SQLAction.REMOVE }

    fun chunkOf(obj: ObjectID): ChunkKey {
        val l = obj.startLocation
        return ChunkKey(worldName(obj), l.blockX shr 4, l.blockZ shr 4)
    }

    /** The world column may hold a UUID; the loaded World's name is what Bukkit.getWorld takes. */
    fun worldName(obj: ObjectID): String = obj.world?.name ?: obj.worldName

    fun objectsIn(key: ChunkKey): List<ObjectID> = liveObjects().filter { chunkOf(it) == key }

    fun find(id: String): ObjectID? = liveObjects().firstOrNull { it.id == id }

    fun origin(obj: ObjectID): IVec3 = obj.startLocation.let { IVec3(it.blockX, it.blockY, it.blockZ) }

    /** Reads the real blocks of the blockList, so the chunk must be loaded. */
    fun piece(obj: ObjectID, world: World): PlacedPiece = PlacedPiece(
        style = obj.project,
        origin = origin(obj),
        flYaw = obj.startLocation.yaw.toDouble(),
        blocks = obj.blockList.associate { l ->
            IVec3(l.blockX, l.blockY, l.blockZ) to world.getBlockAt(l.blockX, l.blockY, l.blockZ).type.key.toString()
        },
    )

    /** Items on the parts of the piece (packet entities), with the part's name (`#SLOT1#` …). */
    fun heldItems(obj: ObjectID): List<HeldItem<ItemStack>> = obj.packetList.filterIsInstance<fContainerEntity>().flatMap { e ->
        val name = e.name ?: ""
        (e.inventory?.stackMap ?: emptyMap()).mapNotNull { (slot, item) ->
            if (item == null || item.type.isAir) null else HeldItem(slot.name, item.type.key.toString(), name, item.clone())
        }
    }

    /**
     * Takes [cells] out of the object's blockList and out of FurnitureLib's global block registry, so
     * that `remove` leaves them alone: a kept crafting table / bed / chest stays exactly as it is,
     * contents included (BlockManager#destroy only touches the cells in the list).
     */
    fun releaseCells(obj: ObjectID, cells: Collection<IVec3>) {
        if (cells.isEmpty()) return
        val set = cells.toSet()
        val w = obj.world
        val match = { l: Location -> l.world == w && IVec3(l.blockX, l.blockY, l.blockZ) in set }
        obj.blockList.removeIf(match)
        lib.blockManager.list.removeIf(match)
    }

    /**
     * Removes the piece from the world and from FurnitureLib's memory: blockList cells → AIR and packets
     * destroyed (FurnitureManager#remove), then forgotten (deleteObjectID) so FurnitureLib's own autosave
     * never touches its database row. The row is deleted later with [deleteRecord], only after the world
     * has been saved (see Engine#commit): if the server dies before that, world and database both still
     * hold the old piece.
     */
    fun removeFromWorld(obj: ObjectID) {
        manager.remove(obj)
        manager.deleteObjectID(obj)
    }

    /** DELETE of the database row right away (SQLManager#remove → Database#delete). */
    fun deleteRecord(obj: ObjectID) {
        lib.sqlManager.remove(obj)
    }

    /** The object's database record (`Data`, decoded), straight from FurnitureLib's own connection. */
    fun record(obj: ObjectID): Map<String, Any>? = runCatching {
        lib.sqlManager.database.connection.use { c ->
            c.prepareStatement("SELECT Data FROM ${Database.TABLE_NAME} WHERE ObjID = ?").use { st ->
                st.setString(1, obj.id)
                st.executeQuery().use { rs -> if (rs.next()) Nbt.readBase64Gzip(rs.getString(1)) else null }
            }
        }
    }.getOrNull()

    /** Every cell of the object's blockList. */
    fun cells(obj: ObjectID): List<IVec3> = obj.blockList.map { IVec3(it.blockX, it.blockY, it.blockZ) }

    /** Every block FurnitureLib still counts as a furniture block (barrier, torch …) in [world]. */
    fun furnitureBlocks(world: World): Set<IVec3> =
        lib.blockManager.list.filter { it.world == world }.mapTo(hashSetOf()) { IVec3(it.blockX, it.blockY, it.blockZ) }
}
