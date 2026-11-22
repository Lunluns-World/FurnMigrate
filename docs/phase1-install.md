# Phase 1：在測試伺服器上安裝試轉的傢俱

> ⚠️ 只在**測試伺服器**（world 的複製品）上做，不要動正式伺服器。

## 0. 重新產生（需要時）

```bash
./gradlew :converter:run --args="--models FurnitureLib/models --out out --only Chair,Lantern,TV"
```

輸出：

| 檔案 | 用途 |
|---|---|
| `out/oraxen/items/dicefurniture.yml` | Oraxen 物品／傢俱設定 |
| `out/oraxen/pack/assets/lunworld/models/dice/*.json` | 模型 |
| `out/mapping.yml` | 對照表（Phase 3 migrator 用） |
| `out/report.md` | 每個樣式的轉換結果 |

## 1. 前置條件：開啟 Oraxen 的傢俱功能

`plugins/Oraxen/mechanics.yml`：

```yaml
furniture:
  ...
  enabled: true   # 原本是 false
```

這個值是 `false` 時，Oraxen **不會顯示任何警告**，直接略過所有物品的 `mechanics.furniture`（見 `MechanicsManager.java:179`、`ItemMechanics.java:37`）。結果是物品拿得到，但放不下去，`/oraxen admin furniture place` 也會回 `Unknown furniture`。

改完後**重新啟動伺服器**。正式遷移時，所有共用資源包的伺服器都要改成相同的設定。

## 2. 複製到 Oraxen

| 來源 | 複製到測試伺服器的 |
|---|---|
| `out/oraxen/items/dicefurniture.yml` | `plugins/Oraxen/items/dicefurniture.yml` |
| `out/oraxen/pack/assets/lunworld/` | `plugins/Oraxen/pack/assets/lunworld/`（整個資料夾） |

- 模型只用到原版貼圖（`minecraft:block/...`），**沒有新增任何 png**。
- `mapping.yml` 和 `report.md` 不用放進伺服器。

## 3. 重新載入

在控制台或遊戲內執行：

```
/oraxen reload all
```

- Oraxen 會重新產生資源包，並依照 `settings.yml` 的設定上傳。
- 已經在線上的玩家要重新接收資源包：重新登入，或執行 `/oraxen pack send <玩家>`。
- 看一下控制台有沒有 `dice_chair`、`dice_lantern`、`dice_tv` 的錯誤訊息（例如 `Invalid ... for furniture`）。

## 4. 拿到並放置傢俱

```
/oraxen give <你的名字> dice_chair
/oraxen give <你的名字> dice_lantern
/oraxen give <你的名字> dice_tv
```

也可以用 `/oraxen inv` 從物品列表裡拿。拿到後對著地板右鍵放置（只能放在地板上）。

## 5. 請幫我檢查這幾項

最好在舊的 DiceFurniture 傢俱旁邊放一個新的，兩個一起比較。

1. **高度和大小**：
   - 椅腳是不是剛好碰到地板？
   - 燈籠的黑曜石底座在原版就有一半埋在地板裡，新的應該一樣。
   - 電視的高度和寬度是否一致？
2. **外觀細節**：貼圖方向（例如地板門的木紋）、拉桿做的腳是否正常、有沒有多出來或少掉的部分。
3. **朝向**：新傢俱的正面是否朝著放置時你看的方向？如果轉了 90° 或 180°，請告訴我轉了多少。
   - 在 Phase 3 遷移時，朝向會由程式計算，另外驗證。
4. **椅子**：右鍵坐下時，高度和原本差不多嗎？
5. **燈籠**：周圍有沒有亮（亮度 14，和火把一樣）？
6. **拆除**：打掉後有沒有掉回對應的物品？

方便的話，截圖給我最快（新舊並排、正面和側面各一張）。

## 已知限制（試轉版本）

- **手持物品**（棍子、骨頭等）、**玩家頭顱**、**旗幟**這次還沒轉換；試轉的三個樣式都沒用到它們。
- **Hitbox**：沒有 barrier 的傢俱，會用一個置中的 interaction 方塊當作點擊範圍。
- **原版物品轉換的細節**：
  - 原版拉桿這類「平面物品」，換成逐像素擠出的 1 px 厚模型，和原版物品的作法相同。
  - 盔甲架頭上物品的位置，用的是原版渲染的常數（`converter/.../ArmorStandRig.kt` 的 `Rig`）。如果你看到高度或大小有固定的偏差，只要改那裡就能全部一起修正。
