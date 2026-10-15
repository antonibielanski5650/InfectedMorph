package pl.infectedmorph.skin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.awt.image.BufferedImage;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Stage2SkinGeneratorTest {
    private final Stage2SkinGenerator generator = new Stage2SkinGenerator();

    static int[] pixels(BufferedImage image) { return image.getRGB(0, 0, 64, 64, null, 0, 64); }
    static int scratchPixels(BufferedImage image) {
        int count = 0;
        for (int pixel : pixels(image)) {
            int r = (pixel >>> 16) & 255, g = (pixel >>> 8) & 255, b = pixel & 255;
            if ((pixel >>> 24) != 0 && r > 90 && r > g * 1.4 && r > b * 1.3) count++;
        }
        return count;
    }

    @ParameterizedTest @EnumSource(SkinModel.class)
    void hasGreaterVisibleScratchCoverageThanApprovedStage1AtEveryIntensity(SkinModel model) {
        for (var intensity : InfectionIntensity.values()) for (int player = 0; player < 24; player++) {
            var original = Stage1PreviewSamples.sample(model);
            var id = new UUID(player, 42);
            var first = new Stage1SkinGenerator().generate(original, model, id, intensity, EyeTreatment.automatic());
            var second = generator.generate(original, model, id, intensity, EyeTreatment.automatic());
            assertTrue(scratchPixels(second) > scratchPixels(first) * 1.1,
                    model + " " + intensity + " must add visible red-cut pixels, rather than just bruise tint");
        }
    }

    @ParameterizedTest @EnumSource(SkinModel.class)
    void deterministicFromOriginalAndLeavesSourceAndStage1OutputIntact(SkinModel model) {
        var original = Stage1PreviewSamples.sample(model); int[] before = pixels(original);
        var id = new UUID(11, 22);
        var first = new Stage1SkinGenerator().generate(original, model, id, InfectionIntensity.MEDIUM, EyeTreatment.automatic());
        int[] approved = pixels(first);
        var second = generator.generate(original, model, id, InfectionIntensity.MEDIUM, EyeTreatment.automatic());
        assertArrayEquals(pixels(second), pixels(generator.generate(original, model, id, InfectionIntensity.MEDIUM, EyeTreatment.automatic())));
        assertArrayEquals(before, pixels(original)); assertArrayEquals(approved, pixels(first));
        assertArrayEquals(approved, pixels(new Stage1SkinGenerator().generate(original, model, id, InfectionIntensity.MEDIUM, EyeTreatment.automatic())));
        assertFalse(java.util.Arrays.equals(pixels(second), pixels(generator.generate(original, model, new UUID(12,22), InfectionIntensity.MEDIUM, EyeTreatment.automatic()))));
    }

    @ParameterizedTest @EnumSource(SkinModel.class)
    void preservesEveryAlphaAndAllPixelsOutsideExactUvSurfacesIncludingSlimPadding(SkinModel model) {
        var original = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) original.setRGB(x, y, ((x + y) % 4 * 85 << 24) | 0x3e8694);
        var output = generator.generate(original, model, new UUID(3,9), InfectionIntensity.HIGH, EyeTreatment.off());
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int px = x, py = y, source = original.getRGB(x, y), result = output.getRGB(x, y);
            assertEquals(source >>> 24, result >>> 24, "Alpha at " + x + "," + y);
            if ((source >>> 24) == 0 || SkinUv.surfaces(model).stream().noneMatch(r -> r.contains(px,py))) assertEquals(source, result);
        }
    }

    @ParameterizedTest @EnumSource(SkinModel.class)
    void addsMarksToFaceTorsoArmsLegsBackAndSidesWithoutBlanketingOriginal(SkinModel model) {
        var original = Stage1PreviewSamples.sample(model);
        var first = new Stage1SkinGenerator().generate(original, model, new UUID(4,7), InfectionIntensity.MEDIUM, EyeTreatment.automatic());
        var output = generator.generate(original, model, new UUID(4,7), InfectionIntensity.MEDIUM, EyeTreatment.automatic());
        for (var region : SkinUv.surfaces(model)) {
            if (region.outer()) continue;
            int added = 0;
            for (int y = region.y(); y < region.y() + region.height(); y++) for (int x = region.x(); x < region.x() + region.width(); x++)
                if (first.getRGB(x,y) != output.getRGB(x,y)) added++;
            assertTrue(added > 0, "Additional marks required on every base side: " + region);
            assertTrue(added <= Math.max(1, (int) (region.width() * region.height() * .26)), "Additional cut coverage is capped");
        }
        int unchanged = 0, opaque = 0;
        int[] sourcePixels = pixels(original), outputPixels = pixels(output);
        for (int i = 0; i < 4096; i++) if ((sourcePixels[i] >>> 24) != 0) {
            opaque++; if (sourcePixels[i] == outputPixels[i]) unchanged++;
        }
        assertTrue(unchanged > opaque * .5, "Most opaque original pixels remain unchanged");
    }

    @Test void protectsCustomEyeCoordinatesHairMouthAndNose() {
        var original = Stage1PreviewSamples.sample(SkinModel.WIDE);
        var eyes = new EyeTreatment(EyeTreatment.Mode.CUSTOM, java.util.List.of(new EyeTreatment.Pixel(0,5), new EyeTreatment.Pixel(7,5)));
        var first = new Stage1SkinGenerator().generate(original, SkinModel.WIDE, new UUID(9,1), InfectionIntensity.HIGH, eyes);
        var output = generator.generate(original, SkinModel.WIDE, new UUID(9,1), InfectionIntensity.HIGH, eyes);
        for (var eye : eyes.locate(original)) assertEquals(first.getRGB(8 + eye.x(),8 + eye.y()), output.getRGB(8 + eye.x(),8 + eye.y()));
        for (int y = 8; y < 16; y++) for (int x = 10; x <= 13; x++) assertEquals(first.getRGB(x,y), output.getRGB(x,y));
        for (int x = 8; x < 16; x++) assertEquals(first.getRGB(x,8), output.getRGB(x,8));
    }
}
