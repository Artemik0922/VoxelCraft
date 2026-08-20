# 🤖 Промт для AI-агента: Запуск генерации текстур жителей

## Промт (скопируйте и отправьте AI-агенту):

```
Мне нужно сгенерировать текстуры жителей деревень для моей Minecraft-подобной игры на Java.
Проект находится в папке: D:/backup_old/TopDownRPG/minecraft-clone/

Выполни следующие шаги по порядку:

1. ПЕРЕЙДИ в папку villager_textures:
   cd D:/backup_old/TopDownRPG/minecraft-clone/villager_textures

2. УСТАНОВИ зависимость Pillow (если ещё не установлена):
   pip install Pillow

3. ЗАПУСТИ основной скрипт генерации текстур:
   python generate_villager_atlas.py

   Ожидаемый результат:
   - Создание 16 отдельных PNG текстур профессий (farmer, librarian, blacksmith, butcher, priest, fisherman, fletcher, leatherworker, shepherd, toolsmith, armorer, weaponsmith, cartographer, cleric, mason, nitwit)
   - Создание атласа villager_atlas.png в src/main/resources/textures/entities/
   - Создание биом-атласа villager_biomes_atlas.png
   - Создание JSON-манифеста villager_atlas.json
   - Создание предпросмотра atlas_preview.png в villager_textures/

4. ЗАПУСТИ скрипт генерации вариантов:
   python generate_villager_variants.py

   Ожидаемый результат:
   - Создание villager_baby.png (маленький житель)
   - Создание villager_zombie.png (зомби-житель)
   - Создание villager_variants_atlas.png
   - Создание variants_preview.png

5. ПРОВЕРЬ что все файлы созданы:
   dir src/main/resources/textures/entities/villager_*.png
   Должно быть ~25+ файлов.

6. Если всё ОК — сообщи что текстуры готовы.
   Если ошибка — выведи текст ошибки и предложи решение.

ВАЖНО:
- Все текстуры должны быть 64x64 пикселя, RGBA, PNG формат
- Файлы сохраняются в src/main/resources/textures/entities/
- Атлас содержит текстуры всех профессий бок о бок (каждая 64x64)
- UV-маппинг соответствует стандартному Minecraft формату
- Лицензия: свободное использование для этого проекта

Если pip install не работает, попробуй:
  python -m pip install Pillow
или:
  py -m pip install Pillow

Если python не найден, попробуй:
  py generate_villager_atlas.py
```

## Что будет после успешного запуска:

```
villager_textures/
├── atlas_preview.png          ← увеличенный предпросмотр 4x
├── variants_preview.png       ← предпросмотр baby+zombie
├── generate_villager_atlas.py
├── generate_villager_variants.py
├── run_all.py
├── requirements.txt
└── README.md

src/main/resources/textures/entities/
├── villager_atlas.png         ← ОСНОВНОЙ АТЛАС (16 x 64px)
├── villager_biomes_atlas.png  ← биом-вариации farmer
├── villager_variants_atlas.png← baby + zombie
├── villager_atlas.json        ← манифест для Java
├── villager_farmer.png
├── villager_librarian.png
├── villager_blacksmith.png
├── villager_butcher.png
├── villager_priest.png
├── villager_fisherman.png
├── villager_fletcher.png
├── villager_leatherworker.png
├── villager_shepherd.png
├── villager_toolsmith.png
├── villager_armorer.png
├── villager_weaponsmith.png
├── villager_cartographer.png
├── villager_cleric.png
├── villager_mason.png
├── villager_nitwit.png
├── villager_baby.png
└── villager_zombie.png
```

## Тестирование в игре:

После генерации текстур и компиляции Java-кода, в игре доступны команды:

```
/spawn villager              — 1 случайный житель
/spawn villager 5            — 5 случайных жителей
/spawn villager 3 farmer     — 3 фермера
/spawn villager 1 blacksmith — 1 кузнец
```

## Компиляция проекта:

После генерации текстур, соберите проект:

```powershell
# Windows
.\build.ps1
# или
javac -d build -cp "lib/*" src/main/java/com/voxelgame/**/*.java src/main/java/com/voxelgame/*.java
```

## Что создано для жителей:

### Java-файлы (уже созданы):
- `src/main/java/com/voxelgame/world/entity/Villager.java` — мирный моб с AI
- `src/main/java/com/voxelgame/world/entity/VillagerModel.java` — 3D модель
- `src/main/java/com/voxelgame/world/structure/VillageGenerator.java` — генератор деревень
- Обновлён `World.java` — добавлены villagers и updateVillagers()
- Обновлён `SpawnCommand.java` — поддержка /spawn villager

### Python-скрипты (для текстур):
- `villager_textures/generate_villager_atlas.py` — основной генератор
- `villager_textures/generate_villager_variants.py` — baby/zombie варианты
- `villager_textures/run_all.py` — запуск всех скриптов
