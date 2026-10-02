"""One-off: restyle toasts to Workshop kraft-paper tags."""

PAPER = ('ui.drawNineSlice(com.voxelgame.ui2.UiMaterials.INSTANCE.paper, x, y, WIDTH, HEIGHT,\n'
         '            com.voxelgame.ui2.UiTheme.PAPER_BORDER, com.voxelgame.ui2.UiTheme.PAPER_TEX,\n'
         '            0xFFFFFFFF);')
BRASS = 'ui.fillRect(x + 6, y + 1, WIDTH - 12, 1, 0xFF8A6420);'

# BiomeToast
p = 'src/main/java/com/voxelgame/ui/toast/BiomeToast.java'
s = open(p, encoding='utf-8').read()
s = s.replace('''        ui.drawNineSlice(gui.glassPanel, x, y, WIDTH, HEIGHT,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF202838);
        ui.fillRect(x + 6, y + 1, WIDTH - 12, 1, 0x60FFFFFF);''',
PAPER + '\n        ' + BRASS)
s = s.replace("font.drawWithShadow(ui, title, textX, y + 5, 0xFFAAAAAA);",
              "font.drawWithShadow(ui, title, textX, y + 5, 0xFF6E5A3A);")
s = s.replace("font.drawWithShadow(ui, biomeName, textX, y + 17, 0xFFFFFFFF);",
              "font.drawWithShadow(ui, biomeName, textX, y + 17, 0xFF2A1D12);")
open(p, 'w', encoding='utf-8').write(s)
print('BiomeToast patched')

# AchievementToast
p = 'src/main/java/com/voxelgame/ui/toast/AchievementToast.java'
s = open(p, encoding='utf-8').read()
s = s.replace('''        ui.drawNineSlice(gui.glassPanel, x, y, WIDTH, HEIGHT,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF262034);
        ui.fillRect(x + 6, y + 1, WIDTH - 12, 1, 0x60FFFFFF);''',
PAPER + '\n        ' + BRASS)
s = s.replace("font.drawWithShadow(ui, title, textX, y + 5, 0xFFFFAA00); // Gold title",
              "font.drawWithShadow(ui, title, textX, y + 5, 0xFF8A6420); // Engraved brass title")
s = s.replace("font.drawWithShadow(ui, description, textX, y + 17, 0xFFFFFFFF);",
              "font.drawWithShadow(ui, description, textX, y + 17, 0xFF2A1D12);")
open(p, 'w', encoding='utf-8').write(s)
print('AchievementToast patched')

# TutorialToast
p = 'src/main/java/com/voxelgame/ui/toast/TutorialToast.java'
s = open(p, encoding='utf-8').read()
s = s.replace('''        ui.drawNineSlice(gui.glassPanel, x, y, WIDTH, HEIGHT,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xFF10141E);
        ui.fillRect(x + 6, y + 1, WIDTH - 12, 1, 0x60FFFFFF);''',
PAPER + '\n        ' + BRASS)
s = s.replace("font.drawWithShadow(ui, tr(\"tutorial.title\"), textX, y + 4, 0xFF7AE07A);",
              "font.drawWithShadow(ui, tr(\"tutorial.title\"), textX, y + 4, 0xFF4A6A2A);")
s = s.replace("font.drawWithShadow(ui, tr(\"tutorial.move\"), textX, y + 17, 0xFFFFFFFF);",
              "font.drawWithShadow(ui, tr(\"tutorial.move\"), textX, y + 17, 0xFF2A1D12);")
s = s.replace("font.drawWithShadow(ui, tr(\"tutorial.jumpSprint\"), textX, y + 29, 0xFFFFFFFF);",
              "font.drawWithShadow(ui, tr(\"tutorial.jumpSprint\"), textX, y + 29, 0xFF2A1D12);")
s = s.replace("font.drawWithShadow(ui, tr(\"tutorial.minePlace\"), textX, y + 41, 0xFFFFFFFF);",
              "font.drawWithShadow(ui, tr(\"tutorial.minePlace\"), textX, y + 41, 0xFF2A1D12);")
s = s.replace("font.drawWithShadow(ui, tr(\"tutorial.inventory\"), textX, y + 53, 0xFFFFFFFF);",
              "font.drawWithShadow(ui, tr(\"tutorial.inventory\"), textX, y + 53, 0xFF2A1D12);")
open(p, 'w', encoding='utf-8').write(s)
print('TutorialToast patched')
