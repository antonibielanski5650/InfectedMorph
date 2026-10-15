package pl.infectedmorph.skin;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Conservative feature heuristic, not semantic face recognition. Coordinates are local to the 8x8 face. */
public record EyeTreatment(Mode mode, List<Pixel> pixels) {
    public enum Mode { AUTO, CUSTOM, OFF }
    public record Pixel(int x, int y) {
        public Pixel {
            if (x < 0 || x > 7 || y < 0 || y > 7) throw new IllegalArgumentException("Eye coordinates must be within the 8x8 face (0..7).");
        }
    }

    public EyeTreatment {
        Objects.requireNonNull(mode, "Eye mode is required.");
        pixels = List.copyOf(pixels);
        if (pixels.size() > 64) throw new IllegalArgumentException("At most 64 eye pixels may be configured.");
    }

    public static EyeTreatment automatic() { return new EyeTreatment(Mode.AUTO, List.of()); }
    public static EyeTreatment off() { return new EyeTreatment(Mode.OFF, List.of()); }

    public List<Pixel> locate(BufferedImage skin) {
        if (mode == Mode.OFF) return List.of();
        if (mode == Mode.CUSTOM) return pixels;
        // Search multiple rows; do not assume the standard eye row. Require isolated, bilateral features.
        double best = 55, runnerUp = 0;
        int row = -1;
        for (int y = 2; y <= 5; y++) {
            double score = 0;
            for (int left = 1; left <= 2; left++) for (int right = 5; right <= 6; right++) {
                int a = skin.getRGB(8 + left, 8 + y), b = skin.getRGB(8 + right, 8 + y);
                if ((a >>> 24) != 255 || (b >>> 24) != 255) continue;
                score = Math.max(score, Math.min(contrast(skin, left, y), contrast(skin, right, y)) - distance(a, b) * .25);
            }
            if (score > best) { runnerUp = best == 55 ? 0 : best; best = score; row = y; }
            else runnerUp = Math.max(runnerUp, score);
        }
        if (row < 0 || runnerUp > best * .85) return List.of();
        List<Pixel> result = new ArrayList<>();
        for (int x : new int[]{1, 2, 5, 6}) if (contrast(skin, x, row) > 45) result.add(new Pixel(x, row));
        return List.copyOf(result);
    }

    private static double contrast(BufferedImage skin, int x, int y) {
        int center = skin.getRGB(8 + x, 8 + y), above = skin.getRGB(8 + x, 7 + y), below = skin.getRGB(8 + x, 9 + y);
        if ((center >>> 24) != 255 || (above >>> 24) != 255 || (below >>> 24) != 255) return 0;
        // A skin-colored row between hair and eyes is not itself an eye feature.
        if (distance(above, below) > 60) return 0;
        return Math.min(distance(center, above), distance(center, below));
    }

    static double distance(int a, int b) {
        int r = ((a >> 16) & 255) - ((b >> 16) & 255), g = ((a >> 8) & 255) - ((b >> 8) & 255), blue = (a & 255) - (b & 255);
        return Math.sqrt(r * r + g * g + blue * blue);
    }
}
