package world.lunlun.furnmigrate.migrator

import io.th0rgal.oraxen.api.OraxenFurniture
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.BlockLocation
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic
import io.th0rgal.oraxen.mechanics.provided.gameplay.storage.StorageMechanic
import io.th0rgal.oraxen.shaded.jeff_media.morepersistentdatatypes.DataType
import io.th0rgal.oraxen.utils.BlockHelpers
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.inventory.ItemStack
import world.lunlun.furnmigrate.common.IVec3

/** Everything the migrator does with Oraxen 1.220.1 (docs/findings.md §5). */
object Ox {
    fun isLoaded(id: String): Boolean = OraxenFurniture.isFurniture(id) && OraxenFurniture.getFurnitureMechanic(id) != null

    fun blockLoc(world: World, cell: IVec3) = Location(world, cell.x.toDouble(), cell.y.toDouble(), cell.z.toDouble())

    /** Barrier cells the furniture will need, exactly as FurnitureMechanic#notEnoughSpace computes them. */
    fun barrierCells(id: String, world: World, at: IVec3, yaw: Float): List<IVec3> {
        val mech = OraxenFurniture.getFurnitureMechanic(id) ?: return emptyList()
        if (!mech.hasBarriers()) return emptyList()
        return mech.getLocations(yaw, blockLoc(world, at), mech.barriers).map { IVec3(it.blockX, it.blockY, it.blockZ) }
    }

    /** Same test Oraxen uses for free space. */
    fun isReplaceable(world: World, cell: IVec3): Boolean = BlockHelpers.isReplaceable(world.getBlockAt(cell.x, cell.y, cell.z))

    /** Places the furniture on the floor; null when Oraxen found no room (or the id is unknown). */
    fun place(id: String, world: World, at: IVec3, yaw: Float): Entity? = OraxenFurniture.place(id, blockLoc(world, at), yaw, BlockFace.UP)

    fun storageSlots(id: String): Int {
        val mech = OraxenFurniture.getFurnitureMechanic(id) ?: return 0
        return if (mech.isStorage) mech.storage.rows * 9 else 0
    }

    /**
     * Puts [items] into the new furniture's storage (base entity PDC `oraxen:storage`, the same key and
     * type StorageMechanic reads). Returns what did not fit or could not be verified; the caller drops it.
     */
    fun putIntoStorage(id: String, base: Entity, items: List<ItemStack>): List<ItemStack> {
        val slots = storageSlots(id)
        if (slots == 0 || items.isEmpty()) return items
        val put = items.take(slots)
        val array = arrayOfNulls<ItemStack>(slots).also { a -> put.forEachIndexed { i, it -> a[i] = it } }
        @Suppress("UNCHECKED_CAST")
        base.persistentDataContainer.set(StorageMechanic.STORAGE_KEY, DataType.ITEM_STACK_ARRAY, array as Array<ItemStack>)
        val back = base.persistentDataContainer.get(StorageMechanic.STORAGE_KEY, DataType.ITEM_STACK_ARRAY)
        val ok = back != null && back.count { it != null && !it.type.isAir } == put.size
        return if (ok) items.drop(slots) else items.also { base.persistentDataContainer.remove(StorageMechanic.STORAGE_KEY) }
    }

    /** Is the furniture [id] already standing on block [at]? */
    fun isPlacedAt(id: String, world: World, at: IVec3): Boolean = baseAt(id, world, at) != null

    /** The furniture [id]'s base entity standing on block [at], if any. */
    fun baseAt(id: String, world: World, at: IVec3): Entity? =
        world.getNearbyEntities(Location(world, at.x + 0.5, at.y + 0.5, at.z + 0.5), 0.6, 1.0, 0.6)
            .firstOrNull { it is org.bukkit.entity.ItemDisplay && OraxenFurniture.isBaseEntity(it) && OraxenFurniture.getFurnitureMechanic(it)?.itemID == id }

    /**
     * Puts back barrier cells of an already placed furniture that something else overwrote (DiceFurniture's
     * FlowerPot sets its own block to a flower pot whenever FurnitureLib loads the old object). Same block
     * and PDC entries as FurnitureMechanic#setBarrierHitbox (FurnitureMechanic.java:1014). Returns the cells fixed.
     */
    fun repairBarriers(id: String, base: Entity, world: World, at: IVec3, yaw: Float): List<IVec3> {
        val fixed = mutableListOf<IVec3>()
        for (c in barrierCells(id, world, at, yaw)) {
            val block = world.getBlockAt(c.x, c.y, c.z)
            if (block.type == org.bukkit.Material.BARRIER) continue
            block.setType(org.bukkit.Material.BARRIER, false)
            val pdc = BlockHelpers.getPDC(block)
            pdc.set(FurnitureMechanic.FURNITURE_KEY, org.bukkit.persistence.PersistentDataType.STRING, id)
            pdc.set(FurnitureMechanic.ROOT_KEY, org.bukkit.persistence.PersistentDataType.STRING, BlockLocation(base.location.clone()).toString())
            pdc.set(FurnitureMechanic.ORIENTATION_KEY, org.bukkit.persistence.PersistentDataType.FLOAT, yaw)
            pdc.set(FurnitureMechanic.BASE_ENTITY_KEY, DataType.UUID, base.uniqueId)
            fixed += c
        }
        return fixed
    }

    /** Is this block an Oraxen furniture barrier? */
    fun isFurnitureBlock(world: World, cell: IVec3): Boolean = OraxenFurniture.isFurniture(world.getBlockAt(cell.x, cell.y, cell.z))
}
