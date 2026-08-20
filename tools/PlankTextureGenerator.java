import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

public class PlankTextureGenerator {

    private static final String[] DEFAULT_TREATMENTS = {
            "raw",
            "dried",
            "sealed",
            "charred",
            "reinforced",
            "composite",
            "waxed",
            "lacquered"
    };

    static class Args {
        Path input;
        Path output;
        Path mapping;
        String mode = "dir";
        int tile = 16;
        List<String> names = new ArrayList<>();
        List<String> treatments = Arrays.asList(DEFAULT_TREATMENTS);
    }

    static class Op {
        float brightness = 1.0f;
        float saturation = 0.0f;
        int rAdd = 0;
        int gAdd = 0;
        int bAdd = 0;
        float noiseAmount = 0.0f;
        float noiseStrength = 0.0f;
        boolean darkNoise = false;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        Args a = parseArgs(args);
        if (a == null) {
            printUsage();
            return;
        }

        Files.createDirectories(a.output);
        List<Map<String, Object>> manifest = new ArrayList<>();

        if (a.mode.equals("atlas-global")) {
            generateAtlasVariants(a, manifest);
        } else {
            Map<String, BufferedImage> bases = loadBaseTextures(a);
            boolean generatedAny = false;

            for (Map.Entry<String, BufferedImage> entry : bases.entrySet()) {
                String name = entry.getKey();

                if (!isBasePlank(name)) {
                    continue;
                }

                String wood = extractWood(name);
                if (wood == null || wood.isEmpty()) {
                    wood = name;
                }

                generateWood(wood, entry.getValue(), a.output, a.treatments, manifest);
                generatedAny = true;
            }

            if (!generatedAny) {
                System.err.println("WARNING: no base plank textures found by name filter. Nothing generated.");
            }
        }

        writeManifest(a.output.resolve("generated_textures.json"), manifest);
        System.out.println("Generated " + manifest.size() + " texture set(s) into " + a.output.toAbsolutePath());
    }

