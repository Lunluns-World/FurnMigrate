# Phase 0 研究結果（findings）

每項都附來源。「✅」= 已用原始碼或你提供的檔案確認；「⚠️」= 尚待確認（需要的東西列在最後）。

來源版本：
- FurnitureLib 原始碼：<https://github.com/Ste3et/FurnitureLib>，commit `c286a72`（2026-10-12）。你提供的 jar 是 **3.2.9.2**，主要 API 已用 `javap` 對照過（見 §3）。
- DiceFurniture 原始碼：<https://github.com/Ste3et/furniture>（DiceFurniture 3.5.0，最後更新 2021-07）。**沒有你伺服器上的 jar**，Java 行為部分以此為準，待確認。
- Oraxen 原始碼：<https://github.com/oraxen/oraxen>，commit `083a6db`（`gradle.properties` 的 `pluginVersion=1.220.1`），與你提供的 `oraxen-1.220.1.jar` 同版本。
- 以下路徑：`FL/` = `FurnitureLib-Core/src/main/java/de/Ste3et_C0st/FurnitureLib/`，`OX/` = `src/main/java/io/th0rgal/oraxen/`。

---

## 1. 模型檔格式 ✅

**位置**：`plugins/FurnitureLib/models/<名稱>.dModel`（1.13+；舊版是 `Crafting/*.yml`）。
來源：`FL/ModelLoader/ModelFileLoader.java`、`FL/Crafting/CraftingFile.java#getPath`。

DiceFurniture 沒有另外的模型資料夾：它的 43 個模型都放在 `plugins/FurnitureLib/models/`，自己的設定在 `plugins/FurnitureLib/plugin/<功能>/`（例如 `catapult/range.yml`、`fence/whiteList.yml`）。

**檔案是 YAML**，最上層的 key 是 header（通常等於檔名；例外：`Log.dModel` 的 header 是 `log_copy`）：

```yaml
Chair:
  system-ID: Chair            # 寫進物品 PDC furniturelib:model 的 ID（CraftingFile#systemID）
  displayName: '&c椅子'
  spawnMaterial: PIGLIN_BRUTE_SPAWN_EGG
  placeAbleSide: TOP          # TOP / SIDE …（SIDE 會反轉旋轉方向）
  crafting: {recipe: ..., index: {...}}
  projectData:
    entities:                 # 舊版拼法 "entitys" 也接受
      '0': <Base64(GZip(NBT))>
    blockList:
      '0': {xOffset: 0.0, yOffset: 0.0, zOffset: 0.0, material: TORCH}   # 或 blockData: "minecraft:barrier"
    functions: [ '<json>', ... ]   # 選用（例：Sofa 的染色）
```
來源：`FL/ModelLoader/Modelschematic.java#loadEntities/#loadBlockData`、`FL/ModelLoader/Block/ModelBlockAquaticUpdate.java`。

**每個 entity** 是 Base64 編碼、GZip 壓縮的 NBT compound（`NBTCompressedStreamTools.read`）。實際解碼出來的欄位（Chair 第 3 個部件）：

```json
{"EntityType":"ARMOR_STAND","Invisible":1,"Marker":1,"BasePlate":0,
 "EulerAngle":{"HEAD":{"X":1.57,"Y":0.0,"Z":0.0}},
 "Inventory":{"HEAD":{"id":"minecraft:oak_trapdoor","Count":1}},
 "Location":{"X-Offset":0.0,"Y-Offset":-0.65,"Z-Offset":-0.25,"Yaw":0.0,"Pitch":0.0}}
```

