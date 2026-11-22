# FurnMigrate

把 Lunlun's World 上的 **DiceFurniture（FurnitureLib）傢俱**換成 **Oraxen 傢俱**，換完之後就能移除 FurnitureLib 和 ProtocolLib。

## 專案結構

| 模組 | 用途 |
|---|---|
| `common` | 對照表（`mapping.yml`）的讀寫，以及所有純邏輯：座標旋轉、遷移計畫、玩家物品判斷、journal 復原（含單元測試） |
| `converter` | 離線工具：從 DiceFurniture 模型產生 Oraxen 傢俱設定、模型 JSON、對照表和報告。模型是重新手工設計的，大小和原本差不多（`converter/.../designs/`） |
| `migrator` | Paper 外掛 **FurnMigrate**：在伺服器上把已放置的舊傢俱一個一個換成 Oraxen 傢俱 |
| `testkit` | ⚠️ **只在本機測試用**的外掛 FurnTestKit（`ftk spawn` / `verify` / `audit`）。**不要裝到任何伺服器上** |

另一個 repo **[FurnGuard](https://github.com/Lunluns-World/FurnGuard)** 會一直留在伺服器上：它阻止舊的 DiceFurniture 物品（其實是原版生怪蛋）被當成生怪蛋使用，並把它們換成新傢俱。

文件：

- [docs/findings.md](docs/findings.md)：研究筆記。所有 FurnitureLib、Oraxen 行為的出處，以及測試發現的問題。
- [docs/phase1-install.md](docs/phase1-install.md)：在測試伺服器上安裝 Oraxen 傢俱。
- [docs/phase3-test.md](docs/phase3-test.md)：migrator 的指令和測試步驟。

## 建置

需要 Java 21 以上。FurnitureLib 沒有公開的 Maven 套件，所以 `libs/` 裡要有伺服器正在用的 jar：`FurnitureLib-3.2.9.2.jar`、`ProtocolLib.jar`（只在編譯時使用，不會打包進去）。

以下這些都**不放進 git**（見 `.gitignore`），要自己從伺服器或本機準備：

| 路徑 | 內容 |
|---|---|
| `libs/` | `FurnitureLib-3.2.9.2.jar`、`ProtocolLib.jar`、`DiceFurniture-3.9.4.jar`、`oraxen-1.220.1.jar` |
| `FurnitureLib/models/` | 從伺服器的 `plugins/FurnitureLib/models/` 複製（converter 用）。裡面有玩家 UUID，而且是 DiceFurniture 的內容，所以不放進 git |
| `vanilla/client-1.21.8.jar` | Minecraft 1.21.8 客戶端 jar（converter 讀原版貼圖用） |
| `dumps/` | 資料庫 dump（研究和本機測試用） |

```bash
./gradlew build
```

| 產出 | 位置 |
|---|---|
| migrator 外掛 | `migrator/build/libs/FurnMigrate-0.1.0.jar` |
| 測試外掛（只在本機用） | `testkit/build/libs/FurnTestKit-0.1.0.jar` |

### 產生 Oraxen 設定和對照表

```bash
./gradlew :converter:run --args="--models FurnitureLib/models --out out"
```

| 輸出 | 用途 |
|---|---|
| `out/oraxen/items/dicefurniture.yml` | Oraxen 傢俱設定 |
| `out/oraxen/pack/assets/lunworld/` | 模型（namespace `lunworld`） |
| `out/mapping.yml` | 對照表，放到 `plugins/FurnMigrate/mapping.yml` |
| `out/oraxen/recipes/dicefurniture-shaped.yml` | 合成表，要附加到 Oraxen 的 `recipes/shaped.yml` |
| `out/report.md` | 每個樣式的轉換結果和取捨 |

會讀取 `vanilla/client-1.21.8.jar` 的原版貼圖和模型（只在本機用，不會 commit）。

## migrator 怎麼保證不出事

- **先記錄，再移除，最後放新的**。每個傢俱動手前，都先把完整資料寫進 `plugins/FurnMigrate/journal/<時間>.jsonl`（每行都 fsync）：位置、朝向、樣式、Oraxen ID、玩家物品（Paper 物品序列化）、舊方塊、保留方塊和箱子內容。
- **物品不會消失**：
  - 武器架、垃圾桶的物品放進 Oraxen 儲物；
  - 桌上的東西和花盆裡的植物掉在原地，而且掉落物永遠不會消失；
  - FurnitureLib 讀不了的舊格式物品，會從資料庫讀出來，經過 DataFixer 升級；
  - 如果還是讀不了，那個傢俱就整個不動，交給管理員處理。
- **工作台、床、箱子保持是真的方塊**：移除前先從 FurnitureLib 的清單裡拿掉，所以完全不會被動到，箱子裡的東西也不用搬。
- **當機也安全**：每個區塊依序執行「存世界（等資料寫進硬碟）→ journal 寫 `COMMIT` → 刪舊的資料庫紀錄 → 更新進度」。不論在哪一步當機，`/furnmigrate resume` 都能接著做，不會遺失也不會重複。
- **改動世界的指令都有 dry-run**，`run` 要在 30 秒內輸入兩次才會開始。
- 除了被替換的舊傢俱，什麼都不會自動刪除。孤兒 barrier 只會列出來。

這些都在本機用真實地形和真實資料驗證過：
- 285 個傢俱，1831 項檢查全部通過；
- 另外還測了跑到一半硬關機、存檔後刪資料庫前當機這兩種情況。

詳見 [docs/findings.md](docs/findings.md) §12。

---

## 正式上線檢查清單

> ⚠️ **正式伺服器只照這份清單操作。**第一次一定先在測試伺服器（正式世界和資料庫的**複本**）上完整跑一遍。

### A. 事前準備（不用停機）

- [ ] 在測試伺服器上照 [docs/phase3-test.md](docs/phase3-test.md) 跑過一次，dry-run 和正式跑的結果都沒問題。
- [ ] 重新建置，確認這三個檔案是同一次建置產生的：
  - `migrator/build/libs/FurnMigrate-0.1.0.jar`
  - `out/oraxen/items/dicefurniture.yml`、`out/oraxen/pack/assets/lunworld/`
  - `out/mapping.yml`
- [ ] FurnGuard 已經建置好，並且在測試伺服器上測過（它的設定、安裝方式見 [FurnGuard 的 README](https://github.com/Lunluns-World/FurnGuard#readme)）。
- [ ] 公告維護時間。

### B. Oraxen 設定（共用同一份資源包的每台伺服器都要做，而且**完全一樣**，包括沒有 DiceFurniture 的伺服器）

- [ ] `plugins/Oraxen/mechanics.yml` 裡 `furniture.enabled: true`。這個值是 `false` 時 Oraxen 不會有任何警告，傢俱只是放不下去。
- [ ] 複製 `out/oraxen/items/dicefurniture.yml` → `plugins/Oraxen/items/`。
- [ ] 複製 `out/oraxen/pack/assets/lunworld/` → `plugins/Oraxen/pack/assets/lunworld/`。
- [ ] 如果有多台伺服器共用資源包，Oraxen 的混淆要維持**關閉**（開啟的話，每台的資源包和物品會不一樣）。
- [ ] 確認每台的 Oraxen 檔案完全相同，每台算出來的值要一樣：

```bash
cd plugins/Oraxen && find items pack/assets pack/uploads mechanics.yml -type f -print0 | sort -z | xargs -0 sha256sum | sha256sum
```

- [ ] **合成表**：把 `out/oraxen/recipes/dicefurniture-shaped.yml`（上傳包裡的 `4-dicefurniture-recipes.yml`）的內容**附加到** `plugins/Oraxen/recipes/shaped.yml` 的最後面。不要覆蓋原檔，也不要另外放一個檔案：Oraxen 只讀固定的檔名（`RecipesManager.java:166`）。所有合成表的 key 都以 `dice_` 開頭，不會和既有的撞名。
  - FurnitureLib 還在的時候，它自己同樣排列的合成表會先被選中，做出來的還是舊的生怪蛋。移除 FurnitureLib 之後才會做出 Oraxen 傢俱（本機測過：26 個全部正確，沒有和原版或既有的合成表衝突）。
- [ ] `/oraxen reload all`，然後用 `/oraxen give <自己> dice_chair` 拿一張椅子放放看，確認放得下去、有模型、拆得掉。

### C. 遷移（每台有 DiceFurniture 的伺服器各做一次）

1. [ ] 維護模式：玩家都離開、不能進來。
2. [ ] **備份**：世界和 FurnitureLib 的資料庫都要（建議用快照）。
3. [ ] 把 `FurnMigrate-0.1.0.jar` 放進 `plugins/`，`out/mapping.yml` 放到 `plugins/FurnMigrate/mapping.yml`，然後重開伺服器。
   - 需要 FurnitureLib、ProtocolLib、DiceFurniture、Oraxen 都還在。
   - Kotlin 標準函式庫會在第一次啟動時自動下載（`plugin.yml` 的 `libraries:`）。
4. [ ] `/furnmigrate scan`：數量要和資料庫裡的一致，沒有「Oraxen 裡找不到」的錯誤。
5. [ ] `/furnmigrate dry-run`，打開 `plugins/FurnMigrate/reports/dryrun-*.txt`：
   - 用真實資料測試時是 **MANUAL 0、SKIP 0**。如果有 MANUAL，先看原因（通常是新傢俱的位置被方塊擋住）。
   - `rescued=` 是從資料庫救回的物品，`keep=` 是保留的真實方塊數量。
6. [ ] `/furnmigrate run`，30 秒內再輸入一次。可以用 `/furnmigrate status` 看進度，幾百個傢俱大約一分鐘就跑完。
7. [ ] `/furnmigrate manual`：有需要手動處理的就逐一去看。物品可以用 `/furnmigrate dropitems <id>` 掉回原地。
8. [ ] 抽查幾個傢俱：帳篷（工作台、床、箱子）、武器架（右鍵打開，武器在裡面）、桌子、路燈、沙發（坐坐看）。
9. [ ] 保存 `plugins/FurnMigrate/journal/` 和 `reports/`（之後有問題時的唯一依據）。
10. [ ] 重開一次，確認舊傢俱沒有復活。資料庫裡 `SELECT COUNT(*) FROM furnitureLibData;` 應該是 0（或只剩被 SKIPPED 的那幾筆）。

出問題時：在 `run` 之前可以隨時停下來，什麼都不會改動。跑到一半可以 `/furnmigrate pause`，或直接停機。要整個復原的話，就還原第 2 步的快照（世界和資料庫一起還原）。

### D. 移除舊外掛（每台都遷移完成並確認之後）

1. [ ] 每台伺服器都裝上 **FurnGuard**，`config.yml` 要完全一樣（沒有 DiceFurniture 的伺服器也要裝，玩家可能把舊的生怪蛋帶過去）。
2. [ ] 移除 `DiceFurniture`、`FurnitureLib`、`ProtocolLib`、`FurnMigrate`（FurnMigrate 只在遷移時需要）。
   - 如果有其他外掛依賴 ProtocolLib，先確認過再移除。
3. [ ] 重開，確認啟動時沒有錯誤；拿一個舊的傢俱生怪蛋試試，應該被擋下並換成新傢俱。
4. [ ] `furnitureLibData` 資料表和 `plugins/FurnitureLib/` 可以先留著當紀錄，確定都沒問題之後再刪。
5. [ ] 之後才升級伺服器版本（升級前同樣先備份）。

---

## 已知限制

| 項目 | 說明 |
|---|---|
| 新模型 | 每個樣式都是重新設計的模型，大小和原本差不多，但不是一模一樣（每個樣式的「大小差距」見 `out/report.md`） |
| 退役的樣式 | **Barrels** 只留下鍋釜，**Fence** 只留下柵欄，**Campfire1** 變成原版營火，這三種不再是 Oraxen 傢俱 |
| 帳篷 | 工作台、床、箱子是真的方塊，遷移時會保留下來；但玩家之後**自己新放的帳篷**不附帶這些方塊（合成表沿用原本的，仍然要用到工作台和床）。放置和拆除都要對準中間的木棒 |
| 合成表 | 沿用 DiceFurniture 原本的 26 個合成表。SnowGolem 原本就停用，所以不能合成；退役的 3 種不再有合成表 |
| 武器架 | 改成 1 排的儲物 GUI（右鍵打開），武器**不會顯示**在架子上 |
| 桌子、黑板、大桌子 | 不能再放東西展示；遷移時桌上原本的東西會掉在旁邊 |
| 垃圾桶 | 1 排的儲物 GUI |
| 路燈 | 一直亮著（亮度 15）。原本的開關燈要靠 LightAPI，Oraxen 沒有對應功能 |
| 沙發 | 不保留染色，一律是白色 |
| 弩（Crossbow） | 純裝飾，不能射箭 |
| 擁有者 | Oraxen 傢俱不記錄擁有者，要靠領地外掛保護 |
| 沒放置過的 13 種樣式 | 沒有做 Oraxen 傢俱（AdventCalender、BearTrap、Billboard、Camera、CandyCane、Catapult、ChristmasTree、FireworkLauncher、Flag、GraveStone、Guillotine、Log、SleepingBag）。它們的舊物品由 FurnGuard 擋住 |
| Oraxen 的警告 | 啟動時的「invalid texture-path minecraft:block/...」可以忽略：模型用的是原版貼圖，Oraxen 只檢查自己資源包裡的貼圖 |
| hitbox | 傢俱的 hitbox 都以本體為中心（Oraxen 偏移 hitbox 的旋轉方向和 barrier 相反）；比較大的傢俱靠 barrier 擋人 |

## 本機測試（開發用）

- `run/`、`run-s1/`、`run-s2/` 是本機測試伺服器，只綁 `127.0.0.1`，已經被 `.gitignore` 忽略。
- `test-regions/` 放從正式伺服器複製來的 region 檔，也被忽略。
- 用 `testkit` 的 `ftk spawn` / `ftk verify`（30 種樣式 × 4 個方向）和 `ftk audit`（依照 journal 檢查真實資料）。
- 做法見 [docs/findings.md](docs/findings.md) §12。

單元測試：

```bash
./gradlew test
```
