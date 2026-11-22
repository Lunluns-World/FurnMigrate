package world.lunlun.furnmigrate.converter

import world.lunlun.furnmigrate.common.oraxenIdFor
import world.lunlun.furnmigrate.converter.designs.Designs
import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """converter --models <FurnitureLib models folder> --out <out dir> [--only <style,...>]
          [--vanilla <client jar or extracted folder>] [--namespace lunworld] [--all true]
  Default: only styles with a hand-made design are written (plus retired styles in mapping.yml).
  --all true also writes the automatic conversion of every other style (for reference)."""

fun main(args: Array<String>) {
    val opts = parseArgs(args) ?: run { System.err.println("usage: $USAGE"); exitProcess(2) }
    val modelsDir = File(opts["models"] ?: fail("--models is required"))
    val outDir = File(opts["out"] ?: fail("--out is required"))
    val vanilla = File(opts["vanilla"] ?: "vanilla/client-1.21.8.jar")
    val namespace = opts["namespace"] ?: "lunworld"
    val all = opts["all"]?.toBoolean() ?: false
    val only = opts["only"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()

    if (!modelsDir.isDirectory) fail("models folder not found: $modelsDir")
    if (!vanilla.exists()) fail("vanilla assets not found: $vanilla")

    val files = modelsDir.listFiles { f -> f.isFile && (f.name.endsWith(".dModel") || f.name.endsWith(".yml")) }.orEmpty().sortedBy { it.name }
    val models = files.map { DModelParser.parse(it) }
    val selected = if (only == null) models else models.filter { m -> only.any { it.equals(m.style, true) } }
    only?.filter { o -> models.none { it.style.equals(o, true) } }?.let { if (it.isNotEmpty()) fail("unknown style(s): ${it.joinToString()}") }

    val rig = Rig()
    val results = VanillaAssets.open(vanilla).use { assets ->
        val conv = StyleConverter(assets, rig)
        selected.map { m ->
            val converted = conv.convert(m, oraxenIdFor(m.style))
            Designs.find(m.style)?.let { DesignApplier.apply(converted, it) } ?: converted
        }
    }
    val retired = Designs.retired.filterKeys { s -> selected.any { it.style == s } }
    val output = results.filter { (it.designed || all) && it.model.style !in retired }
    val notOutput = results.filter { it !in output && it.model.style !in retired }.map { it.model.style }
    Output.writeAll(outDir, namespace, output, rig, retired, notOutput)

    // Crafting recipes of the generated furniture (DiceFurniture `crafting:` → Oraxen shaped.yml entries).
    val fileOf = files.associateBy { f -> models[files.indexOf(f)].style }
    val dice = output.mapNotNull { r -> fileOf[r.model.style]?.let { Recipes.read(it) }?.let { it to r.oraxenId } }
    val recipes = dice.mapNotNull { (d, id) -> Recipes.toOraxen(d, id) }
    val recipeFile = File(outDir, "oraxen/recipes/dicefurniture-shaped.yml").apply { parentFile.mkdirs() }
    recipeFile.writeText(Recipes.toYaml(recipes))
    val noRecipe = output.map { it.model.style }.filter { s -> recipes.none { r -> r.oraxenId == oraxenIdFor(s) } }
    val dup = Recipes.duplicates(recipes)
    File(outDir, "report.md").appendText(buildString {
        appendLine(); appendLine("## 合成表"); appendLine()
        appendLine("- 輸出 ${recipes.size} 個合成表到 `oraxen/recipes/dicefurniture-shaped.yml`（要附加到 Oraxen 的 `recipes/shaped.yml`）。")
        if (noRecipe.isNotEmpty()) appendLine("- 沒有合成表（DiceFurniture 原本就停用）：${noRecipe.joinToString()}")
        if (dup.isNotEmpty()) appendLine("- ⚠️ 排列完全相同的合成表：${dup.joinToString("；") { it.joinToString(" = ") }}")
    })
    println("wrote ${recipes.size} recipe(s); without recipe: ${noRecipe.joinToString().ifEmpty { "-" }}; identical: ${dup.ifEmpty { "-" }}")
    for (r in output) {
        println("%-16s → %-18s %-9s elements=%-4d skipped=%-3d maxAngleErr=%5.1f° %s".format(
            r.model.style, r.oraxenId, if (r.designed) "design" else "converted", r.elements.size, r.skipped.size, r.maxAngleError,
            if (r.needsManualDecision) "NEEDS DECISION" else "ok"))
    }
    println("wrote ${output.size} furniture style(s), ${retired.size} retired, ${notOutput.size} left out → ${outDir.path}")
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