| 欄位 | 意義 | 來源 |
|---|---|---|
| `EntityType` | 預設 `armor_stand`；也有 display / interaction 等（我們的 43 個模型**全部是 ARMOR_STAND**） | `Modelschematic#readNBTtag` |
| `Location.{X,Y,Z}-Offset, Yaw, Pitch` | 相對原點的偏移與部件 yaw（度） | `ModelVector(NBTTagCompound)` |
| `EulerAngle.{HEAD,BODY,LEFT_ARM,RIGHT_ARM,…}.{X,Y,Z}` | 姿勢，**弧度** | `fArmorStand#readAdditionalSaveData` |
| `Pose.<part>`（另一種寫法） | 姿勢，若是 list 則為**度**（會轉成弧度） | 同上，`eulerAngleFetcher(double[])` |
| `Inventory.{HEAD,MAINHAND,OFFHAND,CHEST,LEGS,FEET}` | 物品 NBT；`"NONE"` 代表空 | `fContainerEntity#readInventorySaveData` |
| `Small`, `Marker`, `Invisible`, `ShowArms`/`Arms`, `NoBasePlate`/`BasePlate`, `scaleAttribute` | 盔甲架旗標 | `fArmorStand` |
| `Name`/`CustomName` | **功能標記**（見下） | `fEntity#loadMetadata` |

**名稱標記**（`FL/ModelLoader/ModelHandler.java#getEntityMap`、`FL/main/Furniture.java`、`FL/SchematicLoader/*`）：

| 標記 | 功能 |
|---|---|
| `#SITZ#`、`#Mount:<uuid>#` | 座位 |
| `#Light:…:on#/off#` | 開關燈（lightFunction，要搭配 LightAPI） |
| `#Inventory:<名稱>:<格數>#` | 箱子 GUI（ProjectInventory）。**43 個模型都沒用到** |
| `#ITEM#`、`#BLOCK#` | 可以放玩家物品的部件；拆除時掉落 |
| `#DYE_<x>` | 可染色 |
| `/指令` | 點擊執行指令 |
| 其他，例如 `#TELLER1`、`#SLOT1`、`#ARROW`、`#LAMP` | DiceFurniture Java 類別自己定義的標記 |

**旋轉** ✅：放置時原點 = 方塊座標 +(0.5, 0, 0.5)，`direction = yawToFace(yaw).getOppositeFace()`，每個偏移量這樣換算（`Modelschematic#rotateVector`；`placeAbleSide: SIDE` 時 direction 先反轉）：

| direction | 新座標 | 部件 yaw |
|---|---|---|
| NORTH（預設） | ( x, y, z) | +0 |
| SOUTH | (−x, y, −z) | +180 |
| WEST | ( z, y, −x) | +270 |
| EAST | (−z, y, x) | +90 |

`blockList` 的方塊用同一個表換算；`Directional` 方塊的 facing 也會跟著轉（`ModelBlockAquaticUpdate#place`）。

### 你的 43 個模型 ✅
解碼你的 `FurnitureLib/models/*.dModel` 後，和上游的 `Models113/` 比較：**幾何完全相同**，差別只在 displayName、spawnMaterial 這類物品外觀。

---

## 2. 已放置傢俱的儲存格式 ✅

FurnitureLib 的 `storage-type` 可以是 SQLITE 或 Mysql（`FurnitureLib/config.yml`）。以下以 `storage-methode: LEGACY` 為準。

表名 `furnitureLibData`（`FL/Database/Database.java#TABLE_NAME`），欄位：

| 欄位 | 內容 |
|---|---|
| `ObjID` | `<專案名>:<10 位亂數 serial>:<外掛名>`（`ObjectID(String,String,Location)`） |
| `Data` | Base64(GZip(NBT))：`Location{X,Y,Z,Yaw,Pitch}`、`Owner-UUID`、`Members[]`、`EventType`、`PublicMode`、`entities{<entityId>: 同模型的 entity NBT，但 Location 是世界絕對座標}` |
| `world` | 世界名（或 UUID） |
| `x`, `z` | **chunk** 座標（`blockX >> 4`） |
| `uuid` | 擁有者 |

來源：`FL/Database/Database.java#save/#loadQuery`、`FL/Database/Serializer.java`、`FL/Database/DeSerializer.java`。

- `storage-methode: LEGACY`：啟動時用 `Database#loadAll` 把**所有世界的所有物件**讀進記憶體（`FL/Database/SQLManager.java:61`）。因此 migrator 可以直接從記憶體列出全部物件，不必等 chunk 載入。
- 統計各樣式數量時，只要解析 `ObjID` 冒號前的專案名，再依 `world` 分組，**不用解碼 Data**。
- 箱子內容（ProjectInventory）存在 `plugins/FurnitureLib/metadata/<serial>.yml` 的 `inventory` key，格式是 Base64(GZip(NBT{size, inventory{0..n}}))。我們的模型都沒用到，所以不需要這個資料夾。

