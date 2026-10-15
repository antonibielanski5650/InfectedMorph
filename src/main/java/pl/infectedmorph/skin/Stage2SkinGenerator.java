package pl.infectedmorph.skin;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Always starts with the original pixels. Reuses the approved bruise/eye pass without changing Stage 1. */
public final class Stage2SkinGenerator {
    public BufferedImage generate(BufferedImage original, SkinModel model, UUID player,
                                  InfectionIntensity intensity, EyeTreatment eyes) {
        BufferedImage result = new Stage1SkinGenerator().generate(original, model, player, intensity, eyes);
        long seed = player.getMostSignificantBits() ^ player.getLeastSignificantBits() ^ 0x535441474532L;
        for (int pixel : original.getRGB(0, 0, 64, 64, null, 0, 64)) seed = seed * 31 + pixel;
        Set<Integer> protectedEyes = new HashSet<>();
        var eyePixels = eyes.mode() == EyeTreatment.Mode.OFF ? EyeTreatment.automatic().locate(original) : eyes.locate(original);
        for (var eye : eyePixels) for (int offset : new int[]{8, 40}) protectedEyes.add((8 + eye.y()) * 64 + offset + eye.x());
        int surface = 0;
        for (var region : SkinUv.surfaces(model)) {
            Random random = new Random(seed ^ (++surface * 0x9e3779b97f4a7c15L));
            Set<Integer> cuts = new java.util.LinkedHashSet<>();
            int count = 2 + intensity.ordinal();
            for (int line = 0; line < count; line++) {
                int x = random.nextInt(region.width());
                int start = region.part() == SkinUv.Part.FACE ? 3 : 1 + random.nextInt(3);
                int direction = random.nextBoolean() ? 1 : -1;
                int length = 5 + intensity.ordinal();
                for (int step = 0; step < length; step++) {
                    int px = x + direction * (step / 3), py = start + step;
                    if (px < 0 || px >= region.width() || py >= region.height() - 1) continue;
                    // Keep the central nose/mouth, upper hairline and detected eyes intact.
                    if (region.part() == SkinUv.Part.FACE && px >= 2 && px <= 5) continue;
                    int index = (region.y() + py) * 64 + region.x() + px;
                    if (!protectedEyes.contains(index)) cuts.add(index);
                }
            }
            // Per-surface cap prevents high intensity from turning clothing into a solid red patch.
            int budget = Math.max(1, (int) (region.width() * region.height() * .26));
            for (int index : cuts) {
                int x = index % 64, y = index / 64;
                int source = original.getRGB(x, y);
                if ((source >>> 24) == 0) continue;
                if (region.part() == SkinUv.Part.FACE && region.outer()
                        && EyeTreatment.distance(source, original.getRGB(x - 32, y)) > 60) continue;
                if (budget-- <= 0) break;
                // One bounded tint per pixel; overlaps never accumulate opacity.
                double amount = .66 + intensity.ordinal() * .08;
                int before = result.getRGB(x, y), after = source & 0xff000000;
                int wound = 0x9d3439;
                for (int shift : new int[]{16, 8, 0}) {
                    after |= (int) Math.round(((before >>> shift) & 255) * (1 - amount)
                            + ((wound >>> shift) & 255) * amount) << shift;
                }
                result.setRGB(x, y, after);
            }
        }
        return result;
    }
}
