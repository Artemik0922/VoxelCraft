# Журнал оптимизаций (2026-10-03) — что сделано и как откатить

Все изменения этого сеанса — производительность, **рендеринг затронут только в одном месте**
(асинхронная сборка геометрии, см. раздел 7) и он подробно задокументирован для отката.
На момент записи всё проверено: сборка OK, headless 132/132, живые скриншоты без артефактов.

## Быстрый откат

Всё, кроме пункта 7, — локальная логика, откатывается точечно по файлам ниже.
Полный откат всего сеанса: `git checkout -- .` (если изменения не закоммичены) —
вернёт репозиторий к состоянию «до оптимизаций» (коммит `d79fb11`).
Пункт 7 (рендер) можно откатить отдельно, не трогая остальное — код оригинала сохранён ниже.

## 1. Фермеры — список грядок вместо скана мира (~20 000 чтений блоков)
- `world/entity/Villager.java`: новые поля `farmCrops`, `wideScanCooldown`, `threatScanTimer`,
  `cachedThreat`, `bbScratch`; метод `setFarmCrops(List<int[]>)`; `findFarmerWorkTarget` сначала
  ищет по списку (`scanCropList`), старый скан мира — резервно раз в 8 с; поиск угроз кэшируется
  0.25 с. Откат: вернуть `findFarmerWorkTarget` к двум вызовам `scanCrop(true/false)`.
- `world/structure/VillageGenerator.java`: `generateFarms`/`generateFarm`/`generateGarden`
  принимают `List<int[]> cropSink` и добавляют координаты грядок; после генерации ферм список
  раздаётся фермерам (`v.setFarmCrops(villageCrops)`).

## 2. Ярусное тикание сущностей по дистанции
- `world/World.java`: `ENTITY_FREEZE_RADIUS=128`, `ENTITY_SLOW_RADIUS=56`, `AI_TICK_STRIDE=4`,
  `aiTickPhase`, `entityShouldTick()`. Дальние сущности не тикают, средние — каждый 4-й кадр.
- Откат: убрать проверки `entityShouldTick` в `updateMobs`/`updateVillagers`/`updateAnimals`.

## 3. Батчинг света при массовых правках
- `world/World.java`: `batchEditDepth`, `beginBatchEdits()/endBatchEdits()`, `markBatchNeighbor()`;
  в `setBlock` при активном пакете свет не пересчитывается поштучно, а ставятся флаги.
- `world/World.java`: `explode()` и `makeCrater()` обёрнуты в begin/end.
- `world/water/WaterSimulation.java`: `processSpread()` и `reflow()` (через новый `reflowInner`) обёрнуты.
- Откат: убрать флаг-ветку в setBlock и обёртки begin/end.

## 4. Сейвы: RLE-формат 7 + асинхронный диск
- `world/save/WorldSave.java`: `FORMAT_VERSION = 7` (было 6), RLE-функции `writeBlocksRLE`/
  `readBlocksRLE`, статический `IO_EXECUTOR` + `writeSnapshotsAsync`, уникальные tmp-имена
  (`.nanoTime.tmp`). Чтение v1–6 сохранено полностью → старые сейвы читаются, новые файлы
  НЕ читаются старой версией игры (единственное несовместимое изменение; откат форматa
  на 6 вернёт совместимость, существующие v7-файлы придётся удалить).
- `world/ChunkLoader.java`: перегрузка `request(cx, cz, generator, save)` — диск читается
  на воркере; `collect(target, budget)`.
- `world/World.java`: ветка `chunk.isRestoredFromDisk()` в publish; `snapshotChunk()`;
  выгрузка чанка = снапшот на главном потоке + `writeSnapshotsAsync`.
- `world/Chunk.java`: флаг `restoredFromDisk`; спавнеры собираются в `loadBlocks` (обоих вариантов).

## 5. Генерация: кэши на колонку (формы пещер НЕ изменены)
- `world/generator/TerrainGenerator.java` (`buildColumn`): `aquiferLevel` и `entranceSize`
  считаются раз на колонку; проверки `isWater`/`isLava`/подземных биомов ограничены `y <= height`.
- `world/cave/CaveGenerator.java`: перегрузка `isWater(..., aquiferLevel)`.
- Откат: убрать `y <= height` и кэши (вернётся прежний расход шума, поведение то же,
  кроме «парящих» дрипстоун/лавы в небе низких долин).

## 6. Мелкие правки в Game.java / сущностях
- Хуки команд привязываются один раз на смену (мир, игрок): `hooksWiredWorld`/`hooksWiredPlayer`.
- `collectDebugInfo`: дублирующий `biomeName` удалён; дорогие поля только при видимом F3.
- `updateItemEntities`: итератор вместо `new ArrayList` + `removeAll`.
- `checkSmeltAchievement(dt)`: раз в 0.5 с, ранний выход после получения ачивки.
- Лимитер 240 FPS при выключенном vsync (`frameStartNs` в `loop()`).
- `Villager/Zoloy/Animal`: `bbScratch float[6]` полем вместо `new float[6]` 2 раза за тик.
- `mobCapLogged` — лог лимита мобов один раз.
- Команда `perf` в дебаг-консоли (диагностика, безопасно).
- `World.update(pos, publishBudget)` + `ChunkLoader.collect(target, budget)` — адаптивные публикации.
- `markNeighboursDirty` 8 → 4 рёберных соседа (свет не ходит по диагонали).
- `Game.updateWorld`: дроссель публикаций по backlog'у рендера (>12/24/48 → 1 чанк в 2/4/8 тиков).

