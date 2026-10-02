import com.voxelgame.item.Item;
import com.voxelgame.item.ItemRegistry;
import com.voxelgame.world.BlockType;

/** Resolve saved inventory ids the way the game would. */
public class InvProbe {
    public static void main(String[] args) {
        String data = "0:b:143:1:0;1:b:265:1:0;2:b:115:1:0;3:b:117:1:0;4:b:177:1:0;5:b:169:1:0";
        for (BlockType bt : BlockType.values()) {
            if (bt.isItemSprite()) {
                Item match = null;
                for (Item it : ItemRegistry.all()) {
                    if (it.name.equals(bt.name)) { match = it; break; }
                }
                System.out.println("spriteBlock id=" + bt.id + " " + bt.name
                    + " solid=" + bt.solid + " itemMatch=" + (match != null ? match.name : "NONE"));
            }
        }
        for (String part : data.split(";")) {
            String[] t = part.split(":");
            String slot = t[0], type = t[1];
            int id = Integer.parseInt(t[2]);
            if (type.equals("b")) {
                BlockType found = null;
                for (BlockType bt : BlockType.values()) {
                    if (bt.id == id) { found = bt; break; }
                }
                System.out.println("slot " + slot + " BLOCK id=" + id + " -> "
                    + (found != null ? found.name : "UNRESOLVED"));
            } else {
                Item found = null;
                for (Item it : ItemRegistry.all()) {
                    if (it.id == id) { found = it; break; }
                }
                System.out.println("slot " + slot + " ITEM id=" + id + " -> "
                    + (found != null ? found.name + " sprite=" + found.spriteName
                                     : "UNRESOLVED"));
            }
        }
    }
}
