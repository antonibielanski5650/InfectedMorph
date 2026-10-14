package pl.infectedmorph.skin;

import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.UUID;

/** No geometry changes: a few alpha-preserving marks over a copy of the original UV map. */
public final class Stage1SkinGenerator {
    public BufferedImage generate(BufferedImage original, SkinModel model, UUID player, boolean darkenEyes) {
        if (original.getWidth() != 64 || original.getHeight() != 64) {
            throw new IllegalArgumentException("Obsługiwane są tylko standardowe skiny 64x64 PNG; bez konwersji legacy/HD/persona.");
        }
        BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        long seed = player.getMostSignificantBits() ^ player.getLeastSignificantBits();
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int pixel = original.getRGB(x, y);
            result.setRGB(x, y, pixel);
            seed = seed * 31 + pixel;
        }
        Random random = new Random(seed);
        // Lower cheeks only; leave top of head, hair, mouth and most of the face intact.
        mark(result, 9 + random.nextInt(2), 13, 0x793e49, 0.30);
        mark(result, 13 + random.nextInt(2), 14, 0x8c3334, 0.36);
        // Front arms and lower legs. Repeat on existing outer-layer pixels only.
        arm(result, 44, 20, model, random);
        arm(result, 44, 36, model, random);
        arm(result, 36, 52, model, random);
        arm(result, 52, 52, model, random);
        scratch(result, 4 + random.nextInt(2), 27);
        scratch(result, 4 + random.nextInt(2), 43);
        scratch(result, 20 + random.nextInt(2), 59);
        scratch(result, 4 + random.nextInt(2), 59);
        // Head overlay: never fill transparent pixels or paint over hair above the cheeks.
        mark(result, 41, 13, 0x793e49, 0.25);
        mark(result, 45, 14, 0x8c3334, 0.30);
        if (darkenEyes) {
            for (int x : new int[]{9, 10, 13, 14}) {
                dullEye(result, x, 12);
                dullEye(result, x + 32, 12);
            }
        }
        return result;
    }

    private void arm(BufferedImage image, int x, int y, SkinModel model, Random random) {
        int offset = random.nextInt(model.armWidth() - 1);
        mark(image, x + offset, y + 7, 0x674561, 0.28);
        scratch(image, x + offset, y + 9);
    }

    private void scratch(BufferedImage image, int x, int y) {
        mark(image, x, y, 0x8c3334, 0.40);
        mark(image, x + 1, y + 1, 0x8c3334, 0.32);
    }

    private void mark(BufferedImage image, int x, int y, int tint, double amount) {
        int old = image.getRGB(x, y);
        if ((old >>> 24) == 0) return;
        int r = mix((old >> 16) & 255, (tint >> 16) & 255, amount);
        int g = mix((old >> 8) & 255, (tint >> 8) & 255, amount);
        int b = mix(old & 255, tint & 255, amount);
        image.setRGB(x, y, (old & 0xff000000) | r << 16 | g << 8 | b);
    }

    private void dullEye(BufferedImage image, int x, int y) {
        int p = image.getRGB(x, y);
        if ((p >>> 24) == 0) return;
        int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
        int gray = (r + g + b) / 3;
        image.setRGB(x, y, (p & 0xff000000) | (int)(mix(r, gray, .25) * .78) << 16
                | (int)(mix(g, gray, .25) * .78) << 8 | (int)(mix(b, gray, .25) * .78));
    }

    private int mix(int original, int tint, double amount) {
        return (int) Math.round(original * (1 - amount) + tint * amount);
    }
}
