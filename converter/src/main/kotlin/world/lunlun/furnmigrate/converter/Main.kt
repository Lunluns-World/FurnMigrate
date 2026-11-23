package world.lunlun.furnmigrate.converter

import world.lunlun.furnmigrate.common.oraxenIdFor
import world.lunlun.furnmigrate.converter.designs.Designs
import world.lunlun.furnmigrate.converter.designs.NewDesigns
import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """converter --models <FurnitureLib models folder> --out <out dir> [--only <style,...>]
          [--vanilla <client jar or extracted folder>] [--namespace lunworld] [--all true] [--oraxen 1.220|1.221]
  Default: only styles with a hand-made design are written (plus retired styles in mapping.yml).
  --all true also writes the automatic conversion of every other style (for reference).
  --oraxen: config syntax of the Oraxen version on the servers (default 1.220).
  --textures: folder with the server's own textures (default custom-textures/, not in git); pieces
    whose texture is missing there are left out."""

fun main(args: Array<String>) {
    val opts = parseArgs(args) ?: run { System.err.println("usage: $USAGE"); exitProcess(2) }
    val modelsDir = File(opts["models"] ?: fail("--models is required"))
    val outDir = File(opts["out"] ?: fail("--out is required"))
    val vanilla = File(opts["vanilla"] ?: "vanilla/client-1.21.8.jar")
    val namespace = opts["namespace"] ?: "lunworld"
    val all = opts["all"]?.toBoolean() ?: false
    val format = opts["oraxen"]?.let { runCatching { OraxenFormat.parse(it) }.getOrElse { e -> fail(e.message ?: "bad --oraxen") } } ?: OraxenFormat.V1_220
    val customDir = File(opts["textures"] ?: "custom-textures")
    val only = opts["only"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()

    if (!modelsDir.isDirectory) fail("models folder not found: $modelsDir")
    if (!vanilla.exists()) fail("vanilla assets not found: $vanilla")

    val files = modelsDir.listFiles { f -> f.isFile && (f.name.endsWith(".dModel") || f.name.endsWith(".yml")) }.orEmpty().sortedBy { it.name }
    val models = files.map { DModelParser.parse(it) }
    val selected = if (only == null) models else models.filter { m -> only.any { it.equals(m.style, true) } }
    only?.filter { o -> models.none { it.style.equals(o, true) } }?.let { if (it.isNotEmpty()) fail("unknown style(s): ${it.joinToString()}") }

    val rig = Rig()
    val results = VanillaAssets.open(vanilla).use { assets ->
        // A typo in a design texture shows up as the magenta/black "missing" texture in game.
        val missing = (Designs.all.values + NewDesigns.all.map { it.design })
            .flatMap { d -> d.textures.values.map { d.style to it } }
            .filter { (_, t) -> !CustomTextures.isCustom(t) && assets.texture(t) == null }
        if (missing.isNotEmpty()) fail("textures not in the client jar: " + missing.joinToString { (s, t) -> "$s → $t" })
        val conv = StyleConverter(assets, rig)
        selected.map { m ->
            val converted = conv.convert(m, oraxenIdFor(m.style))
            Designs.find(m.style)?.let { DesignApplier.apply(converted, it) } ?: converted
        }
    }
    val retired = Designs.retired.filterKeys { s -> selected.any { it.style == s } }
    val output = results.filter { (it.designed || all) && it.model.style !in retired }
    val notOutput = results.filter { it !in output && it.model.style !in retired }.map { it.model.style }
    Output.writeAll(outDir, namespace, output, rig, retired, notOutput, format)
    val (available, noTexture) = NewDesigns.all.partition { p -> CustomTextures.missing(p.design, customDir).isEmpty() }
    for (p in noTexture) println("left out ${p.oraxenId}: missing ${CustomTextures.missing(p.design, customDir).joinToString()} in $customDir")
    CustomTextures.copy(available.map { it.design }, customDir, File(outDir, "oraxen/pack/assets"))
    val newPieces = available.map { it to NewFurniture.result(it.oraxenId, it.design) }
    Output.writeNew(outDir, namespace, newPieces.map { it.second }, format)

    // Crafting recipes of the generated furniture (DiceFurniture `crafting:` → Oraxen shaped.yml entries).
    val fileOf = files.associateBy { f -> models[files.indexOf(f)].style }
    val dice = output.mapNotNull { r -> fileOf[r.model.style]?.let { Recipes.read(it) }?.let { it to r.oraxenId } }
    val recipes = dice.mapNotNull { (d, id) -> Recipes.toOraxen(d, id) }
    val recipeFile = File(outDir, "oraxen/recipes/dicefurniture-shaped.yml").apply { parentFile.mkdirs() }
    recipeFile.writeText(Recipes.toYaml(recipes, format))
    val newRecipes = newPieces.map { it.first.recipe }
    File(outDir, "oraxen/recipes/lunworld-furniture-shaped.yml").writeText(Recipes.toYaml(newRecipes, format, "Recipes of the new lunworld furniture"))
    val noRecipe = output.map { it.model.style }.filter { s -> recipes.none { r -> r.oraxenId == oraxenIdFor(s) } }
    val dup = Recipes.duplicates(recipes + newRecipes)
    File(outDir, "report.md").appendText(buildString {
        appendLine(); appendLine("## 合成表"); appendLine()
        appendLine("- 輸出 ${recipes.size} 個合成表到 `oraxen/recipes/dicefurniture-shaped.yml`（要附加到 Oraxen 的 `recipes/shaped.yml`）。")
        if (noRecipe.isNotEmpty()) appendLine("- 沒有合成表（DiceFurniture 原本就停用）：${noRecipe.joinToString()}")
        appendLine("- 新傢俱 ${newRecipes.size} 個合成表在 `oraxen/recipes/lunworld-furniture-shaped.yml`（同樣附加到 `recipes/shaped.yml`）。")
        if (dup.isNotEmpty()) appendLine("- ⚠️ 排列完全相同的合成表：${dup.joinToString("；") { it.joinToString(" = ") }}")
        appendLine(); appendLine("## 新傢俱（沒有 DiceFurniture 原型，不在 mapping.yml）"); appendLine()
        appendLine("| ID | 名稱 | 放置 | 屏障 | 座位 | 光源 | 儲物 |"); appendLine("|---|---|---|---|---|---|---|")
        for ((p, r) in newPieces) {
            val where = listOfNotNull("地面".takeIf { r.placing.floor }, "牆上".takeIf { r.placing.wall }, "天花板".takeIf { r.placing.roof }).joinToString("、")
            appendLine("| `${p.oraxenId}` | ${r.model.displayName.replace(Regex("&."), "")} | $where | ${r.barriers.size} | ${r.seats.size} | ${r.lights.size} | ${r.storage?.let { "${it.rows} 排" } ?: "—"} |")
        }
    })
    println("wrote ${recipes.size} recipe(s); without recipe: ${noRecipe.joinToString().ifEmpty { "-" }}; identical: ${dup.ifEmpty { "-" }}")
    for (r in output) {
        println("%-16s → %-18s %-9s elements=%-4d skipped=%-3d maxAngleErr=%5.1f° %s".format(
            r.model.style, r.oraxenId, if (r.designed) "design" else "converted", r.elements.size, r.skipped.size, r.maxAngleError,
            if (r.needsManualDecision) "NEEDS DECISION" else "ok"))
    }
    println("wrote ${output.size} furniture style(s), ${retired.size} retired, ${notOutput.size} left out, ${newPieces.size} new (Oraxen ${format.label} syntax) → ${outDir.path}")
}

private fun fail(msg: String): Nothing {
    System.err.println("error: $msg")
    exitProcess(1)
}

internal fun parseArgs(args: Array<String>): Map<String, String>? {
    val out = linkedMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (!a.startsWith("--") || i + 1 >= args.size) return null
        out[a.removePrefix("--")] = args[i + 1]
        i += 2
    }
    return out
}
