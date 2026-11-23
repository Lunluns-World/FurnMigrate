package world.lunlun.furnmigrate.testkit

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.Ste3et_C0st.FurnitureLib.main.FurnitureLib
import de.Ste3et_C0st.FurnitureLib.main.FurnitureManager
import de.Ste3et_C0st.FurnitureLib.main.Type
import de.Ste3et_C0st.FurnitureLib.main.entity.fContainerEntity
import io.th0rgal.oraxen.api.OraxenFurniture
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic
import io.th0rgal.oraxen.mechanics.provided.gameplay.storage.StorageMechanic
import io.th0rgal.oraxen.shaded.jeff_media.morepersistentdatatypes.DataType
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.block.Chest
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Item
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import world.lunlun.furnmigrate.common.Facing
import world.lunlun.furnmigrate.common.IVec3
import world.lunlun.furnmigrate.common.MappingIO
import world.lunlun.furnmigrate.common.MigrationPlanner
import world.lunlun.furnmigrate.common.PlacedPiece
import world.lunlun.furnmigrate.common.Plan
import world.lunlun.furnmigrate.common.normalizeId
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * LOCAL TEST ONLY. `ftk spawn` places every mapped style (plus one unmapped) in all four facings with
 * player items, records the old state; `ftk verify` checks a finished FurnMigrate run against it.
 */
