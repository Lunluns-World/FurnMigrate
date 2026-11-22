package world.lunlun.furnmigrate.converter

object Report {
    fun render(results: List<StyleResult>, rig: Rig, retired: Map<String, String> = emptyMap(), notOutput: List<String> = emptyList()): String = buildString {
        appendLine("# 轉換報告（converter report）")
        appendLine()
        if (retired.isNotEmpty()) {
            appendLine("**退役（不做 Oraxen 傢俱）**：" + retired.entries.joinToString("、") { "${it.key} → 留下 `${it.value}`" } + "。遷移時，放在上面的玩家物品會掉在原地。")
            appendLine()
        }
        if (notOutput.isNotEmpty()) {
            appendLine("**沒有輸出**（沒有放置紀錄，也沒有新設計）：${notOutput.joinToString("、")}。")
            appendLine()
        }
        appendLine("| 樣式 | Oraxen ID | 模型來源 | 大小差距 | 已轉換 | 略過 | 最大角度誤差 | 無對應功能 | 需要人工決定 |")
        appendLine("|---|---|---|---:|---:|---:|---:|---:|:---:|")
        for (r in results) {
            val diff = sizeDiff(r)?.let { "${fmt(it)} px" } ?: "—"
            appendLine("| ${r.model.style} | `${r.oraxenId}` | ${if (r.designed) "新設計" else "自動轉換"} | $diff | ${r.converted.size} | ${r.skipped.size} | ${"%.1f".format(r.maxAngleError)}° | ${r.unmatchedFunctions.size} | ${if (r.needsManualDecision) "⚠️" else "—"} |")
        }
        appendLine()
        appendLine("「大小差距」= 新設計和原本傢俱外框的最大差距（任一邊，16 px = 1 格）；只有新設計才有。")
        appendLine()
        appendLine("「需要人工決定」= 有略過的部件、角度誤差 > 10°，或有 Oraxen 沒有對應的功能。")
        appendLine()
        appendLine("校正值（calibration）：head pivot ${fmt(rig.headPivotY)}、head item lift ${fmt(rig.headItemLift)}、scale ${fmt(rig.headItemScale)}、model yaw ${fmt(rig.modelYaw)}°。")
        for (r in results) {
            appendLine()
            appendLine("## ${r.model.style} → `${r.oraxenId}`")
            appendLine()
            appendLine("- 部件：${r.model.entities.size} 個 entity、${r.model.blocks.size} 個 blockList 方塊；輸出 ${r.elements.size} 個 element、${r.textures.size} 張貼圖。")
            if (r.designed) {
                val a = Bounds.of(r.referenceElements); val b = Bounds.of(r.elements.let { els ->
                    if (r.displayScale == 1.0) els else els.map { e ->
                        val c = world.lunlun.furnmigrate.common.Vec3(8.0, 8.0, 8.0); val k = r.displayScale
                        e.copy(from = (e.from - c) * k + c, to = (e.to - c) * k + c)
                    }
                })
                appendLine("- 模型：**新設計**（`designs/Designs.kt`）。原本的外框 ${box(a)}，新設計 ${box(b)}（單位 px）。")
                appendLine("- 下面「已轉換／略過／角度誤差」是原本盔甲架的分析，只作參考；座位、光源、barrier 仍沿用原本的位置。")
            }
            appendLine("- Barrier：${if (r.barriers.isEmpty()) "無（用 hitbox `${r.hitbox}`）" else r.barriers.joinToString { "(${it.x},${it.y},${it.z})" }}")
            if (r.seats.isNotEmpty()) appendLine("- 座位：${r.seats.joinToString { "`$it`" }}")
            if (r.lights.isNotEmpty()) appendLine("- 光源：${r.lights.joinToString { "`$it`" }}")
            if (r.keepBlocks.isNotEmpty()) appendLine("- 保留的真實方塊：${r.keepBlocks.joinToString { "`${it.blockId.removePrefix("minecraft:")}` (${it.cell.x},${it.cell.y},${it.cell.z})" }}（遷移時先記錄、再放回原位，功能和內容都保留；新放的傢俱不會附帶）")
            r.storage?.let { appendLine("- 儲物：Oraxen storage ${it.rows} 排（「${it.title}」），遷移時把原本放著的物品搬進去。") }
            if (r.displayScale != 1.0) appendLine("- 顯示縮放：×${fmt(r.displayScale)}（模型超過 3×3×3 格）")
            section("已轉換", r.converted.map { "${it.part}：${it.what}" })
            section("略過的部件", r.skipped.map { "${it.part}：${it.what}" })
            section("角度誤差（對齊到 22.5° 後）", r.angleErrors.sortedByDescending { it.errorDeg }
                .map { "${it.part}：${"%.1f".format(it.errorDeg)}°${if (it.errorDeg > 10) " ⚠️" else ""}" })
            section("沒有對應 Oraxen mechanic 的功能", r.unmatchedFunctions)
            section("警告", r.warnings)
        }
    }

    /** Largest bounding-box difference between the design (true size) and the original, in px. */
    fun sizeDiff(r: StyleResult): Double? {
        if (!r.designed) return null
        val a = Bounds.of(r.referenceElements) ?: return null
        val k = r.displayScale
        val c = world.lunlun.furnmigrate.common.Vec3(8.0, 8.0, 8.0)
        val b = Bounds.of(r.elements.map { e -> e.copy(from = (e.from - c) * k + c, to = (e.to - c) * k + c) }) ?: return null
        return boundsDifference(a, b)
    }

    private fun box(b: Bounds?): String =
        if (b == null) "（無）" else "(${fmt(b.min.x)}, ${fmt(b.min.y)}, ${fmt(b.min.z)})–(${fmt(b.max.x)}, ${fmt(b.max.y)}, ${fmt(b.max.z)})"

    private fun StringBuilder.section(title: String, lines: List<String>) {
        if (lines.isEmpty()) return
        appendLine()
        appendLine("### $title")
        lines.forEach { appendLine("- $it") }
    }
}