    private static Args parseArgs(String[] args) {
        Args a = new Args();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--input":
                    if (i + 1 >= args.length) return null;
                    a.input = Paths.get(args[++i]);
                    break;

                case "--output":
                    if (i + 1 >= args.length) return null;
                    a.output = Paths.get(args[++i]);
                    break;

                case "--mapping":
                    if (i + 1 >= args.length) return null;
                    a.mapping = Paths.get(args[++i]);
                    break;

                case "--mode":
                    if (i + 1 >= args.length) return null;
                    a.mode = args[++i].toLowerCase(Locale.ROOT);
                    break;

                case "--tile":
                    if (i + 1 >= args.length) return null;
                    a.tile = Integer.parseInt(args[++i]);
                    break;

                case "--names":
                    if (i + 1 >= args.length) return null;
                    a.names = splitCsv(args[++i]);
                    break;

                case "--treatments":
                    if (i + 1 >= args.length) return null;
                    a.treatments = splitCsv(args[++i]);
                    break;

                default:
                    System.err.println("Unknown argument: " + arg);
                    return null;
            }
        }

        if (a.input == null || a.output == null) {
            return null;
        }

        return a;
    }

    private static void printUsage() {
        System.out.println("PlankTextureGenerator");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java PlankTextureGenerator --mode dir --input <folder> --output <folder>");
        System.out.println("  java PlankTextureGenerator --mode atlas --input <atlas.png> --mapping <properties> --output <folder>");
        System.out.println("  java PlankTextureGenerator --mode atlas-global --input <atlas.png> --output <folder>");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --mode dir|atlas|atlas-global");
        System.out.println("  --input <path>");
        System.out.println("  --output <path>");
        System.out.println("  --mapping <properties file>");
        System.out.println("  --tile <pixel size, default 16>");
        System.out.println("  --names oak_planks,spruce_planks,...");
        System.out.println("  --treatments raw,dried,sealed,charred,reinforced,composite,waxed,lacquered");
    }

    private static List<String> splitCsv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static void generateAtlasVariants(Args a, List<Map<String, Object>> manifest) throws IOException {
        BufferedImage atlas = ImageIO.read(a.input.toFile());
        if (atlas == null) {
            throw new IOException("Cannot read atlas image: " + a.input);
        }

        String atlasName = fileNameWithoutExtension(a.input.getFileName().toString());

        for (String treatment : a.treatments) {
            BufferedImage img = applyTreatment(atlas, treatment, seed(atlasName, treatment), false);
            String fileName = write(a.output, atlasName + "_" + treatment + "_atlas.png", img);

            Map<String, String> files = new LinkedHashMap<>();
            files.put("atlas", fileName);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("wood", "atlas");
            entry.put("treatment", treatment);
            entry.put("files", files);

            manifest.add(entry);
        }
    }

    private static Map<String, BufferedImage> loadBaseTextures(Args a) throws IOException {
        Map<String, BufferedImage> out = new LinkedHashMap<>();

        if (a.mode.equals("dir")) {
            if (!Files.isDirectory(a.input)) {
                throw new IOException("Input directory not found: " + a.input);
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(a.input, "*.png")) {
                for (Path p : stream) {
                    String name = fileNameWithoutExtension(p.getFileName().toString());
                    BufferedImage img = ImageIO.read(p.toFile());
                    if (img != null) {
                        out.put(name, img);
                    }
                }
            }
        } else if (a.mode.equals("atlas")) {
            BufferedImage atlas = ImageIO.read(a.input.toFile());
            if (atlas == null) {
                throw new IOException("Cannot read atlas image: " + a.input);
            }

            Map<String, Rectangle> rects;

            if (a.mapping != null) {
                rects = loadMapping(a.mapping);
            } else if (!a.names.isEmpty()) {
                rects = gridMapping(atlas, a.names, a.tile);
            } else {
                throw new IOException("Atlas mode requires --mapping or --names, or use --mode atlas-global");
            }

            for (Map.Entry<String, Rectangle> entry : rects.entrySet()) {
                String key = simpleName(entry.getKey());
                BufferedImage img = crop(atlas, entry.getValue());
                out.put(key, img);
            }
        } else {
            throw new IOException("Unknown mode: " + a.mode);
        }

        return out;
    }

    private static Map<String, Rectangle> loadMapping(Path path) throws IOException {
        Properties p = new Properties();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            p.load(reader);
        }

        Map<String, Rectangle> result = new LinkedHashMap<>();

        for (String key : p.stringPropertyNames()) {
            String value = p.getProperty(key).trim();
            String[] parts = value.split(",");

            if (parts.length < 4) {
                throw new IOException("Bad mapping for " + key + ": " + value);
            }

            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int w = Integer.parseInt(parts[2].trim());
            int h = Integer.parseInt(parts[3].trim());

            result.put(key, new Rectangle(x, y, w, h));
        }

        return result;
    }

    private static Map<String, Rectangle> gridMapping(BufferedImage atlas, List<String> names, int tile) {
        Map<String, Rectangle> map = new LinkedHashMap<>();
        int safeTile = Math.max(1, tile);
        int cols = Math.max(1, atlas.getWidth() / safeTile);

        for (int i = 0; i < names.size(); i++) {
            int x = (i % cols) * safeTile;
            int y = (i / cols) * safeTile;
            map.put(names.get(i), new Rectangle(x, y, safeTile, safeTile));
        }

        return map;
    }

    private static BufferedImage crop(BufferedImage src, Rectangle r) {
        if (r.x < 0 || r.y < 0 || r.x + r.width > src.getWidth() || r.y + r.height > src.getHeight()) {
            throw new IllegalArgumentException("Rectangle out of atlas bounds: " + r);
        }

        BufferedImage dst = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(
                src,
                0,
                0,
                r.width,
                r.height,
                r.x,
                r.y,
                r.x + r.width,
                r.y + r.height,
                null
        );
        g.dispose();
        return dst;
    }

    private static void generateWood(
            String wood,
            BufferedImage base,
            Path outDir,
            List<String> treatments,
            List<Map<String, Object>> manifest
    ) throws IOException {
        for (String treatment : treatments) {
            BufferedImage treated = applyTreatment(base, treatment, seed(wood, treatment), true);

            Map<String, String> files = new LinkedHashMap<>();

            files.put("planks", write(outDir, wood + "_" + treatment + "_planks.png", treated));
            files.put("button", write(outDir, wood + "_" + treatment + "_button.png", copy(treated)));

            BufferedImage doorTop = generateDoor(treated, true);
            BufferedImage doorBottom = generateDoor(treated, false);

            files.put("door_top", write(outDir, wood + "_" + treatment + "_door_top.png", doorTop));
            files.put("door_bottom", write(outDir, wood + "_" + treatment + "_door_bottom.png", doorBottom));
            files.put("door_combined", write(outDir, wood + "_" + treatment + "_door.png", combineVertical(doorTop, doorBottom)));

            files.put("trapdoor", write(outDir, wood + "_" + treatment + "_trapdoor.png", generateTrapdoor(treated)));
            files.put("fence", write(outDir, wood + "_" + treatment + "_fence.png", generateFence(treated)));
            files.put("fence_gate", write(outDir, wood + "_" + treatment + "_fence_gate.png", generateFence(treated)));
            files.put("sign", write(outDir, wood + "_" + treatment + "_sign.png", generateSign(treated)));

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("wood", wood);
            entry.put("treatment", treatment);
            entry.put("files", files);

            manifest.add(entry);
        }
    }

    private static BufferedImage applyTreatment(BufferedImage src, String treatment, long seed, boolean perSpriteOverlays) {
        BufferedImage img = copy(src);
        Op op = new Op();

        switch (treatment) {
            case "raw":
                op.brightness = 0.94f;
                op.saturation = -0.16f;
                op.noiseAmount = 0.04f;
                op.noiseStrength = 16.0f;
                op.darkNoise = true;
                break;

            case "dried":
                op.brightness = 1.05f;
                op.saturation = 0.04f;
                break;

            case "sealed":
                op.brightness = 1.0f;
                op.saturation = 0.12f;
                op.rAdd = 14;
                op.gAdd = 7;
                op.bAdd = -5;
                break;

            case "charred":
                op.brightness = 0.56f;
                op.saturation = -0.64f;
                op.noiseAmount = 0.10f;
                op.noiseStrength = 30.0f;
                op.darkNoise = true;
                break;

            case "reinforced":
                op.brightness = 0.95f;
                op.saturation = -0.07f;
                break;

            case "composite":
                op.brightness = 0.97f;
                op.saturation = -0.26f;
                op.noiseAmount = 0.07f;
                op.noiseStrength = 18.0f;
                break;

            case "waxed":
                op.brightness = 1.06f;
                op.saturation = 0.06f;
                op.rAdd = 8;
                op.gAdd = 5;
                op.bAdd = -2;
                break;

            case "lacquered":
                op.brightness = 1.10f;
                op.saturation = 0.15f;
                op.rAdd = 4;
                op.gAdd = 4;
                op.bAdd = 4;
                break;

            default:
                break;
        }

        int w = img.getWidth();
        int h = img.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 255;
                if (alpha == 0) continue;

                img.setRGB(x, y, adjustPixel(argb, op));
            }
        }

        if (op.noiseAmount > 0.0f) {
            addNoise(img, new Random(seed), op.noiseAmount, op.noiseStrength, op.darkNoise);
        }

        if (perSpriteOverlays) {
            Random overlayRandom = new Random(seed ^ 0x5DEECE66DL);

            if (treatment.equals("reinforced")) {
                addBorderOverlay(img, 130, 130, 130, 120, 1);
                addRivets(img, 230, 230, 230, 210);
            } else if (treatment.equals("sealed")) {
                addSpeckles(img, overlayRandom, 235, 190, 90, 28, 0.012f);
            } else if (treatment.equals("composite")) {
                addSpeckles(img, overlayRandom, 190, 190, 190, 38, 0.02f);
            } else if (treatment.equals("charred")) {
                addSpeckles(img, overlayRandom, 0, 0, 0, 35, 0.03f);
            }
        }

        return img;
    }

    private static int adjustPixel(int argb, Op op) {
        int a = (argb >>> 24) & 255;
        if (a == 0) return 0;

        float r = ((argb >> 16) & 255) * op.brightness + op.rAdd;
        float g = ((argb >> 8) & 255) * op.brightness + op.gAdd;
        float b = (argb & 255) * op.brightness + op.bAdd;

        r = clampF(r);
        g = clampF(g);
        b = clampF(b);

        float gray = 0.299f * r + 0.587f * g + 0.114f * b;
        float sat = 1.0f + op.saturation;

        r = clampF(gray + (r - gray) * sat);
        g = clampF(gray + (g - gray) * sat);
        b = clampF(gray + (b - gray) * sat);

        return (a << 24) | ((int) r << 16) | ((int) g << 8) | (int) b;
    }

    private static void addNoise(BufferedImage img, Random rnd, float amount, float strength, boolean dark) {
        int w = img.getWidth();
        int h = img.getHeight();
        int max = Math.max(1, (int) strength);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (rnd.nextFloat() >= amount) continue;

                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 255;
                if (a == 0) continue;

                int delta = dark ? -rnd.nextInt(max) : rnd.nextInt(max);

                int r = clampI(((argb >> 16) & 255) + delta);
                int g = clampI(((argb >> 8) & 255) + delta);
                int b = clampI((argb & 255) + delta);

                img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    private static void addSpeckles(BufferedImage img, Random rnd, int r, int g, int b, int a, float amount) {
        int w = img.getWidth();
        int h = img.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (rnd.nextFloat() < amount) {
                    blendPixel(img, x, y, r, g, b, a);
                }
            }
        }
    }

    private static void addRivets(BufferedImage img, int r, int g, int b, int a) {
        int w = img.getWidth();
        int h = img.getHeight();

        if (w < 4 || h < 4) return;

        int[][] points = new int[][] {
                {1, 1},
                {w - 2, 1},
                {1, h - 2},
                {w - 2, h - 2},
                {w / 2, 1},
                {w / 2, h - 2},
                {1, h / 2},
                {w - 2, h / 2}
        };

        for (int[] p : points) {
            blendPixel(img, p[0], p[1], r, g, b, a);
        }
    }

    private static void addBorderOverlay(BufferedImage img, int r, int g, int b, int a, int thickness) {
        int w = img.getWidth();
        int h = img.getHeight();
        int t = Math.max(0, thickness);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x < t || y < t || x >= w - t || y >= h - t) {
                    blendPixel(img, x, y, r, g, b, a);
                }
            }
        }
    }

    private static BufferedImage generateDoor(BufferedImage treatedPlank, boolean top) {
        BufferedImage img = copy(treatedPlank);
        int w = img.getWidth();
        int h = img.getHeight();

        addBorderOverlay(img, 0, 0, 0, 80, 1);

        if (top) {
            int rx = Math.max(1, w / 4);
            int ry = Math.max(1, h / 4);
            int rw = Math.max(2, w / 2);
            int rh = Math.max(2, h / 2);

            overlayRect(img, rx, ry, rw, rh, 255, 255, 255, 22);
            overlayRect(img, Math.max(1, w / 2 - 1), 0, 2, h, 0, 0, 0, 18);
        } else {
            int bandH = Math.max(2, h / 6);
            overlayRect(img, 0, h - bandH, w, bandH, 0, 0, 0, 40);
            overlayRect(img, 0, Math.max(0, h / 2 - 1), w, 2, 0, 0, 0, 25);
        }

        return img;
    }

    private static BufferedImage generateTrapdoor(BufferedImage treatedPlank) {
        BufferedImage img = copy(treatedPlank);
        int w = img.getWidth();
        int h = img.getHeight();

        addBorderOverlay(img, 0, 0, 0, 80, 1);
        overlayRect(img, Math.max(0, w / 2 - 1), 0, 2, h, 0, 0, 0, 25);
        overlayRect(img, 0, Math.max(0, h / 2 - 1), w, 2, 0, 0, 0, 25);

        return img;
    }

    private static BufferedImage generateFence(BufferedImage treatedPlank) {
        BufferedImage img = copy(treatedPlank);
        int w = img.getWidth();
        int h = img.getHeight();

        overlayRect(img, Math.max(0, w / 2 - 1), 0, 2, h, 0, 0, 0, 25);
        addBorderOverlay(img, 255, 255, 255, 10, 1);

        return img;
    }

    private static BufferedImage generateSign(BufferedImage treatedPlank) {
        BufferedImage img = copy(treatedPlank);
        int w = img.getWidth();
        int h = img.getHeight();

        addBorderOverlay(img, 0, 0, 0, 70, 1);
        overlayRect(img, 1, 1, Math.max(1, w - 2), Math.max(1, h - 2), 255, 255, 255, 10);

        return img;
    }

    private static BufferedImage combineVertical(BufferedImage top, BufferedImage bottom) {
        int w = Math.max(top.getWidth(), bottom.getWidth());
        int h = top.getHeight() + bottom.getHeight();

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(top, 0, 0, null);
        g.drawImage(bottom, 0, top.getHeight(), null);
        g.dispose();

        return img;
    }

    private static void overlayRect(BufferedImage img, int x, int y, int w, int h, int r, int g, int b, int a) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                blendPixel(img, xx, yy, r, g, b, a);
            }
        }
    }

    private static void blendPixel(BufferedImage img, int x, int y, int r, int g, int b, int a) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight() || a <= 0) {
            return;
        }

        int dst = img.getRGB(x, y);
        int da = (dst >>> 24) & 255;

        if (da == 0) {
            img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            return;
        }

        float af = a / 255.0f;

        int dr = (dst >> 16) & 255;
        int dg = (dst >> 8) & 255;
        int db = dst & 255;

        int nr = clampI((int) (r * af + dr * (1.0f - af)));
        int ng = clampI((int) (g * af + dg * (1.0f - af)));
        int nb = clampI((int) (b * af + db * (1.0f - af)));
        int na = Math.max(da, a);

        img.setRGB(x, y, (na << 24) | (nr << 16) | (ng << 8) | nb);
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    private static String write(Path dir, String fileName, BufferedImage img) throws IOException {
        Path p = dir.resolve(fileName);
        ImageIO.write(img, "png", p.toFile());
        return fileName;
    }

    private static void writeManifest(Path path, List<Map<String, Object>> manifest) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        for (int i = 0; i < manifest.size(); i++) {
            Map<String, Object> entry = manifest.get(i);

            sb.append("  {\n");
            sb.append("    \"wood\": \"").append(escapeJson(String.valueOf(entry.get("wood")))).append("\",\n");
            sb.append("    \"treatment\": \"").append(escapeJson(String.valueOf(entry.get("treatment")))).append("\",\n");
            sb.append("    \"files\": {\n");

            @SuppressWarnings("unchecked")
            Map<String, String> files = (Map<String, String>) entry.get("files");

            int j = 0;
            for (Map.Entry<String, String> fileEntry : files.entrySet()) {
                sb.append("      \"")
                  .append(escapeJson(fileEntry.getKey()))
                  .append("\": \"")
                  .append(escapeJson(fileEntry.getValue()))
                  .append("\"");

                if (j < files.size() - 1) {
                    sb.append(",");
                }

                sb.append("\n");
                j++;
            }

            sb.append("    }\n");
            sb.append("  }");

            if (i < manifest.size() - 1) {
                sb.append(",");
            }

            sb.append("\n");
        }

        sb.append("]\n");

        Files.write(path, sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static boolean isBasePlank(String name) {
        String lower = name.toLowerCase(Locale.ROOT);

        if (!lower.contains("plank")) {
            return false;
        }

        for (String treatment : DEFAULT_TREATMENTS) {
            if (lower.contains("_" + treatment + "_") || lower.endsWith("_" + treatment) || lower.startsWith(treatment + "_")) {
                return false;
            }
        }

        return true;
    }

    private static String extractWood(String name) {
        String lower = name.toLowerCase(Locale.ROOT);

        int idx = lower.indexOf("_planks");
        if (idx > 0) {
            return name.substring(0, idx);
        }

        if (lower.endsWith("_plank")) {
            return name.substring(0, name.length() - 6);
        }

        if (lower.startsWith("planks_")) {
            return name.substring(7);
        }

        String cleaned = name.replaceAll("(?i)_?planks?", "");
        return cleaned.isEmpty() ? name : cleaned;
    }

    private static String simpleName(String s) {
        int colon = s.lastIndexOf(':');
        if (colon >= 0) {
            s = s.substring(colon + 1);
        }

        int slash = s.lastIndexOf('/');
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }

        return s;
    }

    private static String fileNameWithoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Minimal JSON string escaping for the manifest writer. */
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static long seed(String a, String b) {
        return (a + ":" + b).hashCode();
    }

    private static float clampF(float v) {
        if (v < 0.0f) return 0.0f;
        if (v > 255.0f) return 255.0f;
        return v;
    }

    private static int clampI(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }
}