class TestKitPlugin : JavaPlugin() {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val expectedFile get() = File(dataFolder, "expected.json")
    private val unmapped = "Guillotine"
    private val keepReal = Regex("minecraft:(crafting_table|chest|trapped_chest|.*_bed|furnace|barrel)")
    private val nonSolid = Regex("minecraft:(.*torch|.*_sign|.*_button|lever|.*pressure_plate|.*carpet|tripwire.*|rail|.*_rail|.*flower|.*sapling|light|air|redstone_wire)")
    private val torch = Regex("minecraft:.*torch")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.firstOrNull()) {
            "spawn" -> spawn(sender)
            "verify" -> loadThen(sender) { verify(sender) }
            "count" -> sender.sendMessage("FL objects: ${liveObjects().size}")
            "audit" -> loadThen(sender, auditCells()) { audit(sender) }
            "recipes" -> recipes(sender)
            // Oraxen wall placing needs the clicked block to be solid (LimitedPlacing#isNotPlacableOn)
            "solid" -> sender.sendMessage("ftk solid: " + args.drop(1).joinToString { m -> "$m=${Material.matchMaterial(m)?.isSolid}" })
            else -> sender.sendMessage("ftk spawn | verify | count")
        }
        return true
    }

    private fun liveObjects() = FurnitureLib.getInstance().furnitureManager.objectList.filter { it.sqlAction != Type.SQLAction.REMOVE }

    private fun mapping() = File(dataFolder.parentFile, "FurnMigrate/mapping.yml").bufferedReader().use { MappingIO.read(it) }

    private fun itemFor(part: String): Material? = when {
        part.startsWith("#SLOT1") -> Material.IRON_SWORD
        part.startsWith("#SLOT2") -> Material.IRON_AXE
        part.startsWith("#SLOT3") -> Material.GOLDEN_SWORD
        part.startsWith("#ITEM") -> Material.COOKED_CHICKEN
        part.startsWith("#TRASH") -> Material.ROTTEN_FLESH
        else -> null
    }

    private fun spawn(sender: CommandSender) {
        val world = Bukkit.getWorlds().first()
        val styles = mapping().entries.keys.sorted() + unmapped
        val out = JsonArray()
        for ((row, style) in styles.withIndex()) {
            val project = FurnitureManager.getInstance().getProject(style)
            if (project == null) { sender.sendMessage("no FurnitureLib project $style"); continue }
            for ((col, yaw) in listOf(0f, 90f, 180f, 270f).withIndex()) {
                val x = col * 16; val z = row * 16
                val y = world.getHighestBlockYAt(x, z) + 1
                // Like FurnitureLib's own placement: block corner in, spawn() moves it to the block centre (+.5).
                val obj = FurnitureLib.getInstance().spawn(project, Location(world, x.toDouble(), y.toDouble(), z.toDouble(), yaw, 0f))
                if (obj == null) { sender.sendMessage("spawn failed $style $yaw"); continue }
                FurnitureManager.getInstance().addObjectID(obj)
                val start = obj.startLocation
                val items = JsonArray()
                val seats = JsonArray()
                for (e in obj.packetList) {
                    val name = e.name ?: ""
                    if (name.matches(Regex("^#(SITZ|Mount:).*", RegexOption.IGNORE_CASE))) seats.add(JsonObject().apply {
                        addProperty("x", e.location.x); addProperty("z", e.location.z)
                    })
                    if (e is fContainerEntity) itemFor(name.uppercase())?.let { m ->
                        e.setItemInMainHand(ItemStack(m))
                        items.add(m.key.toString())
                    }
                }
                obj.packetList.forEach { runCatching { it.update() } }
                val blocks = JsonObject()
                for (l in obj.blockList) {
                    val b = world.getBlockAt(l)
                    blocks.addProperty("${l.blockX},${l.blockY},${l.blockZ}", b.type.key.toString())
                    (b.state as? Chest)?.let { c -> c.inventory.addItem(ItemStack(Material.DIAMOND, 5)) }
                }
                out.add(JsonObject().apply {
                    addProperty("id", obj.id); addProperty("style", style); addProperty("world", world.name)
                    addProperty("x", start.blockX); addProperty("y", start.blockY); addProperty("z", start.blockZ); addProperty("yaw", start.yaw)
                    addProperty("start", "${start.x},${start.y},${start.z}")
                    add("blocks", blocks); add("items", items); add("seats", seats)
                })
            }
        }
        FurnitureLib.getInstance().sqlManager.save()
        dataFolder.mkdirs()
        expectedFile.writeText(gson.toJson(out))
        sender.sendMessage("ftk: spawned ${out.size()} pieces, FL objects now ${liveObjects().size}")
    }

    /** Keeps every test chunk (and neighbours) loaded and waits until their entities are loaded too. */
    private fun expectedCells(): List<Pair<World, IVec3>> {
        val world = Bukkit.getWorlds().first()
        return JsonParser.parseString(expectedFile.readText()).asJsonArray.map { it.asJsonObject }
            .map { e -> world to IVec3(e["x"].asInt, 0, e["z"].asInt) }
    }

    private fun loadThen(sender: CommandSender, cells: List<Pair<World, IVec3>> = expectedCells(), then: () -> Unit) {
        val chunks = cells.flatMap { (w, c) ->
            val cx = c.x shr 4; val cz = c.z shr 4
            (-1..1).flatMap { dx -> (-1..1).map { dz -> Triple(w, cx + dx, cz + dz) } }
        }.distinct().map { (w, x, z) -> w.getChunkAt(x, z).also { it.addPluginChunkTicket(this) } }
        sender.sendMessage("ftk: loading ${chunks.size} chunks")
        lateinit var task: org.bukkit.scheduler.BukkitTask
        var waited = 0
        task = Bukkit.getScheduler().runTaskTimer(this, Runnable {
            waited++
            if (chunks.all { it.isEntitiesLoaded } || waited > 400) {
                task.cancel()
                then()
                chunks.forEach { it.removePluginChunkTicket(this) }
            }
        }, 5L, 1L)
    }

    private fun verify(sender: CommandSender) {
        val mapping = mapping()
        val expected = JsonParser.parseString(expectedFile.readText()).asJsonArray.map { it.asJsonObject }
        val live = liveObjects().map { it.id }.toSet()
        val lines = mutableListOf<String>()
        var pass = 0; var fail = 0
        fun check(ok: Boolean, what: String) { if (ok) pass++ else { fail++; lines += "FAIL $what" } }
        val furnitureKey = NamespacedKey("oraxen", "furniture")

        for (e in expected) {
            val style = e["style"].asString; val yaw = e["yaw"].asDouble
            val tag = "$style yaw=${yaw.toInt()} @${e["x"].asInt},${e["z"].asInt}"
            val world: World = Bukkit.getWorld(e["world"].asString)!!
            val blocks = e["blocks"].asJsonObject.entrySet().associate { (k, v) ->
                val (x, y, z) = k.split(',').map { it.toInt() }; IVec3(x, y, z) to v.asString
            }
            val origin = IVec3(e["x"].asInt, e["y"].asInt, e["z"].asInt)
            val entry = mapping.find(style)
            val piece = PlacedPiece(style, origin, yaw, blocks)
            val plan = MigrationPlanner.plan(entry, piece)
            val id = e["id"].asString
            fun type(c: IVec3) = world.getBlockAt(c.x, c.y, c.z).type.key.toString()

            if (plan is Plan.Skip) {
                check(id in live, "$tag unmapped piece should stay in FurnitureLib")
                check(blocks.all { (c, t) -> type(c) == t }, "$tag unmapped piece blocks should be untouched")
                continue
            }
            check(id !in live, "$tag old FurnitureLib object still exists")
            val keep = plan.keepCells.toSet()
            for (c in keep) check(normalizeId(type(c)) == normalizeId(blocks[c]!!), "$tag kept ${blocks[c]} at $c is now ${type(c)}")
            for (c in keep) (world.getBlockAt(c.x, c.y, c.z).state as? Chest)?.let { ch ->
                check(ch.inventory.contains(Material.DIAMOND, 5), "$tag chest at $c lost its diamonds")
            }
            for ((c, t) in blocks) if (c !in keep && torch.matches(t)) check(!torch.matches(type(c)), "$tag torch left at $c")

            when (plan) {
                is Plan.Replace -> {
                    val at = plan.placeAt
                    val center = Location(world, at.x + 0.5, at.y.toDouble(), at.z + 0.5)
                    val bases = world.getNearbyEntities(center, 0.6, 1.5, 0.6).filter { it is org.bukkit.entity.ItemDisplay && OraxenFurniture.isBaseEntity(it) }
                    check(bases.size == 1, "$tag expected one Oraxen base entity at $at, found ${bases.size}")
                    val base = bases.firstOrNull() ?: continue
                    val mech = OraxenFurniture.getFurnitureMechanic(base)
                    check(mech?.itemID == plan.oraxenId, "$tag placed ${mech?.itemID} instead of ${plan.oraxenId}")
                    val gotYaw = FurnitureMechanic.getFurnitureYaw(base)
                    val dy = abs(((gotYaw - plan.oraxenYaw) % 360 + 540) % 360 - 180)
                    check(dy < 1, "$tag yaw $gotYaw, expected ${plan.oraxenYaw}")

                    // barriers: the new ones must sit exactly where the old solid blocks were
                    val oldSolid = blocks.filter { (c, t) -> c !in keep && !nonSolid.matches(normalizeId(t)) }.keys
                    val newBarriers = if (mech != null && mech.hasBarriers()) Ox.cells(mech, plan.oraxenYaw, world, at) else emptySet()
                    check(newBarriers == oldSolid, "$tag barriers ${newBarriers.sortedBy { it.toString() }} vs old solid ${oldSolid.sortedBy { it.toString() }}")
                    for (c in newBarriers) check(world.getBlockAt(c.x, c.y, c.z).type == Material.BARRIER, "$tag no barrier block at $c")

                    // seats: same horizontal position as the old seat markers
                    val oldSeats = e["seats"].asJsonArray.map { it.asJsonObject["x"].asDouble to it.asJsonObject["z"].asDouble }
                    val newSeats = world.getNearbyEntities(center, 8.0, 4.0, 8.0).filterIsInstance<ArmorStand>()
                        .filter { it.persistentDataContainer.get(furnitureKey, PersistentDataType.STRING) == plan.oraxenId }
                        .map { it.location.x to it.location.z }
                    if (oldSeats.isNotEmpty()) {
                        check(newSeats.size == oldSeats.size, "$tag ${newSeats.size} seats, expected ${oldSeats.size}")
                        for ((sx, sz) in oldSeats) check(newSeats.any { hypot(it.first - sx, it.second - sz) < 0.2 },
                            "$tag no seat near old seat ${"%.2f".format(sx)},${"%.2f".format(sz)}; new: ${newSeats.joinToString { "%.2f,%.2f".format(it.first, it.second) }}")
                    }

                    // player items: into storage, or dropped next to the piece
                    val items = e["items"].asJsonArray.map { it.asString }.sorted()
                    if (items.isNotEmpty()) {
                        if (plan.storage) {
                            val stored = base.persistentDataContainer.get(StorageMechanic.STORAGE_KEY, DataType.ITEM_STACK_ARRAY)
                                ?.filterNotNull()?.filter { !it.type.isAir }?.map { it.type.key.toString() }?.sorted() ?: emptyList()
                            check(stored == items, "$tag storage $stored, expected $items")
                        } else {
                            val dropped = world.getNearbyEntities(center, 3.0, 3.0, 3.0).filterIsInstance<Item>()
                                .map { it.itemStack.type.key.toString() }.sorted()
                            check(dropped.containsAll(items), "$tag dropped $dropped, expected $items")
                        }
                    }
                }
                is Plan.Retire -> {
                    plan.keepCells.forEach { c -> check(type(c) == normalizeId(blocks[c]!!), "$tag retired block missing at $c") }
                    plan.placeBlock?.let { (c, b) -> check(type(c) == b, "$tag expected $b at $c, found ${type(c)}") }
                    for ((c, _) in blocks) if (c !in keep) check(type(c) != "minecraft:barrier", "$tag barrier left at $c")
                }
                is Plan.Skip -> Unit
            }
        }
        val report = File(dataFolder, "verify.txt")
        report.writeText((listOf("PASS $pass FAIL $fail") + lines).joinToString("\n") + "\n")
        sender.sendMessage("ftk verify: PASS $pass FAIL $fail (details in ${report.path})")
    }

    // ------------------------------------------------------------------ audit of a real run, from the journal

    private fun journalLines(): List<JsonObject> {
        val dir = File(dataFolder.parentFile, "FurnMigrate/journal")
        val f = dir.listFiles { x -> x.name.endsWith(".jsonl") }?.maxByOrNull { it.name } ?: return emptyList()
        return f.readLines().filter { it.isNotBlank() }.map { JsonParser.parseString(it).asJsonObject }
    }

    private fun auditCells(): List<Pair<World, IVec3>> = journalLines().filter { it["type"].asString == "BEGIN" }.mapNotNull { b ->
        Bukkit.getWorld(b["world"].asString)?.let { it to IVec3(b["x"].asInt, b["y"].asInt, b["z"].asInt) }
    }

    /**
     * Checks every piece of the last FurnMigrate run against its journal record: new furniture, yaw,
     * barriers on the old solid cells, kept blocks and their contents, storage / dropped items, no old
     * torches or barriers left, and that skipped pieces are still FurnitureLib objects.
     */
    private fun audit(sender: CommandSender) {
        val lines = journalLines()
        val begin = linkedMapOf<String, JsonObject>()
        val last = hashMapOf<String, String>()
        for (l in lines) {
            val id = l["id"].asString
            when (val t = l["type"].asString) {
                "BEGIN" -> begin[id] = l
                "DONE", "NEEDS_MANUAL", "ABORTED", "CLEANUP", "SKIPPED" -> last[id] = t
            }
        }
        val mapping = mapping()
        val live = liveObjects().map { it.id }.toSet()
        val out = mutableListOf<String>()
        var pass = 0; var fail = 0
        fun check(ok: Boolean, what: String) { if (ok) pass++ else { fail++; out += "FAIL $what" } }
        val tally = sortedMapOf<String, Int>()
        last.values.forEach { tally[it] = (tally[it] ?: 0) + 1 }

        for ((id, t) in last) if (t == "SKIPPED") check(id in live, "$id skipped but no longer a FurnitureLib object")
        for ((id, b) in begin) {
            val result = last[id]
            val style = b["style"].asString
            val world = Bukkit.getWorld(b["world"].asString)!!
            val o = IVec3(b["x"].asInt, b["y"].asInt, b["z"].asInt)
            val tag = "$style ${world.name} ${o.x},${o.y},${o.z}"
            if (result == "NEEDS_MANUAL") { out += "MANUAL $tag"; continue }
            if (result != "DONE") { out += "OPEN $tag ($result)"; continue }
            check(id !in live, "$tag still a FurnitureLib object")
            fun type(c: IVec3) = world.getBlockAt(c.x, c.y, c.z).type.key.toString()
            val old = b.getAsJsonObject("old_blocks")?.entrySet()?.associate { (k, v) ->
                val (x, y, z) = k.split(',').map { it.toInt() }; IVec3(x, y, z) to v.asString
            } ?: emptyMap()
            val keep = b.getAsJsonArray("keep_blocks").map { it.asJsonObject }
            val keepCells = keep.map { k -> k["at"].asString.split(',').map { it.toInt() }.let { (x, y, z) -> IVec3(x, y, z) } }.toSet()
            for (k in keep) {
                val (x, y, z) = k["at"].asString.split(',').map { it.toInt() }
                val block = world.getBlockAt(x, y, z)
                check(block.blockData.asString == k["block"].asString, "$tag kept block at $x,$y,$z is ${block.blockData.asString}, was ${k["block"].asString}")
                k.getAsJsonArray("contents")?.let { arr ->
                    val want = arr.map { it.asJsonObject.let { i -> i["material"].asString to i["amount"].asInt } }.sortedBy { it.toString() }
                    val got = ((block.state as? org.bukkit.block.Container)?.inventory?.contents ?: emptyArray())
                        .filterNotNull().filter { !it.type.isAir }.map { it.type.key.toString() to it.amount }.sortedBy { it.toString() }
                    check(got == want, "$tag container at $x,$y,$z holds $got, had $want")
                }
            }
            for ((c, t0) in old) if (c !in keepCells && torch.matches(t0)) check(!torch.matches(type(c)), "$tag torch left at $c")
            val items = b.getAsJsonArray("items").map { it.asJsonObject["material"].asString }.sorted()
            val oraxenId = b["oraxen_id"]?.asString
            if (oraxenId != null) {
                val at = b["place_at"].asString.split(',').map { it.toInt() }.let { (x, y, z) -> IVec3(x, y, z) }
                val yaw = b["oraxen_yaw"].asFloat
                val center = Location(world, at.x + 0.5, at.y.toDouble(), at.z + 0.5)
                val bases = world.getNearbyEntities(center, 0.6, 1.5, 0.6).filter { it is org.bukkit.entity.ItemDisplay && OraxenFurniture.isBaseEntity(it) }
                check(bases.size == 1, "$tag expected one Oraxen base at $at, found ${bases.size}")
                val base = bases.firstOrNull() ?: continue
                val mech = OraxenFurniture.getFurnitureMechanic(base)
                check(mech?.itemID == oraxenId, "$tag placed ${mech?.itemID} instead of $oraxenId")
                val dy = abs(((FurnitureMechanic.getFurnitureYaw(base) - yaw) % 360 + 540) % 360 - 180)
                check(dy < 1, "$tag yaw ${FurnitureMechanic.getFurnitureYaw(base)}, expected $yaw")
                // The world may differ from the model (blocks broken long ago, planted pots): new barriers must
                // sit on old cells, and every old cell that really held a solid block must now be a barrier.
                val oldSolid = old.filter { (c, t0) -> c !in keepCells && !nonSolid.matches(normalizeId(t0)) && !t0.contains("potted_") }.keys
                val newBarriers = if (mech != null && mech.hasBarriers()) Ox.cells(mech, yaw, world, at) else emptySet()
                check(old.keys.containsAll(newBarriers), "$tag barriers ${newBarriers.sortedBy { it.toString() }} not all on old cells ${old.keys.sortedBy { it.toString() }}")
                check(newBarriers.containsAll(oldSolid), "$tag old solid ${oldSolid.sortedBy { it.toString() }} not all covered by barriers ${newBarriers.sortedBy { it.toString() }}")
                for (c in newBarriers) check(world.getBlockAt(c.x, c.y, c.z).type == Material.BARRIER, "$tag no barrier at $c")
                if (items.isNotEmpty()) {
                    val storage = mapping.find(style)?.storage == true
                    if (storage) {
                        val stored = base.persistentDataContainer.get(StorageMechanic.STORAGE_KEY, DataType.ITEM_STACK_ARRAY)
                            ?.filterNotNull()?.filter { !it.type.isAir }?.map { it.type.key.toString() }?.sorted() ?: emptyList()
                        check(stored == items, "$tag storage $stored, expected $items")
                    } else {
                        val dropped = world.getNearbyEntities(center, 3.0, 3.0, 3.0).filterIsInstance<Item>().map { it.itemStack.type.key.toString() }
                        check(dropped.containsAll(items), "$tag dropped $dropped, expected $items")
                    }
                }
            } else {
                for ((c, _) in old) if (c !in keepCells) check(type(c) != "minecraft:barrier", "$tag barrier left at $c")
            }
        }
        val report = File(dataFolder, "audit.txt")
        dataFolder.mkdirs()
        report.writeText((listOf("PASS $pass FAIL $fail  results=$tally") + out).joinToString("\n") + "\n")
        sender.sendMessage("ftk audit: PASS $pass FAIL $fail $tally (details in ${report.path})")
    }

    /** Puts every dice_* shaped recipe into a crafting grid and checks what the server actually crafts. */
    private fun recipes(sender: CommandSender) {
        val cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(File(dataFolder.parentFile, "Oraxen/recipes/shaped.yml"))
        val world = Bukkit.getWorlds().first()
        val out = mutableListOf<String>()
        var ok = 0
        val keys = cfg.getKeys(false).filter { it.startsWith("dice_") || it.startsWith("lun_") }
        for (key in keys) {
            val sec = cfg.getConfigurationSection(key)!!
            // Oraxen 1.221 spells the keys with hyphens
            val want = sec.getString("result.oraxen_item") ?: sec.getString("result.oraxen-item")
            val shape = sec.getStringList("shape")
            val matrix = Array(9) { ItemStack(Material.AIR) }
            for ((y, row) in shape.withIndex()) for ((x, ch) in row.withIndex()) {
                val m = sec.getString("ingredients.$ch.minecraft_type") ?: sec.getString("ingredients.$ch.minecraft-type") ?: continue
                matrix[y * 3 + x] = ItemStack(Material.matchMaterial(m) ?: error("$key: unknown material $m"))
            }
            val recipe = Bukkit.getCraftingRecipe(matrix, world)
            val got = recipe?.result?.let { io.th0rgal.oraxen.api.OraxenItems.getIdByItem(it) ?: it.type.key.toString() } ?: "nothing"
            if (got == want) ok++ else out += "FAIL $key crafts $got (${recipe?.let { (it as? org.bukkit.Keyed)?.key } ?: "-"})"
        }
        File(dataFolder, "recipes.txt").apply { parentFile.mkdirs() }.writeText((listOf("OK $ok of ${keys.size}") + out).joinToString("\n") + "\n")
        sender.sendMessage("ftk recipes: OK $ok of ${keys.size}" + out.joinToString("") { "\n  $it" })
    }

    private object Ox {
        fun cells(mech: FurnitureMechanic, yaw: Float, world: World, at: IVec3): Set<IVec3> =
            mech.getLocations(yaw, Location(world, at.x.toDouble(), at.y.toDouble(), at.z.toDouble()), mech.barriers)
                .mapTo(hashSetOf()) { IVec3(it.blockX, it.blockY, it.blockZ) }
    }

    @Suppress("unused") private val keepFacing = Facing   // common is bundled
}
