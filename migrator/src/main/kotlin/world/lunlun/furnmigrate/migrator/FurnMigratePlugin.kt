package world.lunlun.furnmigrate.migrator

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import world.lunlun.furnmigrate.common.ChunkKey
import world.lunlun.furnmigrate.common.JournalRecovery
import world.lunlun.furnmigrate.common.JournalType
import world.lunlun.furnmigrate.common.Mapping
import world.lunlun.furnmigrate.common.MappingAction
import world.lunlun.furnmigrate.common.MappingIO
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FurnMigratePlugin : JavaPlugin() {
    lateinit var state: RunState
        private set
    var engine: Engine? = null
        private set

    override fun onEnable() {
        saveDefaultConfig()
        state = RunState(File(dataFolder, "state.yml")).load()
        val cmd = Commands(this)
        getCommand("furnmigrate")!!.apply { setExecutor(cmd); tabCompleter = cmd }
        if (state.unfinished) Bukkit.getScheduler().runTask(this, Runnable {
            Msg.broadcast(Msg.UNFINISHED, "done" to state.doneChunks.size, "total" to state.totalChunks)
        })
    }

    override fun onDisable() {
        engine?.let { e ->
            e.stop()
            if (!e.dry) state.save()   // unfinished stays unfinished: /furnmigrate resume after the restart
        }
        engine = null
    }

    fun objectsPerTick(): Int = config.getInt("objects-per-tick", 2).coerceIn(1, 50)

    private fun worldFilter(): Set<String> = config.getStringList("worlds").toSet()

    /** Path relative to the server folder, for messages. */
    fun rel(f: File): String = f.absoluteFile.relativeTo(dataFolder.absoluteFile.parentFile.parentFile).path

    fun stamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))

    fun loadMapping(sender: CommandSender): Mapping? {
        val f = File(dataFolder, "mapping.yml")
        if (!f.isFile) { Msg.send(sender, Msg.NO_MAPPING); return null }
        return try {
            f.bufferedReader().use { MappingIO.read(it) }
        } catch (e: Exception) {
            Msg.send(sender, Msg.MAPPING_ERROR, "error" to (e.message ?: e.toString())); null
        }
    }

    /** Chunks that hold live DiceFurniture in the selected worlds, sorted. */
    fun chunksToDo(): List<ChunkKey> {
        val worlds = worldFilter()
        return Fl.liveObjects().map { Fl.chunkOf(it) }.filter { worlds.isEmpty() || it.world in worlds }.distinct().sorted()
    }

    /** Oraxen ids of mapped styles that are actually placed but not loaded in Oraxen. */
    fun missingOraxenIds(mapping: Mapping): List<String> {
        val placed = Fl.liveObjects().map { it.project }.toSet()
        return placed.mapNotNull { mapping.find(it) }
            .filter { it.action == MappingAction.REPLACE && !Ox.isLoaded(it.oraxenId) }
            .map { it.oraxenId }.distinct().sorted()
    }

    fun startDryRun(sender: CommandSender) {
        if (engine != null) return Msg.send(sender, Msg.BUSY, "mode" to engine!!.modeName)
        val mapping = loadMapping(sender) ?: return
        val missing = missingOraxenIds(mapping)
        if (missing.isNotEmpty()) Msg.send(sender, Msg.IDS_MISSING, "ids" to missing.joinToString())
        val id = stamp()
        val keys = chunksToDo()
        val dryState = RunState(File(dataFolder, "dryrun-state.unused")).apply { reset(id, "", keys.size) }
        val e = Engine(this, mapping, true, keys, dryState, null,
            File(dataFolder, "reports/dryrun-$id.txt"), File(dataFolder, "reports/orphans-dryrun-$id.txt"))
        engine = e
        Msg.broadcast(Msg.DRY_START, "chunks" to keys.size)
        e.start()
    }

    /** A new run (after confirmation) or [resume] of the checkpointed one. */
    fun startRun(sender: CommandSender, resume: Boolean) {
        if (engine != null) return Msg.send(sender, Msg.BUSY, "mode" to engine!!.modeName)
        val mapping = loadMapping(sender) ?: return
        val missing = missingOraxenIds(mapping)
        if (missing.isNotEmpty()) return Msg.send(sender, Msg.IDS_MISSING, "ids" to missing.joinToString())
        val keys: List<ChunkKey>
        if (resume) {
            keys = chunksToDo().filter { it !in state.doneChunks }
            recoverInterrupted()
        } else {
            keys = chunksToDo()
            val id = stamp()
            state.reset(id, "journal/$id.jsonl", keys.size)
        }
        state.paused = false
        state.save()
        val e = Engine(this, mapping, false, keys, state, Journal(File(dataFolder, state.journal)),
            File(dataFolder, "reports/run-${state.runId}.txt"), File(dataFolder, "reports/orphans-run-${state.runId}.txt"))
        engine = e
        if (resume) Msg.broadcast(Msg.RESUMED) else Msg.broadcast(Msg.RUN_START, "chunks" to keys.size, "file" to "plugins/FurnMigrate/${state.journal}")
        e.start()
    }

    /**
     * Objects whose BEGIN line has no closing line: the server stopped in the middle of one. If the old
     * piece still exists nothing happened to it (ABORTED, it is simply done again); otherwise it is
     * NEEDS_MANUAL and its items stay in the journal for /furnmigrate dropitems.
     */
    private fun recoverInterrupted() {
        val journal = Journal(File(dataFolder, state.journal))
        val open = JournalRecovery.interrupted(journal.lines().map { it["type"].asString to it["id"].asString })
        var lost = 0
        for (id in open) {
            if (Fl.find(id) != null) journal.write(JournalType.ABORTED, id) { addProperty("reason", "server stopped before this chunk was saved; the piece is done again") }
            else {
                lost++; state.manual++
                journal.write(JournalType.NEEDS_MANUAL, id) {
                    addProperty("reason", "處理中被中斷（舊傢俱已移除）；請檢查新傢俱是否已放好，物品可用 /furnmigrate dropitems $id 掉出")
                }
            }
        }
        if (lost > 0) Msg.broadcast(Msg.INTERRUPTED, "n" to lost)
    }

    fun pause(sender: CommandSender) {
        val e = engine ?: return Msg.send(sender, Msg.NOT_RUNNING)
        e.paused = true
        if (!e.dry) { state.paused = true; state.save() }
        Msg.broadcast(Msg.PAUSED)
    }

    fun resume(sender: CommandSender) {
        val e = engine
        if (e != null) {
            if (!e.paused) return Msg.send(sender, Msg.BUSY, "mode" to e.modeName)
            e.paused = false
            if (!e.dry) { state.paused = false; state.save() }
            return Msg.broadcast(Msg.RESUMED)
        }
        if (!state.unfinished) return Msg.send(sender, Msg.NOTHING_TO_RESUME)
        startRun(sender, resume = true)
    }

    fun engineFinished(e: Engine) {
        if (engine === e) engine = null
    }

    /** Every journal under plugins/FurnMigrate/journal, oldest first. */
    fun journals(): List<Journal> =
        File(dataFolder, "journal").listFiles { f -> f.name.endsWith(".jsonl") }?.sortedBy { it.name }?.map { Journal(it) } ?: emptyList()
}
