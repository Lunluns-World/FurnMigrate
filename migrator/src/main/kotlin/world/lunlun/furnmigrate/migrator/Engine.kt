package world.lunlun.furnmigrate.migrator

import de.Ste3et_C0st.FurnitureLib.main.ObjectID
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.ChunkSnapshot
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Container
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import world.lunlun.furnmigrate.common.ChunkKey
import world.lunlun.furnmigrate.common.IVec3
import world.lunlun.furnmigrate.common.PlayerItems
import world.lunlun.furnmigrate.common.RecordItems
import world.lunlun.furnmigrate.common.PottedPlants
import world.lunlun.furnmigrate.common.HeldItem
import world.lunlun.furnmigrate.common.JournalType
import world.lunlun.furnmigrate.common.Mapping
import world.lunlun.furnmigrate.common.MigrationPlanner
import world.lunlun.furnmigrate.common.Plan
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * Works through [queue] one chunk at a time: loads the chunk and its neighbours with getChunkAtAsync,
 * holds plugin tickets while working, replaces at most `objects-per-tick` pieces per tick, then (optionally)
 * lists orphan barriers and checkpoints the chunk. With [dry] nothing in the world is changed.
 */
class Engine(
    private val plugin: FurnMigratePlugin,
    private val mapping: Mapping,
    val dry: Boolean,
    queue: List<ChunkKey>,
    val state: RunState,
    private val journal: Journal?,
    private val reportFile: File,
    private val orphanFile: File?,
) {
    private enum class Phase { IDLE, LOADING, PROCESSING, SCANNING, DONE }

    private val queue = ArrayDeque(queue)
    private var phase = Phase.IDLE
    private var task: BukkitTask? = null
    private var key: ChunkKey? = null
    private var world: World? = null
    private var tickets: List<Chunk> = emptyList()
    private val current = ArrayDeque<ObjectID>()
    private val scanned = hashSetOf<ChunkKey>()
    private var orphanCount = 0
    private var waitingForEntities = false
    /** Removed from the world, database row not deleted yet (see [commit]). */
    private val pending = mutableListOf<ObjectID>()
    /** From this run's journal: objects in a COMMIT line, and objects with a DONE / NEEDS_MANUAL line. */
    private val committedIds = hashSetOf<String>()
    private val finishedIds = hashSetOf<String>()
    var paused = false
    val modeName: String get() = if (dry) "dry-run" else "run"

    fun start() {
        journal?.lines()?.forEach { line ->
            when (line["type"]?.asString) {
                JournalType.COMMIT -> line.getAsJsonArray("objects")?.forEach { committedIds += it.asString }
                JournalType.DONE, JournalType.NEEDS_MANUAL -> finishedIds += line["id"].asString
            }
        }
        reportFile.parentFile.mkdirs()
        report("# FurnMigrate $modeName ${state.runId}")
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 1L, 1L)
    }

    /** Stops after the current object (plugin disable). Tickets are released; the chunk is not marked done. */
    fun stop() {
        task?.cancel(); task = null
        if (!dry) commit("shutdown")
        releaseTickets()
        phase = Phase.DONE
    }

    private fun tick() {
        if (paused) return
        try {
            when (phase) {
                Phase.IDLE -> nextChunk()
                Phase.PROCESSING -> repeat(plugin.objectsPerTick()) {
                    val obj = current.removeFirstOrNull()
                    if (obj == null) { finishChunk(); return }
                    try {
                        process(obj)
                    } catch (t: Throwable) {
                        // Only reachable before the BEGIN line (process handles everything after it):
                        // the old piece is untouched, so it is simply left in place and reported.
                        plugin.logger.severe("FurnMigrate: ${obj.id} skipped: $t")
                        t.printStackTrace()
                        state.skipped++
                        journal?.write(JournalType.SKIPPED, obj.id) { addProperty("style", obj.project); addProperty("reason", "error: $t") }
                        report("ERROR  ${obj.project} ${obj.id} — $t (left in place)")
                    }
                }
                Phase.LOADING -> if (waitingForEntities && tickets.all { it.isEntitiesLoaded }) {
                    // entities must be loaded: a re-run checks for furniture placed before a crash
                    waitingForEntities = false
                    current.clear(); current.addAll(Fl.objectsIn(key!!))
                    phase = Phase.PROCESSING
                }
                Phase.SCANNING, Phase.DONE -> Unit
            }
        } catch (t: Throwable) {
            plugin.logger.severe("FurnMigrate engine error: $t")
            t.printStackTrace()
            paused = true
            state.paused = true
            if (!dry) state.save()
            Msg.broadcast("<red>(；ﾟДﾟ) 發生錯誤，已自動暫停：<white><e></white>", "e" to t.toString())
        }
    }

    private fun nextChunk() {
        val k = queue.removeFirstOrNull() ?: return finish()
        val w = Bukkit.getWorld(k.world)
        if (w == null) {
            report("! world ${k.world} is not loaded; chunk $k skipped")
            return
        }
        phase = Phase.LOADING
        val futures = (-1..1).flatMap { dx -> (-1..1).map { dz -> w.getChunkAtAsync(k.x + dx, k.z + dz) } }
        CompletableFuture.allOf(*futures.toTypedArray()).whenComplete { _, err ->
            if (!plugin.isEnabled) return@whenComplete
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (phase != Phase.LOADING) return@Runnable
                if (err != null) {
                    report("! chunk $k could not be loaded: $err")
                    phase = Phase.IDLE
                    return@Runnable
                }
                tickets = futures.map { it.join() }.onEach { it.addPluginChunkTicket(plugin) }
                key = k; world = w
                waitingForEntities = true
            })
        }
    }

    private fun finishChunk() {
        val k = key ?: return
        val w = world ?: return
        if (!plugin.config.getBoolean("list-orphan-barriers", true) || orphanFile == null) return chunkDone(k)
        phase = Phase.SCANNING
        val todo = tickets.filter { ChunkKey(w.name, it.x, it.z) !in scanned }
        val snaps: List<ChunkSnapshot> = todo.map { it.getChunkSnapshot(false, false, false) }
        todo.forEach { scanned += ChunkKey(w.name, it.x, it.z) }
        val minY = w.minHeight; val maxY = w.maxHeight
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val found = mutableListOf<IVec3>()
            for (s in snaps) for (x in 0..15) for (z in 0..15) for (y in minY until maxY) {
                if (s.getBlockType(x, y, z) == Material.BARRIER) found += IVec3(s.x * 16 + x, y, s.z * 16 + z)
            }
            if (!plugin.isEnabled) return@Runnable
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (phase != Phase.SCANNING) return@Runnable
                val fl = Fl.furnitureBlocks(w)
                val orphans = found.filter { it !in fl && !Ox.isFurnitureBlock(w, it) }
                if (orphans.isNotEmpty()) {
                    orphanFile.parentFile.mkdirs()
                    orphanFile.appendText(orphans.joinToString("") { "${w.name} ${it.x} ${it.y} ${it.z}\n" })
                    orphanCount += orphans.size
                }
                chunkDone(k)
            })
        })
    }

    private fun chunkDone(k: ChunkKey) {
        if (!dry) commit(k.toString())
        releaseTickets()
        state.doneChunks += k
        if (!dry) state.save()
        if (state.doneChunks.size % 10 == 0) Msg.broadcast(Msg.PROGRESS, *counters())
        key = null; world = null
        phase = Phase.IDLE
    }

    /**
     * Makes the chunk's changes durable in the right order: save the world (flush = wait for the region
     * files), note it in the journal, and only then delete the old database rows. A crash before the save
     * leaves old world + old rows (the chunk is simply redone); a crash after it is recognised by the COMMIT
     * line (or by the new furniture standing there) and only the rows are removed.
     */
    private fun commit(label: String) {
        if (pending.isEmpty()) return
        pending.mapNotNull { it.world }.distinct().forEach { it.save(true) }
        val ids = pending.map { it.id }
        journal?.write(JournalType.COMMIT, label) { add("objects", com.google.gson.JsonArray().apply { ids.forEach { add(it) } }) }
        committedIds += ids
        for (obj in pending) Fl.deleteRecord(obj)
        pending.clear()
    }

    private fun releaseTickets() {
        tickets.forEach { runCatching { it.removePluginChunkTicket(plugin) } }
        tickets = emptyList()
    }

    private fun finish() {
        task?.cancel(); task = null
        phase = Phase.DONE
        state.finished = true
        if (!dry) state.save()
        report("# done: ok=${state.ok} retired=${state.retired} manual=${state.manual} skipped=${state.skipped} orphan_barriers=$orphanCount")
        if (dry) Msg.broadcast(Msg.DRY_DONE, *counters(), "file" to plugin.rel(reportFile))
        else Msg.broadcast(Msg.RUN_DONE, *counters())
        if (orphanCount > 0 && orphanFile != null) Msg.broadcast(Msg.ORPHANS, "n" to orphanCount, "file" to plugin.rel(orphanFile))
        plugin.engineFinished(this)
    }

    fun counters(): Array<Pair<String, Any>> = arrayOf(
        "done" to state.doneChunks.size, "total" to state.totalChunks,
        "ok" to state.ok, "retired" to state.retired, "manual" to state.manual, "skipped" to state.skipped,
    )

    private fun report(line: String) = reportFile.appendText(line + "\n")

    // ---------------------------------------------------------------- one piece

    private fun process(obj: ObjectID) {
        val w = world ?: return
        val entry = mapping.find(obj.project)
        val piece = Fl.piece(obj, w)
        val plan = MigrationPlanner.plan(entry, piece)
        val o = piece.origin
        val where = "${w.name} ${o.x},${o.y},${o.z}"
        val id = obj.id

        if (plan is Plan.Skip) {
            state.skipped++
            journal?.write(JournalType.SKIPPED, id) { addProperty("style", obj.project); addProperty("where", where); addProperty("reason", plan.reason) }
            report("SKIP   ${obj.project} @ $where — ${plan.reason}")
            return
        }
        entry!!
        val loaded = PlayerItems.of(Fl.heldItems(obj))
        // Holder items FurnitureLib failed to load (old item format) are read from the database record.
        val missing = Fl.record(obj)?.let { RecordItems.notLoaded(RecordItems.holders(it), loaded) }.orEmpty()
        val rescued = missing.mapNotNull { r -> Rescue.toItem(r)?.let { HeldItem(r.slot, it.type.key.toString(), r.part, it) } }
        if (rescued.size < missing.size) {
            // Never lose an item: leave this piece as it is and report it.
            val bad = missing.filter { r -> rescued.none { it.partName == r.part && it.slot == r.slot } }
            state.manual++
            journal?.write(JournalType.SKIPPED, id) {
                addProperty("style", obj.project); addProperty("where", where)
                addProperty("reason", "item(s) in the database that neither FurnitureLib nor the DataFixer can read; piece left in place")
                add("raw_items", com.google.gson.JsonArray().apply { bad.forEach { add(Rescue.toJson(it.item)) } })
            }
            report("MANUAL ${obj.project} @ $where — ${bad.size} item(s) unreadable (${bad.joinToString { it.material }}); left in place")
            return
        }
        // Plants in real flower pots of the old piece (the pot block becomes air when it is removed).
        val keepSet = plan.keepCells.toSet()
        val plants = piece.blocks.filterKeys { it !in keepSet }.mapNotNull { (cell, block) ->
            PottedPlants.content(block)?.let { org.bukkit.Material.matchMaterial(it) }?.let { m ->
                HeldItem("BLOCK", m.key.toString(), "#POT@${cell.x},${cell.y},${cell.z}", ItemStack(m))
            }
        }
        val playerItems = loaded + rescued + plants
        val items = playerItems.map { it.payload }

        if (dry) return dryCheck(obj, piece, plan, where, items.size, rescued.size)

        // A re-run after a crash: the new furniture is already in the saved world, only the old row is left.
        val alreadyMigrated = id in committedIds ||
            (id in finishedIds && plan is Plan.Replace && Ox.isPlacedAt(plan.oraxenId, w, plan.placeAt))
        if (alreadyMigrated) {
            Fl.releaseCells(obj, Fl.cells(obj))   // its blocks now belong to the new furniture / kept blocks
            Fl.removeFromWorld(obj)
            pending += obj
            // Loading the old object may have run DiceFurniture code that overwrote a new barrier.
            val repaired = (plan as? Plan.Replace)?.let { r ->
                Ox.baseAt(r.oraxenId, w, r.placeAt)?.let { base -> Ox.repairBarriers(r.oraxenId, base, w, r.placeAt, r.oraxenYaw) }
            }.orEmpty()
            journal!!.write(JournalType.CLEANUP, id) {
                addProperty("style", obj.project); addProperty("where", where)
                if (repaired.isNotEmpty()) addProperty("repaired_barriers", repaired.joinToString(" ") { "${it.x},${it.y},${it.z}" })
            }
            report("CLEANUP ${obj.project} @ $where (already migrated before a restart; old row removed${if (repaired.isEmpty()) "" else ", ${repaired.size} barrier(s) put back"})")
            return
        }

        // 1. log
        journal!!.write(JournalType.BEGIN, id) {
            addProperty("style", obj.project)
            addProperty("action", entry.action.name)
            addProperty("world", w.name)
            addProperty("x", o.x); addProperty("y", o.y); addProperty("z", o.z)
            addProperty("fl_yaw", piece.flYaw)
            addProperty("facing", runCatching { obj.blockFace?.name }.getOrNull() ?: "")
            if (plan is Plan.Replace) {
                addProperty("oraxen_id", plan.oraxenId)
                addProperty("place_at", "${plan.placeAt.x},${plan.placeAt.y},${plan.placeAt.z}")
                addProperty("oraxen_yaw", plan.oraxenYaw)
            }
            add("items", Journal.items(playerItems))
            if (rescued.isNotEmpty()) addProperty("rescued_from_db", rescued.size)
            add("old_blocks", com.google.gson.JsonObject().apply {
                for ((c, t) in piece.blocks) addProperty("${c.x},${c.y},${c.z}", t)
            })
            add("keep_blocks", com.google.gson.JsonArray().apply {
                for (c in plan.keepCells) add(com.google.gson.JsonObject().apply {
                    val b = w.getBlockAt(c.x, c.y, c.z)
                    addProperty("at", "${c.x},${c.y},${c.z}")
                    addProperty("block", b.blockData.asString)
                    (b.state as? Container)?.let { add("contents", Journal.itemArray(it.inventory.contents.filterNotNull())) }
                })
            })
        }
        var removed = false
        var dropped = false
        try {
            // 2. remove old (kept real blocks are released first, so FurnitureLib leaves them alone)
            Fl.releaseCells(obj, plan.keepCells)
            Fl.removeFromWorld(obj)
            pending += obj
            removed = true

            // 3. place new
            when (plan) {
                is Plan.Replace -> {
                    val base = Ox.place(plan.oraxenId, w, plan.placeAt, plan.oraxenYaw)
                    if (base == null) {
                        val blocked = Ox.barrierCells(plan.oraxenId, w, plan.placeAt, plan.oraxenYaw).filter { !Ox.isReplaceable(w, it) }
                        drop(w, plan.placeAt, items); dropped = true
                        manual(id, obj.project, where, "Oraxen 放不下（被佔用：${blocked.joinToString { "${it.x},${it.y},${it.z}" }}），物品已掉在原地", items.size)
                    } else {
                        val rest = if (plan.storage) Ox.putIntoStorage(plan.oraxenId, base, items) else items
                        drop(w, plan.placeAt, rest); dropped = true
                        state.ok++
                        journal.write(JournalType.DONE, id) { addProperty("stored", items.size - rest.size); addProperty("dropped", rest.size) }
                        report("OK     ${obj.project} @ $where → ${plan.oraxenId} (stored ${items.size - rest.size}, dropped ${rest.size})")
                    }
                }
                is Plan.Retire -> {
                    var problem: String? = null
                    plan.placeBlock?.let { (cell, block) ->
                        val b = w.getBlockAt(cell.x, cell.y, cell.z)
                        if (b.type.key.toString() == block) Unit   // already there (re-run)
                        else if (b.type.isAir || Ox.isReplaceable(w, cell)) b.setBlockData(Bukkit.createBlockData(block), false)
                        else problem = "原位置被 ${b.type.key} 佔用，沒有放回 $block"
                    }
                    drop(w, o, items); dropped = true
                    val p = problem
                    if (p != null) manual(id, obj.project, where, p, items.size)
                    else {
                        state.retired++
                        journal.write(JournalType.DONE, id) { addProperty("retired", true); addProperty("dropped", items.size) }
                        report("RETIRE ${obj.project} @ $where (dropped ${items.size})")
                    }
                }
                is Plan.Skip -> Unit
            }
        } catch (t: Throwable) {
            plugin.logger.severe("FurnMigrate: $id failed: $t")
            t.printStackTrace()
            if (!removed && Fl.find(id) != null) {
                // The old piece (with its items) is still there: dropping now would duplicate them.
                state.skipped++
                journal.write(JournalType.ABORTED, id) { addProperty("reason", "error before removal: $t") }
                report("ERROR  ${obj.project} @ $where — $t (old piece left in place)")
            } else {
                if (!dropped) runCatching { drop(w, o, items) }
                manual(id, obj.project, where, "例外：$t（物品已掉在原地）", items.size)
            }
        }
    }

    private fun manual(id: String, style: String, where: String, reason: String, items: Int) {
        state.manual++
        journal?.write(JournalType.NEEDS_MANUAL, id) { addProperty("style", style); addProperty("where", where); addProperty("reason", reason) }
        if (items > 0) journal?.write(JournalType.ITEMS_DROPPED, id) { addProperty("count", items) }
        report("MANUAL $style @ $where — $reason")
        Msg.broadcast(Msg.NEEDS_MANUAL, "style" to style, "where" to where, "reason" to reason)
    }

    private fun dryCheck(obj: ObjectID, piece: world.lunlun.furnmigrate.common.PlacedPiece, plan: Plan, where: String, items: Int, rescued: Int) {
        val w = world ?: return
        when (plan) {
            is Plan.Replace -> {
                val problem = when {
                    !Ox.isLoaded(plan.oraxenId) -> "Oraxen 沒有 ${plan.oraxenId}"
                    else -> MigrationPlanner.blockedCells(Ox.barrierCells(plan.oraxenId, w, plan.placeAt, plan.oraxenYaw), piece, plan.keepCells) { Ox.isReplaceable(w, it) }
                        .takeIf { it.isNotEmpty() }?.let { c -> "預計放不下（被佔用：${c.joinToString { "${it.x},${it.y},${it.z}" }}）" }
                }
                val slots = if (plan.storage) Ox.storageSlots(plan.oraxenId) else 0
                val itemNote = if (items == 0) "" else if (plan.storage) " items=$items→storage(${minOf(items, slots)}) drop=${maxOf(0, items - slots)}" else " items=$items→drop"
                val keepNote = (if (plan.keepCells.isEmpty()) "" else " keep=${plan.keepCells.size}") + (if (rescued == 0) "" else " rescued=$rescued")
                if (problem != null) { state.manual++; report("MANUAL ${obj.project} @ $where → ${plan.oraxenId} — $problem$itemNote") }
                else { state.ok++; report("OK     ${obj.project} @ $where → ${plan.oraxenId} at ${plan.placeAt.x},${plan.placeAt.y},${plan.placeAt.z} yaw ${plan.oraxenYaw}$keepNote$itemNote") }
            }
            is Plan.Retire -> {
                val blocked = plan.placeBlock?.let { (cell, _) -> cell !in piece.blocks && !Ox.isReplaceable(w, cell) } ?: false
                val what = if (plan.keepCells.isNotEmpty()) "keeps its real block" else plan.placeBlock?.second?.let { "puts $it" } ?: "nothing left"
                if (blocked) { state.manual++; report("MANUAL ${obj.project} @ $where — 退役方塊的位置被佔用") }
                else { state.retired++; report("RETIRE ${obj.project} @ $where — $what${if (items > 0) " items=$items→drop" else ""}") }
            }
            is Plan.Skip -> Unit
        }
    }

    /** Drops items just above [cell]; they never despawn (items must never vanish). */
    private fun drop(w: World, cell: IVec3, items: List<ItemStack>) {
        val loc = Location(w, cell.x + 0.5, cell.y + 1.2, cell.z + 0.5)
        for (it in items) w.dropItem(loc, it) { e ->
            e.isUnlimitedLifetime = true
            e.velocity = Vector(0, 0, 0)
        }
    }
}
