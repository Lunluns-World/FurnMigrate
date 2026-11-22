# Phase 3 測試指南：migrator 外掛

> ⚠️ **只在測試伺服器上用世界的複本測試。**不要裝到正式伺服器，也不要連正式伺服器的資料庫。
> 測試伺服器上的 FurnitureLib 必須連到**資料庫的複本**：migrator 會刪除 `furnitureLibData` 裡已處理的紀錄。

## 1. 安裝

1. 先**備份**（測試伺服器的世界和資料庫都要，建議用快照），測壞了可以直接還原重來。
2. 把 `migrator/build/libs/FurnMigrate-0.1.0.jar` 放進 `plugins/`。
   - 需要 FurnitureLib、ProtocolLib、Oraxen 都在。
   - Kotlin 標準函式庫會在第一次啟動時由 Paper 自動下載（`plugin.yml` 的 `libraries:`），伺服器要能連上 Maven Central。
3. 啟動一次，產生 `plugins/FurnMigrate/`。
4. 把 converter 產生的檔案放到對應位置：
   - `out/mapping.yml` → `plugins/FurnMigrate/mapping.yml`
   - `out/oraxen/items/dicefurniture.yml` 和 `out/oraxen/pack/assets/lunworld/` → Oraxen（和 Phase 1 一樣），然後 `/oraxen reload all`
5. 確認 Oraxen 的 `mechanics.yml` 裡 `furniture.enabled: true`。

設定檔 `plugins/FurnMigrate/config.yml`：

| 設定 | 預設 | 說明 |
|---|---|---|
| `objects-per-tick` | `2` | 每 tick 處理幾個傢俱（20 tick = 1 秒） |
| `worlds` | `[]` | 只處理這些世界；空的 = 全部 |
| `list-orphan-barriers` | `true` | 列出處理過的區塊中不屬於任何傢俱的 barrier（只列出，不刪除） |

## 2. 指令（權限 `furnmigrate.admin`，預設 OP）

| 指令 | 會改動世界？ | 說明 |
|---|---|---|
| `/furnmigrate scan` | 否 | 統計各世界、各樣式的數量和對應動作；完整清單寫到 `reports/scan-*.txt` |
| `/furnmigrate dry-run` | 否 | 實際載入每個區塊模擬一遍：預測放不放得下、物品會進儲物還是掉出、會保留哪些方塊；報告寫到 `reports/dryrun-*.txt` |
| `/furnmigrate run` | **是** | 第一次只會顯示警告，**30 秒內再輸入一次**才會開始 |
| `/furnmigrate status` | 否 | 進度、各種結果的數量、journal 位置 |
| `/furnmigrate pause` / `resume` | — | 暫停／繼續；伺服器重開後也用 `resume` 從上次的區塊繼續 |
| `/furnmigrate manual` | 否 | 列出所有 NEEDS_MANUAL 的傢俱 |
| `/furnmigrate dropitems <id>` | 掉落物品 | 把 journal 裡記下的物品掉回原地（每筆只能掉一次） |

## 3. 每個傢俱的處理流程

1. **先寫 journal**（`plugins/FurnMigrate/journal/<時間>.jsonl`，每行寫完都會 fsync）：
   世界、座標、朝向、樣式、Oraxen ID、玩家物品（Paper 物品序列化成 base64）、保留方塊的狀態和箱子內容。
2. **移除舊的**：
   - 工作台、床、箱子這類保留方塊會先從 FurnitureLib 的清單裡拿掉，所以完全不會被動到（箱子裡的東西也不用搬）。
   - 其餘的 barrier、火把等方塊會變成空氣。
   - 資料庫紀錄**先保留**，等這個區塊做完、世界存檔之後才刪（見第 7 點）。
3. **放新的**：
   - 放在對照表裡的基準格（帳篷是中間那格），朝向會換算成 Oraxen 的 yaw。
   - TrashCan、WeaponStand 的物品放進 Oraxen 儲物；放不下或沒有儲物的，就掉在原地。
   - 掉出來的物品**永遠不會消失**（unlimited lifetime）。
4. **放不下** → journal 記 `NEEDS_MANUAL`，物品掉在原地，並在聊天室通知。
5. **退役款式**：
   - Barrels、Fence：原本的鍋釜、柵欄保留下來。
   - Campfire1：原位置放一個營火。
6. **沒有對照的樣式** → 記 `SKIPPED`，舊傢俱原樣保留。
7. **每個區塊做完**：存世界（等資料寫進硬碟）→ journal 寫 `COMMIT` → 刪除這個區塊的舊資料庫紀錄 → 更新進度。
   不論在哪一步當機，世界和資料庫都會停在一致的狀態：
   - 還沒存檔：舊傢俱整個回來，resume 時重做。
   - 已經存檔：resume 時只清掉舊紀錄，記為 `CLEANUP`，不會重複放傢俱，也不會重複掉物品。

只有標記部件（`#ITEM`、`#SLOT`、`#TRASH`、`#TELLER`、`#ARROW`）上的東西才算玩家物品。路燈開燈後的螢石這類模型本身的變化不會掉出來（原因見 `docs/findings.md` §12）。

## 4. 測試步驟

> 已經先在本機做過整合測試（`testkit/` 外掛，見 `docs/findings.md` §12 的最後一段）：
> 124 個傢俱、4 個方向、三種當機情境，764 項檢查全部通過。
> 測試伺服器上主要確認的是**真實地形**和**遊戲內的外觀**。
> ⚠️ `testkit/`（FurnTestKit）只在本機測試用，**不要裝到任何伺服器上**。

1. `/furnmigrate scan`：數量應該和 `docs/findings.md` §9 一致（該世界的數字）。
2. `/furnmigrate dry-run`，然後看 `reports/dryrun-*.txt`：
   - `OK` 的那幾行有新傢俱的座標和 yaw，`keep=` 是會保留的方塊數。
   - `MANUAL` 的那幾行是預計放不下的，記下來實際跑完後去看。
   - `orphans-dryrun-*.txt` 是孤兒 barrier 的清單（如果有）。
3. **跑之前先挑幾個傢俱記住位置和朝向**（建議：一張椅子、一張沙發、一個帳篷、一個武器架、一個垃圾桶、一個路燈、一個 Barrels、一個 Campfire1），可以截圖。
4. `/furnmigrate run` → 30 秒內再輸入一次。
5. 跑的途中試一次 `/furnmigrate pause` 和 `/furnmigrate resume`。
6. **重開測試**：再跑一次（先還原快照），跑到一半直接重開伺服器，啟動後應該會提示「上次的遷移還沒做完」，再用 `/furnmigrate resume` 繼續。
7. 跑完後檢查：
   - [ ] 剛才記下的傢俱：位置、朝向和原本一樣（帳篷的工作台、床、箱子還在原位置，箱子裡的東西還在，床可以睡）
   - [ ] 武器架、垃圾桶：右鍵打開，裡面是原本放的東西
   - [ ] 桌子上原本的食物掉在桌子旁邊
   - [ ] Barrels 剩下鍋釜，Fence 剩下柵欄，Campfire1 變成營火
   - [ ] 沒有殘留的 barrier、火把；路燈有亮
   - [ ] `/furnmigrate status` 和 `/furnmigrate manual`：NEEDS_MANUAL 的傢俱逐一去看
   - [ ] 資料庫：`SELECT COUNT(*) FROM furnitureLibData;` 只剩被跳過的那幾筆
   - [ ] 重開伺服器後，舊傢俱沒有復活
8. 回報結果時附上 `reports/` 底下的報告和 journal 檔案。