## 7. ЕДИНСТВЕННОЕ изменение рендера — асинхронная сборка геометрии чанков

**Проблема:** пермеш чанка (чистый CPU, 20–70 мс) выполнялся в кадре; бюджет 6 мс проверялся
только ПЕРЕД началом чанка → один тяжёлый чанк раздувал кадр до 50–87 мс (FPS 12–20 в полёте).

**Решение:** CPU-геометрию строят 2 фоновых потока `mesh-worker`, GL-заливка (3/кадр) и весь
остальной GL — по-прежнему только главный поток. Валидация результата — по счётчикам версий.

Затронутые места и ОРИГИНАЛЬНЫЙ код для отката:

### 7a. `rendering/Renderer.java`
- `updateDirtyChunks`: в цикле мешей вместо синхронного `rc.rebuild(...)` —
  `submitGeometryBuild(key, chunk, world)` + `processFinishedBuilds(world)` в конце.
- Новое: поля `UPLOAD_BUDGET_PER_FRAME=3`, `MAX_IN_FLIGHT_BUILDS=8`, `meshWorkers` (2 потока,
  daemon, NORM-1), `inFlightBuilds`, класс `PendingGeom` (key/chunk/world/neighbors/versions/future),
  методы `submitGeometryBuild`, `processFinishedBuilds`, `versionsUnchanged`.
- `RenderChunk.rebuild(...)` теперь только `applyGeometry(chunk, build(...))`; сам `applyGeometry` —
  прежнее тело rebuild без строки `ChunkMeshBuilder.build` (modelMatrix, Mesh.rebind ×3, fade).
- `cleanup()`: добавлено `meshWorkers.shutdownNow(); inFlightBuilds.clear();`

**Оригинальный цикл мешей (для отката заменить новую версию на этот код):**
```java
for (Map.Entry<Long, Chunk> entry : world.getChunks().entrySet()) {
    Chunk chunk = entry.getValue();
    if (!chunk.isDirty()) continue;

    // Never mesh a chunk whose light is still pending
    if (chunk.isLightDirty()) { meshBacklog++; continue; }

    if (System.nanoTime() > deadline) { meshBacklog++; continue; }

    RenderChunk rc = renderChunks.computeIfAbsent(entry.getKey(), k -> new RenderChunk());
    rc.rebuild(chunk, world, textureAtlas);
    chunk.setDirty(false);
    remeshGeneration++;
}
```
(и удалить processFinishedBuilds(world) в конце updateDirtyChunks, поля пула,
PendingGeom, submitGeometryBuild/processFinishedBuilds/versionsUnchanged; rebuild вернуть
к слитому виду; из cleanup() убрать две строки пула.)

**Оригинальный RenderChunk.rebuild:**
```java
void rebuild(Chunk chunk, World world, TextureAtlas atlas) {
    boolean firstBuild = opaque == null && transparent == null && leaves == null;
    modelMatrix = new Matrix4f().translate(chunk.getWorldX(), 0, chunk.getWorldZ());
    ChunkMeshBuilder.ChunkGeom geom = ChunkMeshBuilder.build(chunk, world, atlas);
    opaque = Mesh.rebind(opaque, geom.opaque);
    transparent = Mesh.rebind(transparent, geom.transparent);
    leaves = Mesh.rebind(leaves, geom.leaves);
    if (firstBuild) { bornNs = System.nanoTime(); fadeAlpha = 0.0f; }
    else fadeAlpha = 1.0f;
}
```

### 7b. Поддержка со стороны логики (счётчики версий)
- `world/Chunk.java`: `volatile long meshVersion` + `bumpMeshVersion()`; инкременты в
  `setBlock`, обоих `loadBlocks`, `setDoorMeta`, `setCropStage`, `setWaterLevel`;
  мета-карты `doorMeta/cropMeta/waterMeta` переведены с HashMap на ConcurrentHashMap
  (воркер читает их параллельно с главным потоком).
- `world/LightEngine.java`: `computeChunkLight` в конце делает `chunk.bumpMeshVersion()`.
- Откат 7b безопасен ТОЛЬКО вместе с 7a (без воркеров счётчики просто не нужны).

### Семантика валидности (важно при отладке)
Результат сборки принимается, только если: тот же World, чанк в карте, `isDirty`,
`!isLightDirty`, и meshVersion чанка и 8 соседей не изменились с момента старта.
Иначе геометрия выбрасывается и чанк перестраивается. При исключении в воркере —
синхронный `rebuild` (старое поведение) как fallback.

## Прочие файлы
- `core/FrameTimer.java`, `core/Settings.java` — не менялись.
- `debug/HeadlessTestRunner.java` — не менялся (регрессия: `java -cp "build;lib/*" com.voxelgame.Main --headless`).
