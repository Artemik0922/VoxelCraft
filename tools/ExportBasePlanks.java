import com.voxelgame.rendering.TileGenerator;
import com.voxelgame.world.BlockType;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

/**
 * Exports the game's base plank textures as standalone 16x16 PNGs so the
 * PlankTextureGenerator can run in --mode dir.
 *
 * oak_planks.png already ships in resources; the other species are
 * procedural tiles, exported here at variant 0 (the default atlas variant).
 */
public class ExportBasePlanks {

    public static void main(String[] args) throws Exception {
        Path out = Paths.get(args[0]);
        Files.createDirectories(out);

        Files.copy(
            Paths.get("src/main/resources/textures/blocks/oak_planks.png"),
            out.resolve("oak_planks.png"),
            StandardCopyOption.REPLACE_EXISTING);

        write(out, "spruce_planks", BlockType.SPRUCE_PLANKS);
        write(out, "birch_planks", BlockType.BIRCH_PLANKS);
        write(out, "jungle_planks", BlockType.JUNGLE_PLANKS);

        System.out.println("Exported base planks into " + out.toAbsolutePath());
    }

    private static void write(Path dir, String name, BlockType type) throws Exception {
        int[] px = TileGenerator.generate(name, type, 0);
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < px.length; i++) {
            img.setRGB(i % 16, i / 16, px[i]);
        }
        ImageIO.write(img, "png", dir.resolve(name + ".png").toFile());
    }
}