---

## 3. FurnitureLib API（migrator 用）✅（已對照 3.2.9.2 jar）

| 需求 | API |
|---|---|
| 取得 manager | `FurnitureLib.getInstance().getFurnitureManager()`（`FurnitureManager extends ObjectIdManager`） |
| 列出某世界全部物件 | `getObjectStreamFromWorld(World)` / `getInWorld(World)`；依 chunk 查：`getInChunkByCoord(cx, cz, World)` |
| 位置／朝向 | `ObjectID#getStartLocation()`（含 yaw）、`#getBlockFace()` |
| 樣式名 | `ObjectID#getProject()`（= dModel header／system-ID） |
| 部件與物品 | `ObjectID#getPacketList()` → `fEntity`；`fContainerEntity#getInventory()`（helmet/mainhand/offhand…） |
| 模型方塊位置 | `ObjectID#getBlockList()`（barrier、火把等實體方塊） |
| 移除 | `FurnitureManager#remove(ObjectID)`：設定 `SQLAction.REMOVE`，**把 blockList 每格設成 AIR**（`BlockManager#destroy(list,false)`），並送出銷毀封包。`ObjectID#remove()` 的效果相同（不掉落物品）。 |
| 寫回資料庫 | `remove` 只做標記，真正的 `DELETE` 要等下次 `SQLManager#save()`。**migrator 每批處理完要主動呼叫 save**；否則若在下次存檔前當機，舊傢俱會復活，和新傢俱重疊。 |

來源：`FL/main/ObjectIdManager.java:184`、`FL/main/BlockManager.java:63`、`FL/main/ObjectID.java:291`。

### 「儲存內容」實際上分散在哪裡（重要）
1. **部件手上或頭上的玩家物品**：例如 Barrels（helmet）、LargeTable（盤子）、WeaponStand、Crossbow，以及 `#ITEM#` 部件。判斷方法：拿已放置物件的物品和模型範本逐格比對，不一樣的就是玩家放進去的。
2. **blockList 中的實體容器**：Tent2 有一格 `minecraft:chest`，`remove()` 會直接把它設成 AIR，**箱子裡的東西會消失**。必須在移除前先讀出內容並寫進 journal。
3. **GraveStone 的告示牌文字**、**Billboard 生成的物品展示框**（真實實體，不是封包）。這些屬於 DiceFurniture 的 Java 行為，要另外處理或列為 NEEDS_MANUAL。⚠️ 需要你的 DiceFurniture jar 確認。

### 燈（見 §8）
FurnitureLib 的燈分兩種：一是透過 LightAPI（`FL/main/LightManager.java`，只有安裝 LightAPI 才有效）；二是 blockList 裡的真實發光方塊（TORCH、REDSTONE_TORCH 等）。我們的模型都屬於第二種。

---

## 4. Oraxen furniture 設定欄位 ✅（`OX/mechanics/provided/gameplay/furniture/FurnitureMechanic.java` 建構子）

