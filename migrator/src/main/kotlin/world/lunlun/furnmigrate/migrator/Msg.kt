package world.lunlun.furnmigrate.migrator

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

/** Player and console messages (Traditional Chinese MiniMessage). */
object Msg {
    private val mm = MiniMessage.miniMessage()
    private const val PREFIX = "<gradient:#f6a6c1:#9ad0f5>[FurnMigrate]</gradient> "

    /** Values are inserted as plain text; a [Component] value keeps its own formatting. */
    fun render(text: String, vararg args: Pair<String, Any>): Component =
        mm.deserialize(PREFIX + text, *args.map { (k, v) ->
            if (v is Component) Placeholder.component(k, v) else Placeholder.unparsed(k, v.toString())
        }.toTypedArray())

    fun mini(text: String): Component = mm.deserialize(text)

    fun send(to: CommandSender, text: String, vararg args: Pair<String, Any>) = to.sendMessage(render(text, *args))

    /** To the console and to everyone with furnmigrate.admin. */
    fun broadcast(text: String, vararg args: Pair<String, Any>) {
        val c = render(text, *args)
        Bukkit.getConsoleSender().sendMessage(c)
        for (p in Bukkit.getOnlinePlayers()) if (p.hasPermission("furnmigrate.admin")) p.sendMessage(c)
    }

    const val NO_MAPPING = "<red>(；´д｀) 找不到 <yellow>plugins/FurnMigrate/mapping.yml</yellow>，請先把 converter 產生的 out/mapping.yml 放進來"
    const val MAPPING_ERROR = "<red>(；´д｀) mapping.yml 讀取失敗：<white><error></white>"
    const val MAPPING_LOADED = "<green>(๑•̀ㅂ•́)و✧ 已讀取對照表：<yellow><n></yellow> 個樣式"
    const val BUSY = "<yellow>(・_・;) 已經有一個 <white><mode></white> 在跑了，先用 <white>/furnmigrate status</white> 看看吧"
    const val IDS_MISSING = "<red>(╥﹏╥) Oraxen 裡找不到這些傢俱：<white><ids></white><newline><red>   請確認 items 已複製、mechanics.yml 的 furniture.enabled 是 true，再 /oraxen reload all"

    const val SCAN_HEAD = "<aqua>(｡•̀ᴗ-)✧ 掃描結果：共 <yellow><total></yellow> 個 DiceFurniture"
    const val SCAN_LINE = "<gray>  <world> <white><style></white> × <yellow><n></yellow> → <action>"
    const val SCAN_UNMAPPED = "<gold>(・へ・) 沒有對照的樣式（會跳過、保留原樣）：<white><styles></white>"
    const val SCAN_FILE = "<gray>  完整清單：<white><file></white>"

    const val RUN_WARN = "<gold><bold>(⊙_⊙;) 注意！</bold><yellow>這會把 <white><total></white> 個舊傢俱換成 Oraxen 傢俱，<red>會改動世界</red>。<newline>" +
        "<yellow>  1. 先確認這是<white>測試伺服器</white>，或已經在維護時間<newline>" +
        "<yellow>  2. 先<white>備份世界和資料庫</white>（快照）<newline>" +
        "<yellow>  3. 建議先跑過 <white>/furnmigrate dry-run</white><newline>" +
        "<green>  確定的話，<white>30 秒內</white>再輸入一次 <white>/furnmigrate run</white>"
    const val RUN_START = "<green>(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧ 開始遷移！共 <yellow><chunks></yellow> 個區塊；journal：<white><file></white>"
    const val DRY_START = "<aqua>(・ω・) dry-run 開始，不會改動任何東西；共 <yellow><chunks></yellow> 個區塊"
    const val PROGRESS = "<gray>  ⋯ 區塊 <white><done></white>/<white><total></white>，已轉換 <yellow><ok></yellow>，需手動 <red><manual></red>，跳過 <gold><skipped></gold>"
    const val RUN_DONE = "<green>(๑•̀ㅂ•́)و✧ 遷移完成！已轉換 <yellow><ok></yellow> 個傢俱，退役 <yellow><retired></yellow>，需手動 <red><manual></red>，跳過 <gold><skipped></gold>"
    const val DRY_DONE = "<aqua>(๑•̀ㅂ•́)و✧ dry-run 完成：可轉換 <yellow><ok></yellow>，退役 <yellow><retired></yellow>，預計需手動 <red><manual></red>，跳過 <gold><skipped></gold><newline><gray>  報告：<white><file></white>"
    const val ORPHANS = "<gold>(・へ・) 找到 <yellow><n></yellow> 個不屬於任何傢俱的 barrier（只列出、不刪除）：<white><file></white>"
    const val NEEDS_MANUAL = "<red>(；ﾟДﾟ) 需要手動處理：<white><style></white> @ <white><where></white> — <reason>"

    const val PAUSED = "<yellow>(￣o￣) . z Z 已暫停，用 <white>/furnmigrate resume</white> 繼續"
    const val RESUMED = "<green>(ง •̀_•́)ง 繼續遷移！"
    const val NOT_RUNNING = "<gray>(・_・) 現在沒有在跑的遷移"
    const val NOTHING_TO_RESUME = "<gray>(・_・) 沒有可以繼續的遷移"
    const val UNFINISHED = "<gold>(・o・) 上次的遷移還沒做完（<white><done></white>/<white><total></white> 區塊），用 <white>/furnmigrate resume</white> 繼續"
    const val INTERRUPTED = "<red>(；ﾟДﾟ) 有 <yellow><n></yellow> 個傢俱在處理中被中斷，已記為 NEEDS_MANUAL；用 <white>/furnmigrate manual</white> 查看"
    const val STATUS = "<aqua>(・∀・) 狀態：<white><mode></white> <state><newline>" +
        "<gray>  區塊 <white><done></white>/<white><total></white>，已轉換 <yellow><ok></yellow>，退役 <yellow><retired></yellow>，需手動 <red><manual></red>，跳過 <gold><skipped></gold><newline>" +
        "<gray>  journal：<white><file></white>"

    const val MANUAL_HEAD = "<red>(・へ・) 需要手動處理的傢俱（<yellow><n></yellow>）："
    const val MANUAL_LINE = "<gray>  <white><id></white> <style> @ <where> — <reason> <items>"
    const val MANUAL_NONE = "<green>(๑˃ᴗ˂)ﻭ 沒有需要手動處理的傢俱"
    const val DROPPED = "<green>(｡•̀ᴗ-)✧ 已把 <yellow><n></yellow> 個物品掉在 <white><where></white>"
    const val DROP_UNKNOWN = "<red>(・_・;) journal 裡找不到可以掉出的物品：<white><id></white>（可能已經掉過了）"

    const val USAGE = "<aqua>(・ω・)ノ 指令：<newline>" +
        "<white>  /furnmigrate scan</white> <gray>— 統計各樣式數量<newline>" +
        "<white>  /furnmigrate dry-run</white> <gray>— 模擬一遍，不改動世界<newline>" +
        "<white>  /furnmigrate run</white> <gray>— 真的開始遷移（30 秒內輸入兩次確認）<newline>" +
        "<white>  /furnmigrate status | pause | resume</white><newline>" +
        "<white>  /furnmigrate manual</white> <gray>— 列出需要手動處理的傢俱<newline>" +
        "<white>  /furnmigrate dropitems \\<id></white> <gray>— 把 journal 裡記下的物品掉回原地"
}
