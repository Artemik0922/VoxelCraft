"""One-off: restyle container screens + tooltips + chat to the Workshop theme."""
import re

FILES = [
    'src/main/java/com/voxelgame/ui/screen/SurvivalInventoryScreen.java',
    'src/main/java/com/voxelgame/ui/screen/CreativeInventoryScreen.java',
    'src/main/java/com/voxelgame/ui/screen/CraftingScreen.java',
    'src/main/java/com/voxelgame/ui/screen/FurnaceScreen.java',
    'src/main/java/com/voxelgame/ui/screen/EnchantingTableScreen.java',
    'src/main/java/com/voxelgame/ui/screen/TradingScreen.java',
    'src/main/java/com/voxelgame/ui/screen/ChestScreen.java',
]

SLOT_RE = re.compile(
    r'(\w+) \? gui\.glassSlotHover : gui\.glassSlot,\n'
    r'(\s+[^,\n]+, [^,\n]+, SLOT, SLOT, 3, GuiAssets\.SLOT_SIZE, )0xFFFFFFFF\);')
SLOT_REPL = r'gui.glassSlot,\n\2\1 ? 0xFF9A7D4C : 0xFF3A2A1A);'

for p in FILES:
    s = open(p, encoding='utf-8').read()
    n0 = s
    s = s.replace('0xFF10141E', '0xFF332314')                      # panel body
    s = SLOT_RE.sub(SLOT_REPL, s)                                  # slot tints
    s = s.replace('0xFFE8EEFF', '0xFFF2E6C8')                      # titles
    s = s.replace('0xFFE8F0FF', '0xFFF2E6C8')
    # scrollbars
    s = s.replace('''ui.drawNineSlice(gui.glassTrack, scrollX, gridY, SCROLLBAR_W, gridH,''',
                  '''ui.drawNineSlice(gui.glassTrack, scrollX, gridY, SCROLLBAR_W, gridH,''')
    open(p, 'w', encoding='utf-8').write(s)
    print(p.split('/')[-1], 'panel:', str(n0.count('0xFF10141E')), 'slots done')

# scroll thumbs -> brass (both screens with custom scrollbars)
for p in ['src/main/java/com/voxelgame/ui/screen/CreativeInventoryScreen.java',
          'src/main/java/com/voxelgame/ui/screen/TradingScreen.java']:
    s = open(p, encoding='utf-8').read()
    s = s.replace('''ui.drawNineSlice(gui.glassPanel, scrollX, thumbY, SCROLLBAR_W, thumbH,''',
                  '''ui.drawNineSlice(gui.glassPanel, scrollX, thumbY, SCROLLBAR_W, thumbH,''')
    # find their tint argument after the call and swap to brass if white-ish
    s = re.sub(r'(gui\.glassPanel, scrollX, thumbY, SCROLLBAR_W, thumbH,\n\s+[^,]+, [^,]+, )0x[0-9A-Fa-f]{8}\);',
               r'\g<1>0xFFC9973B);', s)
    s = re.sub(r'(gui\.glassTrack, scrollX, gridY, SCROLLBAR_W, gridH,\n\s+[^,]+, [^,]+, )0x[0-9A-Fa-f]{8}\);',
               r'\g<1>0xFF26180E);', s)
    s = re.sub(r'(gui\.glassTrack, scrollbarX, listY, SCROLLBAR_W, listH,\n\s+[^,]+, [^,]+, )0x[0-9A-Fa-f]{8}\);',
               r'\g<1>0xFF26180E);', s)
    s = re.sub(r'(gui\.glassPanel, scrollbarX, thumbY, SCROLLBAR_W, thumbH,\n\s+[^,]+, [^,]+, )0x[0-9A-Fa-f]{8}\);',
               r'\g<1>0xFFC9973B);', s)
    open(p, 'w', encoding='utf-8').write(s)
    print(p.split('/')[-1], 'scrollbar done')

# GlassTooltip -> kraft paper with ink text
p = 'src/main/java/com/voxelgame/ui/GlassTooltip.java'
s = open(p, encoding='utf-8').read()
s = s.replace('''        GuiAssets g = GuiAssets.INSTANCE;
        ui.drawNineSlice(g.glassShadow, tx + 2, ty + 3, tw, th,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xAA000000);
        ui.drawNineSlice(g.glassPanel, tx, ty, tw, th,
            GuiAssets.GLASS_BORDER, GuiAssets.GLASS_WIDGET, 0xF8202432);
        ui.fillRect(tx + 8, ty + 1, tw - 16, 1, 0x66FFFFFF);''',
'''        com.voxelgame.ui2.UiMaterials m = com.voxelgame.ui2.UiMaterials.INSTANCE;
        ui.drawNineSlice(m.paper, tx, ty, tw, th,
            com.voxelgame.ui2.UiTheme.PAPER_BORDER, com.voxelgame.ui2.UiTheme.PAPER_TEX,
            0xFFF6EDD8);
        ui.drawRectOutline(tx + 1, ty + 1, tw - 2, th - 2, 0x662A1D12);''')
s = s.replace("col = (i == 0) ? 0xFFFFFFFF : 0xFFB8C2DC;",
              "col = (i == 0) ? 0xFF2A1D12 : 0xFF5A4A34;")
open(p, 'w', encoding='utf-8').write(s)
print('GlassTooltip done')

# StackIcons: default tooltip colours readable on paper
p = 'src/main/java/com/voxelgame/ui/StackIcons.java'
s = open(p, encoding='utf-8').read()
s = s.replace('colors.add(0xFFFFFFFF);', 'colors.add(0xFF2A1D12);')
open(p, 'w', encoding='utf-8').write(s)
print('StackIcons done')