```yaml
dice_chair:
  itemname: "<gray>椅子"
  material: PAPER
  Pack: { generate_model: false, model: lunworld:dice/chair }   # ⚠️ 帶 namespace 的寫法待 Phase 1 驗證
  mechanics:
    furniture:
      type: DISPLAY_ENTITY        # ITEM_FRAME / GLOW_ITEM_FRAME / ARMOR_STAND / DISPLAY_ENTITY（預設看 mechanics.yml）
      properties:                  # DisplayEntityProperties
        display_transform: FIXED   # ItemDisplayTransform
        scale: {x: 1, y: 1, z: 1}
        translation: {x: 0, y: 0, z: 0}
        view_range, shadow_radius, shadow_strength, displayWidth, displayHeight,
        tracking_rotation, brightness: {block_light, sky_light}
      barrier: true                # = 原點一格
      barriers: [ origin, {x: 1, y: 0, z: 0}, ... ]   # 整數方塊偏移
      hitboxes: [ "x,y,z width,height", ... ]         # interaction 實體
      seats:    [ "x,y,z", "x,y,z yaw", ... ]
      lights:   [ "x,y,z level", ... ]                # 整數偏移, level 0-15
      light: 15                    # 舊式單一光源（LightMechanic）
      storage: { type: STORAGE|PERSONAL|ENDERCHEST|DISPOSAL|SHULKER, rows: 6, title: "..." , open_sound, close_sound }
      rotatable: true              # 只要有 x/z≠0 的 barrier 就會被強制關閉
      restricted_rotation: STRICT  # 或 VERY_STRICT（placement 時的 8 向/4 向）
      limited_placing: { floor, wall, roof, type, block_types… }
      drop: { silktouch: false, loots: [ {oraxen_item: dice_chair, probability: 1.0} ] }
      breakable, hardness, block_sounds, text_entity(ies), jukebox, blocklocker, stages/evolution
```
- 欄位解析：`FurnitureMechanic.java:183-305`；seat、light、hitbox 字串格式：`:325-436`。
- 範例：`src/main/resources/items/furniture.yml`；預設值：`src/main/resources/mechanics.yml:58`。
- **Barrier 旋轉**：`BlockLocation#groundRotate(yaw)`，`getLocations(yaw, center, barriers)`（`FurnitureMechanic.java:1299`，`BlockLocation.java:100`）。converter 要用 yaw=0（預設朝向）輸出，交給 Oraxen 旋轉。⚠️ 和 FurnitureLib 的 NORTH 是否對齊，Phase 1 會在遊戲內實測。
- **模型大小限制**：Java 模型的 element 座標只能在 −16..32（3×3×3 格）內，而且每個 element 只能沿單一軸旋轉 22.5° 的倍數（CLAUDE.md 已註明）。更大的傢俱要先把模型縮小，再用 `properties.scale` 放大回來。

## 5. Oraxen 放置／移除 API ✅（`OX/api/OraxenFurniture.java`）

```java
@Nullable Entity OraxenFurniture.place(String itemID, Location loc, float yaw, BlockFace face); // :129
boolean OraxenFurniture.remove(Entity baseEntity, @Nullable Player p);                          // :198
FurnitureMechanic OraxenFurniture.getFurnitureMechanic(String itemID);                          // :399
```
- `place` → `FurnitureMechanic#place(loc, item, yaw, face, checkSpace=true)`（`:721`）：
  - **位置被佔用時**：`notEnoughSpace` 會檢查每一格 barrier 是否 `BlockHelpers.isReplaceable`；有任何一格不是就**回傳 null**，不放置也不丟例外（`:1306`）。沒有 barrier 的傢俱不檢查空間。
  - 所以順序一定是「先移除舊的 → 再放新的」；回傳 null 就記 `NEEDS_MANUAL`。
  - 必須在主執行緒呼叫（Javadoc 的 thread contract）。
- **Storage 內容**：存在 base entity 的 PDC，key 是 `oraxen:storage`，型別是 `DataType.ITEM_STACK_ARRAY`（MorePersistentDataTypes，jar 內已 relocate 成 `io.th0rgal.oraxen.shaded.jeff_media.morepersistentdatatypes.DataType`）。來源：`OX/mechanics/provided/gameplay/storage/StorageMechanic.java:46,100,370`。
  - migrator 放好新傢俱後，用同一個 `DataType` 寫入 PDC，就能把東西搬進去；寫完用 `getStorageContents(entity)` 讀回驗證，失敗就把物品掉在原地。
- **Classloader**：Oraxen 是 Paper plugin，但 `paper-plugin.yml` 有 `has-open-classloader: true`，所以一般 plugin.yml 外掛加上 `depend: [Oraxen]` 就能直接用它的類別（已從 jar 確認）。
- **Maven**：`https://repo.oraxen.com/releases`，`io.th0rgal:oraxen:1.220.1`（已確認 maven-metadata 有這版）。

---

## 6. 樣式難度分析（以你的 43 個模型計算）

