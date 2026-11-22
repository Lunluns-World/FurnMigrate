package world.lunlun.furnmigrate.migrator

import com.google.gson.JsonObject
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.util.Vector
import world.lunlun.furnmigrate.common.ConfirmGate
import world.lunlun.furnmigrate.common.JournalType
import world.lunlun.furnmigrate.common.MappingAction
import java.io.File

class Commands(private val plugin: FurnMigratePlugin) : TabExecutor {
    private val gate = ConfirmGate(30_000)
    private val subs = listOf("scan", "dry-run", "run", "status", "pause", "resume", "manual", "dropitems")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            "scan" -> scan(sender)
            "dry-run", "dryrun" -> plugin.startDryRun(sender)
            "run" -> run(sender)
            "status" -> status(sender)
            "pause" -> plugin.pause(sender)
            "resume" -> plugin.resume(sender)
            "manual" -> manual(sender)
            "dropitems" -> args.getOrNull(1)?.let { dropItems(sender, it) } ?: Msg.send(sender, Msg.USAGE)
            else -> Msg.send(sender, Msg.USAGE)
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> =
        if (args.size == 1) subs.filter { it.startsWith(args[0].lowercase()) } else emptyList()

    private fun scan(sender: CommandSender) {
        val mapping = plugin.loadMapping(sender) ?: return
        Msg.send(sender, Msg.MAPPING_LOADED, "n" to mapping.entries.size)
        val objs = Fl.liveObjects()
        val file = File(plugin.dataFolder, "reports/scan-${plugin.stamp()}.txt").apply { parentFile.mkdirs() }
        file.writeText(buildString {
            appendLine("# FurnMigrate scan: ${objs.size} pieces")
            for (o in objs.sortedWith(compareBy({ Fl.worldName(it) }, { it.project }, { it.id }))) {
                val l = o.startLocation
                val e = mapping.find(o.project)
                appendLine("${Fl.worldName(o)} ${l.blockX},${l.blockY},${l.blockZ} yaw ${l.yaw} ${o.project} ${o.id} → ${e?.action ?: "UNMAPPED"} ${e?.oraxenId.orEmpty()}")
            }
        })
        Msg.send(sender, Msg.SCAN_HEAD, "total" to objs.size)
        for ((group, list) in objs.groupBy { Fl.worldName(it) to it.project }.toSortedMap(compareBy({ it.first }, { it.second }))) {
            val e = mapping.find(group.second)
            val action = when {
                e == null -> "<gold>跳過（沒有對照）"
                e.action == MappingAction.RETIRE -> "<gray>退役（${e.retireBlock ?: "-"}）"
                else -> "<green>${e.oraxenId}"
            }
            Msg.send(sender, Msg.SCAN_LINE, "world" to group.first, "style" to group.second, "n" to list.size, "action" to Msg.mini(action))
        }
        val unmapped = mapping.missing(objs.map { it.project })
        if (unmapped.isNotEmpty()) Msg.send(sender, Msg.SCAN_UNMAPPED, "styles" to unmapped.joinToString())
        val missing = plugin.missingOraxenIds(mapping)
        if (missing.isNotEmpty()) Msg.send(sender, Msg.IDS_MISSING, "ids" to missing.joinToString())
        Msg.send(sender, Msg.SCAN_FILE, "file" to plugin.rel(file))
    }

    private fun run(sender: CommandSender) {
        if (plugin.engine != null) return Msg.send(sender, Msg.BUSY, "mode" to plugin.engine!!.modeName)
        if (plugin.state.unfinished) {
            return Msg.send(sender, Msg.UNFINISHED, "done" to plugin.state.doneChunks.size, "total" to plugin.state.totalChunks)
        }
        if (gate.confirm(sender.name, System.currentTimeMillis())) plugin.startRun(sender, resume = false)
        else Msg.send(sender, Msg.RUN_WARN, "total" to Fl.liveObjects().size)
    }

