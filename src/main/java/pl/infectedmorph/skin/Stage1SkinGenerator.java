package pl.infectedmorph.skin;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/** Local masks preserve alpha, UV orientation and untouched features; there is no global skin tint. */
public final class Stage1SkinGenerator {
    public BufferedImage generate(BufferedImage original, SkinModel model, UUID player,
                                  InfectionIntensity intensity, EyeTreatment eyes) {
        Objects.requireNonNull(model, "Skin model is required.");
        Objects.requireNonNull(player, "Player UUID is required.");
        Objects.requireNonNull(intensity, "Infection intensity is required.");
        Objects.requireNonNull(eyes, "Eye settings are required.");
        if (original.getWidth() != 64 || original.getHeight() != 64) {
            throw new IllegalArgumentException("Only standard 64x64 PNG skins are supported; legacy, HD and persona conversion is unavailable.");
        }
        int[] source = original.getRGB(0, 0, 64, 64, null, 0, 64);
        BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, 64, 64, source, 0, 64);
        long seed = player.getMostSignificantBits() ^ player.getLeastSignificantBits();
        for (int pixel : source) seed = seed * 31 + pixel;
        List<EyeTreatment.Pixel> eyePixels = eyes.locate(original);
        List<EyeTreatment.Pixel> protectedEyes = eyes.mode() == EyeTreatment.Mode.OFF
                ? EyeTreatment.automatic().locate(original) : eyePixels;
        double[] bruises = new double[4096], cuts = new double[4096];
        int index = 0;
        for (SkinUv.Region region : SkinUv.surfaces(model)) {
            // Separate RNG per surface keeps existing marks stable as intensity adds more lesions.
            long surfaceSeed = seed ^ (0x9e3779b97f4a7c15L * ++index);
            Random random = new Random(surfaceSeed);
            if (region.part() == SkinUv.Part.FACE) {
                for (int side : new int[]{0, 6}) {
                    var cheek = new SkinUv.Region(SkinUv.Part.FACE, region.x() + side, region.y() + 5, 2, 3, region.outer());
                    lesion(bruises, cuts, cheek, intensity, random, surfaceSeed);
                }
            } else {
                for (int lesion = 0; lesion < intensity.lesions; lesion++) {
                    lesion(bruises, cuts, region, intensity, random, surfaceSeed + lesion * 101L);
                }
            }
        }
        for (EyeTreatment.Pixel eye : protectedEyes) {
            for (int offset : new int[]{8, 40}) {
                int i = (8 + eye.y()) * 64 + offset + eye.x();
                bruises[i] = 0; cuts[i] = 0;
            }
        }
        for (int i = 0; i < source.length; i++) {
            if ((source[i] >>> 24) == 0) continue;
            int x = i % 64, y = i / 64;
            if (x >= 40 && x < 48 && y >= 8 && y < 16 && EyeTreatment.distance(source[i], source[i - 32]) > 60) continue;
            int pixel = tint(source[i], 0x58385f, bruises[i]);
            pixel = tint(pixel, 0x922e32, cuts[i]);
            result.setRGB(i % 64, i / 64, pixel);
        }
        for (EyeTreatment.Pixel eye : eyePixels) {
            darkenEye(result, original, 8 + eye.x(), 8 + eye.y(), intensity);
            // Do not darken unrelated opaque hat/hair pixels over an eye.
            int base = original.getRGB(8 + eye.x(), 8 + eye.y());
            int overlay = original.getRGB(40 + eye.x(), 8 + eye.y());
            if (EyeTreatment.distance(base, overlay) < 45) darkenEye(result, original, 40 + eye.x(), 8 + eye.y(), intensity);
        }
        return result;
    }

    private void lesion(double[] bruises, double[] cuts, SkinUv.Region region, InfectionIntensity intensity, Random random, long seed) {
        int centerX = random.nextInt(region.width());
        int centerY = region.part() == SkinUv.Part.FACE ? 1 : 3 + random.nextInt(4);
        double radiusX = Math.min(intensity.radius, region.width() * .65);
        double radiusY = intensity.radius * 1.2;
        for (int y = 0; y < region.height(); y++) for (int x = 0; x < region.width(); x++) {
            if (region.part() != SkinUv.Part.FACE && (y < 1 || y >= region.height() - 1)) continue;
            double dx = (x - centerX) / radiusX, dy = (y - centerY) / radiusY;
            double noise = ((seed ^ (x * 374761393L) ^ (y * 668265263L)) & 255) / 255.0;
            double distance = Math.sqrt(dx * dx + dy * dy);
            double edge = .80 + noise * .35;
            double strength = Math.max(0, Math.min(1, (edge - distance) * 1.8));
            put(bruises, region, x, y, intensity.bruiseOpacity * strength);
        }
        int startX = random.nextInt(region.width());
        int startY = region.part() == SkinUv.Part.FACE ? 0 : 2 + random.nextInt(4);
        int direction = random.nextBoolean() ? 1 : -1;
        int length = 2 + intensity.ordinal();
        for (int step = 0; step < length; step++) {
            int x = startX + direction * (step / 2), y = startY + step;
            put(cuts, region, x, y, intensity.cutOpacity);
            put(cuts, region, x - direction, y, intensity.cutOpacity * .22);
        }
    }

    private void put(double[] mask, SkinUv.Region region, int x, int y, double strength) {
        if (x < 0 || x >= region.width() || y < 0 || y >= region.height()) return;
        int index = (region.y() + y) * 64 + region.x() + x;
        mask[index] = Math.max(mask[index], strength); // Overlap cannot make marks arbitrarily dark.
    }

    private int tint(int original, int color, double amount) {
        int result = original & 0xff000000;
        for (int shift : new int[]{16, 8, 0}) {
            int channel = (int) Math.round(((original >> shift) & 255) * (1 - amount) + ((color >> shift) & 255) * amount);
            result |= channel << shift;
        }
        return result;
    }

    private void darkenEye(BufferedImage result, BufferedImage original, int x, int y, InfectionIntensity intensity) {
        int pixel = original.getRGB(x, y);
        if ((pixel >>> 24) == 0) return;
        int r = (pixel >> 16) & 255, g = (pixel >> 8) & 255, b = pixel & 255;
        double gray = (r + g + b) / 3.0;
        int output = pixel & 0xff000000;
        int[] channels = {r, g, b};
        for (int i = 0; i < 3; i++) {
            int channel = (int) Math.round((channels[i] * .55 + gray * .45) * intensity.eyeBrightness);
            output |= channel << (16 - i * 8);
        }
        result.setRGB(x, y, output);
    }
}