欄位說明：parts = 頭上和手上有物品的部件數；multiAx = 姿勢同時旋轉兩軸以上的部件數（Java 模型做不到，只能近似）；err>10 = 對齊到 22.5° 後誤差超過 10° 的部件數；pHead = 玩家頭顱（需要皮膚貼圖）；blocks = blockList 格數。

| 類別 | 樣式 |
|---|---|
| **好轉**（只有頭上的方塊、角度都是 90° 的倍數） | Chair, Lantern, TV, Trunk, BearTrap, TrashCan, CactusPlant, Sofa, BlackBoard, WeaponStand, Tent3, Tent2（87 個部件，很大） |
| **中等**（有手持物品或少數角度誤差） | Table, SchoolTable, SchoolChair, CampChair, FireworkLauncher, SleepingBag, Camera, ChristmasTree, Guillotine, GraveStone, Flag, FlowerPot, Hammock, LargeTable, Sunshade, Tent1（12 個部件誤差 >10°） |
| **難**（大量手持物品／多軸旋轉／玩家頭） | Streetlamp, Campfire2, Crossbow, HumanSkeleton, Catapult, SnowGolem, Billboard, MailBox, AdventCalender, CandyCane（10 個玩家頭）, WaxCandle（玩家頭） |
| **幾乎沒有模型，靠實體方塊或 Java** | Barrels（只有一格 cauldron）, Fence, Log, Campfire1（沒有可見部件，視覺效果是 Java 生成的） |

- 「頭上方塊」不一定是方塊：lever、trapdoor、pressure plate、carpet 都是用**原版物品模型**顯示的。converter 必須讀取原版 1.21.8 的 `assets/minecraft/models/block/*.json`，取出它們的 element，套上盔甲架頭部的 display transform 與姿勢後合併。⚠️ 需要原版 client jar 的 assets。
- 有 Java 行為的樣式（DiceFurniture 類別）：AdventCalender、barrels、BearTrap、billboard、camera、campfire、Catapult、Crossbow、fence、FireworkLauncher、flag、graveStone、Guillotine、hammock、largeTable、log、streetlamp、sunshade、tent_1/2/3、TFlowerPot、TrashCan、Trunk、WaxCandle、weaponStand。這些功能大多在 Oraxen 沒有對應的 mechanic，要寫進 report。

---

## 7. 原版資源 ✅

- `vanilla/client-1.21.8.jar` 從 Mojang 官方下載，來源是 `piston-meta.mojang.com` 的 version manifest → `1.21.8.json` → `downloads.client`。SHA-1 `a19d9badbea944a4369fd0059e53bf7286597576` 與官方相符。這個檔案已加進 `.gitignore`，不會 commit。
- 物品外觀的查法：`assets/minecraft/items/<id>.json` → `model` → 沿著 `parent` 往上追，直到找到 `elements`。
  - `oak_trapdoor` → `block/oak_trapdoor_bottom` → `template_trapdoor_bottom`，是一個 16×3×16 的板子。
  - `oak_pressure_plate`、`black_carpet`、`obsidian` 都會指到各自的 block 模型（立體形狀）。
  - **`lever` 不一樣**：`items/lever.json` 指到 `item/lever`，它的 parent 是 `item/generated`，貼圖是 `block/lever`。也就是說，盔甲架頭上的拉桿顯示的是**平面的物品圖示**，不是方塊形狀。converter 要把它做成擠出的薄片（和手持物品同一套做法）。
- `block/block.json` 的 `display` 裡**沒有 `head`**，戴在頭上時用的是遊戲引擎內建的預設轉換。這個數值不用猜：Phase 1 先放一個只有單一部件的測試傢俱，在遊戲內和原本的盔甲架並排比對校正。

## 8. 燈 ✅
沒有 LightAPI 時，FurnitureLib 的 `#Light:` 開關燈沒有作用（Oraxen 也沒有對應功能）。實際的光源只有 blockList 裡的發光方塊（TORCH、REDSTONE_TORCH 等）。轉換時把這些方塊換成 Oraxen `lights: ["x,y,z level"]`，亮度用該方塊在原版的亮度（火把 14、紅石火把 7）。

## 9. 放置數量（Phase 0 結果）✅