    private fun status(sender: CommandSender) {
        val e = plugin.engine
        val st = e?.state ?: plugin.state
        if (e == null && st.runId.isEmpty()) return Msg.send(sender, Msg.NOT_RUNNING)
        val (mode, stateText) = when {
            e != null && e.paused -> e.modeName to "<yellow>暫停中"
            e != null -> e.modeName to "<green>進行中"
            st.unfinished -> "run" to "<gold>未完成（可 resume）"
            else -> "run" to "<aqua>已完成"
        }
        Msg.send(sender, Msg.STATUS, "mode" to mode, "state" to Msg.mini(stateText), *(e?.counters() ?: arrayOf(
            "done" to st.doneChunks.size, "total" to st.totalChunks, "ok" to st.ok, "retired" to st.retired,
            "manual" to st.manual, "skipped" to st.skipped,
        )), "file" to (if (e?.dry == true) "-" else "plugins/FurnMigrate/${st.journal}"))
    }

    /** Per object id: its BEGIN record, whether it needs manual work, and whether its items were dropped. */
    private data class Record(val journal: Journal, val begin: JsonObject?, val manual: JsonObject?, val dropped: Boolean)

    private fun records(): Map<String, Record> {
        val out = linkedMapOf<String, Record>()
        for (j in plugin.journals()) for (line in j.lines()) {
            val id = line["id"]?.asString ?: continue
            val r = out[id] ?: Record(j, null, null, false)
            out[id] = when (line["type"]?.asString) {
                JournalType.BEGIN -> Record(j, line, null, false)
                JournalType.NEEDS_MANUAL -> r.copy(manual = line)
                JournalType.ITEMS_DROPPED -> r.copy(dropped = true)
                JournalType.DONE, JournalType.ABORTED, JournalType.CLEANUP -> r.copy(manual = null)
                else -> r
            }
        }
        return out
    }

    private fun manual(sender: CommandSender) {
        val list = records().filterValues { it.manual != null }
        if (list.isEmpty()) return Msg.send(sender, Msg.MANUAL_NONE)
        Msg.send(sender, Msg.MANUAL_HEAD, "n" to list.size)
        for ((id, r) in list) {
            val b = r.begin
            val where = b?.let { "${it["world"].asString} ${it["x"].asInt},${it["y"].asInt},${it["z"].asInt}" } ?: "?"
            val n = b?.getAsJsonArray("items")?.size() ?: 0
            val items = when {
                n == 0 -> ""
                r.dropped -> "（$n 個物品已掉出）"
                else -> "（$n 個物品在 journal 裡，可 /furnmigrate dropitems $id）"
            }
            Msg.send(sender, Msg.MANUAL_LINE, "id" to id, "style" to (b?.get("style")?.asString ?: "?"), "where" to where,
                "reason" to (r.manual?.get("reason")?.asString ?: ""), "items" to items)
        }
    }

    private fun dropItems(sender: CommandSender, id: String) {
        val r = records()[id]
        val b = r?.begin
        val items = Journal.decodeItems(b?.getAsJsonArray("items"))
        if (r == null || b == null || r.dropped || items.isEmpty()) return Msg.send(sender, Msg.DROP_UNKNOWN, "id" to id)
        val world = Bukkit.getWorld(b["world"].asString) ?: return Msg.send(sender, Msg.DROP_UNKNOWN, "id" to id)
        val loc = Location(world, b["x"].asInt + 0.5, b["y"].asInt + 1.2, b["z"].asInt + 0.5)
        for (it in items) world.dropItem(loc, it) { e -> e.isUnlimitedLifetime = true; e.velocity = Vector(0, 0, 0) }
        r.journal.write(JournalType.ITEMS_DROPPED, id) { addProperty("count", items.size); addProperty("by", sender.name) }
        Msg.send(sender, Msg.DROPPED, "n" to items.size, "where" to "${world.name} ${b["x"].asInt},${b["y"].asInt},${b["z"].asInt}")
    }
}
