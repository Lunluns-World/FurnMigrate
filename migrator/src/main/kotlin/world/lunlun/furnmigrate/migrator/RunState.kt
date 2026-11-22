package world.lunlun.furnmigrate.migrator

import org.bukkit.configuration.file.YamlConfiguration
import world.lunlun.furnmigrate.common.ChunkKey
import java.io.File

/** Checkpoint of a real run (`plugins/FurnMigrate/state.yml`), saved after every chunk. */
class RunState(private val file: File) {
    var runId: String = ""
    var journal: String = ""
    var totalChunks: Int = 0
    val doneChunks: MutableSet<ChunkKey> = linkedSetOf()
    var finished: Boolean = true
    var paused: Boolean = false
    var ok = 0
    var retired = 0
    var manual = 0
    var skipped = 0

    val unfinished: Boolean get() = runId.isNotEmpty() && !finished

    fun load(): RunState {
        if (!file.isFile) return this
        val y = YamlConfiguration.loadConfiguration(file)
        runId = y.getString("run_id", "")!!
        journal = y.getString("journal", "")!!
        totalChunks = y.getInt("total_chunks")
        doneChunks.clear(); y.getStringList("done_chunks").mapTo(doneChunks) { ChunkKey.parse(it) }
        finished = y.getBoolean("finished", true)
        paused = y.getBoolean("paused", false)
        ok = y.getInt("counters.ok"); retired = y.getInt("counters.retired")
        manual = y.getInt("counters.manual"); skipped = y.getInt("counters.skipped")
        return this
    }

    fun save() {
        val y = YamlConfiguration()
        y.options().setHeader(listOf("FurnMigrate checkpoint. Do not edit while a run is active."))
        y.set("run_id", runId)
        y.set("journal", journal)
        y.set("total_chunks", totalChunks)
        y.set("done_chunks", doneChunks.map { it.toString() })
        y.set("finished", finished)
        y.set("paused", paused)
        y.set("counters.ok", ok); y.set("counters.retired", retired)
        y.set("counters.manual", manual); y.set("counters.skipped", skipped)
        val tmp = File(file.parentFile, file.name + ".tmp")
        y.save(tmp)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    fun reset(runId: String, journal: String, totalChunks: Int) {
        this.runId = runId; this.journal = journal; this.totalChunks = totalChunks
        doneChunks.clear(); finished = false; paused = false
        ok = 0; retired = 0; manual = 0; skipped = 0
    }
}