來源：伺服器的 `furnitureLibData` 資料表（匯出的 dump）。統計方法是取 ObjID 冒號前的專案名。285 筆 Data 全部都能解碼，外掛名稱都是 `DiceFurniture`，起始 yaw 只有 0、90、180、270 四種。

| 樣式 | 數量 |
|---|---:|
| Chair | 30 |
| TrashCan | 30 |
| Trunk | 31 |
| TV | 28 |
| Sofa | 18 |
| Streetlamp | 17 |
| Table | 15 |
| CampChair | 14 |
| WeaponStand | 13 |
| MailBox | 9 |
| Sunshade | 8 |
| Campfire2 | 8 |
| Campfire1 | 7 |
| LargeTable | 6 |
| Tent1 | 6 |
| Tent3 | 5 |
| Hammock | 5 |
| Lantern | 4 |
| CactusPlant | 4 |
| Crossbow | 4 |
| Fence | 4 |
| Barrels | 3 |
| FlowerPot | 3 |
| SchoolChair | 3 |
| Tent2 | 3 |
| BlackBoard | 2 |
| SchoolTable | 2 |
| HumanSkeleton | 1 |
| WaxCandle | 1 |
| SnowGolem | 1 |
| **合計** | **285** |

- 有 13 個樣式**從來沒有被放置過**：AdventCalender、BearTrap、Billboard、Camera、CandyCane、Catapult、ChristmasTree、FireworkLauncher、Flag、GraveStone、Guillotine、Log、SleepingBag。
- **用真實資料驗證旋轉規則**：取 Chair 在 yaw 0 和 yaw 270 的兩筆紀錄，用 §1 的旋轉表換算 dModel 的偏移量和部件 yaw，結果和資料庫裡存的完全一致。例如 lever (−0.24, −0.49) 在 yaw 270 時變成 (0.49, −0.24)，部件 yaw 180 加 90 變成 270。
- **起始點**：Location 的 X、Z 是方塊中心（`.5`），Y 是整數（方塊底部）。和 converter 假設的「方塊地板中心」一致。

## 10. DiceFurniture 3.9.4 ✅
你提供的 `libs/DiceFurniture-3.9.4.jar` 內附的 `Models113/*.dModel`，解碼後的幾何和你的 `FurnitureLib/models` 完全相同。

## 11. 尚待確認 ⚠️

| 項目 | 需要 |
|---|---|
| DiceFurniture 的 Java 行為（告示牌、展示框、WeaponStand） | 3.9.4 jar 已取得，Phase 2／3 處理到該樣式時再反編譯確認 |
| FurnitureLib 朝向和 Oraxen yaw 的對應、頭部 display 轉換 | Phase 1 在遊戲內實測 |
| `Pack.model` 帶 namespace 的寫法 | Phase 1 在遊戲內實測 |

## 12. Phase 3 migrator 用到的細節 ✅（用 `javap` 對照 `libs/FurnitureLib-3.2.9.2.jar`、`libs/oraxen-1.220.1.jar`）

### FurnitureLib
| 需求 | 實際作法與來源 |
|---|---|
| 列出所有物件 | `FurnitureManager#getObjectList()`，過濾掉 `getSQLAction() == REMOVE` 的物件 |
| 世界 | `ObjectData#getWorld()`；資料庫的 world 欄位可能存的是 UUID，所以區塊鍵一律用 `World#getName()` |
| 部件名稱 | `fEntity#getName()` 會直接回傳 `getCustomName()`。`fEntity#loadMetadata` 先讀 NBT 的 `CustomName`，沒有才讀 `Name`。資料庫裡兩種格式都有（舊的用 `Name`，新的用 `CustomName`） |
| 部件物品 | `fContainerEntity#getInventory().getStackMap()`，key 是 `fInventory$EquipmentSlot`（`MAINHAND`、`OFFHAND`、`FEET`、`LEGS`、`CHEST`、`HEAD`） |
| blockList | `ObjectID#getBlockList()` 直接回傳內部的 `locList` 欄位（bytecode：`getfield locList; areturn`），可以直接修改 |
| 移除 | `ObjectIdManager#remove(ObjectID)`：設定 `SQLAction.REMOVE`，呼叫 `BlockManager#destroy(blockList,false)` 把每格設成 AIR，再從 `BlockManager.locList` 拿掉；接著 `killObject` 送出銷毀封包 |
| 保留真實方塊 | 在 `remove` 之前，把要保留的格子從 `ObjectID#getBlockList()` 和 `BlockManager#getList()` 拿掉。`destroy` 只處理清單上的格子，所以工作台、床、箱子（連同內容物）都不會被動到 |
| 立即刪除資料庫紀錄 | `SQLManager#remove(ObjectID)` 會直接呼叫 `Database#delete`（馬上執行 DELETE），不必等下次存檔。之後再用 `ObjectIdManager#deleteObjectID(ObjectID)` 從記憶體移除。`SQLManager#save()` 自己也是這個流程 |

### 玩家物品（重要）
- **只有專門放物品的標記部件上的東西才算玩家物品**：`#ITEM`（Table、SchoolTable、BlackBoard）、`#SLOT1..3`（WeaponStand）、`#TRASH`（TrashCan）、`#TELLER1..4`（LargeTable）、`#ARROW`（Crossbow）。這些部件在 43 個模型範本裡都是空的。
- **不能用「已放置物品減掉範本」**：用資料庫 dump 驗證過，DiceFurniture 會自己替換模型上的物品。Streetlamp 開燈時把 redstone_torch 換成 glowstone（14 個），FlowerPot 會多出木棍（3 個），Trunk 的原木換成 redstone_block（1 個），Fence 柱子頂端有 stone（1 個）。Fence 沒有 Java 類別，也沒有 `functions`，所以玩家沒辦法在上面放東西。如果把這些掉出來，等於白送物品。
- 驗證結果（用標記規則，並和 FurnitureLib 一樣先讀 `CustomName` 再讀 `Name`）：SchoolTable 1 個、Table 3 個、TrashCan 4 個、WeaponStand 7 個（共 16 件工具，每個最多 3 件），其他樣式 0 個。

### Oraxen
- `OraxenFurniture.place(id, loc, yaw, BlockFace)` → `FurnitureMechanic#place(location, item, yaw, facing, checkSpace=true)`：
  - 空間檢查是 `notEnoughSpace(yaw, location)` = `getLocations(yaw, location, getBarriers())` 每一格都要通過 `BlockHelpers.isReplaceable`（`FurnitureMechanic.java:1306`）。這兩個方法都是 public，dry-run 用同一套方式預測（舊傢俱會清掉的格子視為空的）。
  - 實體生成位置經過 `correctedSpawnLocation`：DISPLAY_ENTITY 會用 `location.toCenterLocation()`（`FurnitureMechanic.java:756`），所以傳方塊座標就好。
- 儲物：`StorageMechanic.STORAGE_KEY`（`oraxen:storage`）+ `DataType.ITEM_STACK_ARRAY`。底層是 `ConfigurationSerializableArrayDataType`，用 `BukkitObjectOutputStream#writeObject` 逐格寫入，可以寫 null（空格）。容量是 `getStorage().getRows() × 9`。
- 座位是非 marker 的隱形 ArmorStand（0.5 × 1.975），生成在座位偏移的位置（`FurnitureMechanic.java:1351`）。hitbox 必須能包住它，否則點擊會被它擋掉（converter 的 `encloseSeats`）。
- 光源方塊（`Material.LIGHT`）沒有任何標記（`LightMechanic.java`）。FurnitureLib 的模型也沒有用到光源方塊，所以孤兒方塊只列 barrier：不屬於 FurnitureLib（`BlockManager#getList`），也不是 Oraxen 傢俱方塊（`OraxenFurniture.isFurniture(Block)`）的 barrier。

### 本機整合測試發現的事（2026-11-22，`testkit/` + 本機 Paper 1.21.8）
- **FurnitureLib 的起始點**：`FurnitureLib#spawn(Project, Location)` 會執行 `getStartLocation().add(.5, 0, .5)`，而且直接改掉物件本身的起始點（`ModelHandler#spawn`）。所以要傳**方塊角落的整數座標**，放完之後起始點就變成方塊中心，資料庫裡看到的 `.5` 就是這樣來的。`spawn` 不會登記物件，要再呼叫 `FurnitureManager#addObjectID`（`Command/spawnCommand.java:41` 也是這樣做）。
- **DiceFurniture 載入物件時會改方塊**：`TFlowerPot` 的建構子（FurnitureLib 每次載入這個物件都會執行）會把起始格設成 `FLOWER_POT`。如果舊紀錄在世界已經遷移之後才被載入，會把 Oraxen 的 barrier 蓋掉。migrator 的 CLEANUP 會用和 `FurnitureMechanic#setBarrierHitbox`（`FurnitureMechanic.java:1014`）一樣的方塊和 PDC 把它補回來。
- **當機時的一致性**：資料庫的 DELETE 是馬上生效的，但世界的變動要等存檔才會寫進硬碟（自動存檔預設 5 分鐘一次）。所以 migrator 每做完一個區塊，依序執行：`World#save(true)`（flush，等 region 檔案寫完）→ journal 寫 `COMMIT` → 刪資料庫紀錄 → 更新 checkpoint。
  - 存檔前當機：世界和資料庫都是舊的，resume 時那個區塊整個重做。
  - 存檔後、刪資料庫前當機：FurnitureLib 會重新載入舊紀錄。migrator 從 journal 的 `COMMIT`（或「那個位置已經有對應的 Oraxen 傢俱」）判斷已經遷移過，只做 `CLEANUP`：把所有格子從 FurnitureLib 釋放後刪掉紀錄，不重放傢俱、不掉物品。
- **測試結果**：30 種樣式加 1 種沒有對照的樣式，每種 4 個方向，共 124 個傢俱。以下三種情境都是 764 項檢查全部通過：
  1. 正常跑完；
  2. 跑到一半 `kill -9` 之後 resume；
  3. 模擬存檔後、刪資料庫前當機。

  檢查項目包括：新傢俱的位置和 yaw、新 barrier 是否剛好落在舊的實心方塊位置、座位位置、保留方塊和箱子內容、儲物、掉落物、沒有殘留的火把和 barrier、沒有對照的樣式保持原樣。

### 用真實地形和真實資料測試（2026-11-22，本機測試伺服器 1.21.8）
- 資料：只複製有傢俱的 region 檔（伺服器軟體和 region 格式和正式環境相同），再把資料庫 dump 匯入本機 SQLite。
- 結果（`ftk audit`，依照 journal 逐一檢查每個傢俱）：
  - 285 個傢俱：轉換 271、退役 14，需手動 0、跳過 0，**1831 項全部通過**。
- **FurnitureLib 讀不了舊版物品格式**：它載入紀錄裡的物品 NBT 時不會升級格式。真實資料裡有一個武器架上的附魔鐵鋤，存的是 1.21.4 的 `enchantments: {levels: {...}}`，在 1.21.8 載入後是空的，遊戲裡看不到，但資料庫裡還在。
  - migrator 現在會檢查：標記部件在記憶體裡是空的、資料庫裡卻有物品時，就用 FurnitureLib 自己的連線讀出紀錄（`Database#getConnection`，表 `Database.TABLE_NAME`），再交給 `UnsafeValues#deserializeItemFromJson` 套 DataFixer 升級。
  - 資料版本依格式推測：`Count`/`tag` → 3700（1.20.4），`levels`/`show_in_tooltip` → 4189（1.21.4），其他用目前版本。
  - 已確認救回的鐵鋤保留了效率附魔。如果有物品連 DataFixer 也讀不了，**整個傢俱都不動**，原始 NBT 寫進 journal，記為 SKIPPED，交給管理員處理。
- **花盆裡的植物**：DiceFurniture 的 FlowerPot 是真的花盆方塊，玩家可以在裡面種東西（`potted_*`）。移除舊傢俱時這格會變成空氣，所以 migrator 會把植物當成玩家物品掉在旁邊（真實資料裡有 2 個：cherry_sapling、blue_orchid）。
- **世界和模型對不上的情況**：有 2 盞路燈的方塊在世界裡早就是空氣（可能早就被拆壞了）。照樣正常遷移。
