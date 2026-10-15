package pl.infectedmorph.skin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Stage1SkinGeneratorTest {
    private final Stage1SkinGenerator generator = new Stage1SkinGenerator();
    private final UUID id = UUID.fromString("e1e0fa60-0e40-4ec3-874b-123456789abc");

    private BufferedImage fixture(boolean alpha) {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int opacity = alpha ? new int[]{0, 128, 255}[(x + y) % 3] : 255;
            image.setRGB(x, y, opacity << 24 | (80 + x * 2) << 16 | (60 + y * 2) << 8 | 110);
        }
        return image;
    }

    @ParameterizedTest @EnumSource(InfectionIntensity.class)
    void preservesInputAlphaHairFeaturesAndUvAtEveryIntensity(InfectionIntensity intensity) {
        for (SkinModel model : SkinModel.values()) {
            BufferedImage original = fixture(true);
            int[] snapshot = pixels(original);
            BufferedImage result = generator.generate(original, model, id, intensity, EyeTreatment.off());
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                int before = original.getRGB(x, y), after = result.getRGB(x, y);
                assertEquals(before >>> 24, after >>> 24, "Alpha at " + x + "," + y);
                if ((before >>> 24) == 0 || !allowed(model, x, y)) assertEquals(before, after, "Protected UV at " + x + "," + y);
                // Protect the central nose and mouth, and the upper forehead/hairline.
                if ((x >= 10 && x <= 13 && y >= 13 && y <= 15) || (x >= 8 && x < 16 && y >= 8 && y < 10)) {
                    assertEquals(before, after, "Protected facial feature");
                }
            }
            assertArrayEquals(snapshot, pixels(original), "Input image must remain byte-for-byte intact as ARGB pixels");
            assertEquals(64, result.getWidth());
            assertEquals(64, result.getHeight());
        }
    }

    // Independently specified Minecraft UV side strips; no production region helper is used here.
    private boolean allowed(SkinModel model, int x, int y) {
        int armStrip = model == SkinModel.SLIM ? 14 : 16;
        int[][] strips = {{8,8,8,8},{40,8,8,8},{16,20,24,12},{16,36,24,12},
                {40,20,armStrip,12},{40,36,armStrip,12},{32,52,armStrip,12},{48,52,armStrip,12},
                {0,20,16,12},{0,36,16,12},{16,52,16,12},{0,52,16,12}};
        for (int[] r : strips) if (x >= r[0] && x < r[0]+r[2] && y >= r[1] && y < r[1]+r[3]) return true;
        return false;
    }

    @ParameterizedTest @EnumSource(SkinModel.class)
    void intensityIncreasesCoverageAndColorChangeWithoutCoveringTheSkin(SkinModel model) {
        for (int seed = 0; seed < 12; seed++) {
            var source = fixture(false);
            long previousDistance = 0;
            int previousCount = 0;
            BufferedImage previous = null;
            for (InfectionIntensity intensity : InfectionIntensity.values()) {
                var result = generator.generate(source, model, new UUID(seed, 42), intensity, EyeTreatment.off());
                int count = changed(source, result);
                long distance = distance(source, result);
                assertTrue(count > previousCount, "Coverage must increase at " + intensity);
                assertTrue(distance > previousDistance, "Color contrast must increase at " + intensity);
                assertTrue(count < 4096 * .30, "At least 70% of all original pixels must remain untouched");
                if (previous != null) {
                    int[] before=pixels(previous), original=pixels(source), after=pixels(result);
                    for (int i = 0; i < 4096; i++) {
                        if (before[i] != original[i]) assertNotEquals(original[i], after[i], "Existing marks must not disappear");
                    }
                }
                previousDistance = distance; previousCount = count; previous = result;
            }
        }
    }

    @ParameterizedTest @EnumSource(SkinModel.class)
    void mediumHasVisibleMarksOnEveryFrontBodyPart(SkinModel model) {
        var source = fixture(false);
        var result = generator.generate(source, model, id, InfectionIntensity.MEDIUM, EyeTreatment.off());
        for (int[] r : new int[][]{{8,8,8,8},{20,20,8,12},{44,20,model.armWidth(),12},
                {36,52,model.armWidth(),12},{4,20,4,12},{20,52,4,12}}) {
            int changes = 0;
            for (int y = r[1]; y < r[1]+r[3]; y++) for (int x = r[0]; x < r[0]+r[2]; x++) {
                if (source.getRGB(x,y) != result.getRGB(x,y)) changes++;
            }
            assertTrue(changes >= 5, "Visible marks required in front region " + Arrays.toString(r));
        }
    }

    @Test void deterministicForEveryIntensityAndSensitiveToPlayerAndOriginalSkin() {
        var source = fixture(false);
        for (InfectionIntensity intensity : InfectionIntensity.values()) {
            var a = generator.generate(source, SkinModel.WIDE, id, intensity, EyeTreatment.off());
            var b = generator.generate(source, SkinModel.WIDE, id, intensity, EyeTreatment.off());
            var c = generator.generate(source, SkinModel.WIDE, new UUID(5, 7), intensity, EyeTreatment.off());
            assertArrayEquals(pixels(a), pixels(b));
            assertFalse(Arrays.equals(pixels(a), pixels(c)));
            var modified = fixture(false);
            modified.setRGB(0, 0, 0xff445566);
            var d = generator.generate(modified, SkinModel.WIDE, id, intensity, EyeTreatment.off());
            int[] left=pixels(a),right=pixels(d);
            left[0]=right[0];
            assertFalse(Arrays.equals(left,right), "Changing original pixels must influence the lesion seed");
        }
    }

    @Test void entirelyTransparentPixelsIncludingHiddenRgbStayUntouched() {
        var source = new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<64;y++) for(int x=0;x<64;x++) source.setRGB(x,y,0x00123456);
        for (InfectionIntensity intensity : InfectionIntensity.values()) {
            assertArrayEquals(pixels(source), pixels(generator.generate(source,SkinModel.SLIM,id,intensity,EyeTreatment.automatic())));
        }
    }

    @ParameterizedTest @EnumSource(SkinModel.class)
    void realUvFixturesRetainMostVisiblePixelsEvenAtHighIntensity(SkinModel model) {
        var source = Stage1PreviewSamples.sample(model);
        int visible = 0;
        for (int pixel : pixels(source)) if ((pixel >>> 24) != 0) visible++;
        for (InfectionIntensity intensity : InfectionIntensity.values()) {
            var result = generator.generate(source, model, id, intensity, EyeTreatment.automatic());
            assertTrue(changed(source, result) < visible * .45,
                    "Most visible original pixels must remain untouched even for cinematic intensity");
        }
    }

    @Test void unrelatedOpaqueFaceOverlayHairIsPreserved() {
        var source = Stage1PreviewSamples.sample(SkinModel.WIDE);
        for (int y = 8; y < 16; y++) for (int x = 40; x < 48; x++) source.setRGB(x,y,0xff17131d);
        var result = generator.generate(source, SkinModel.WIDE, id, InfectionIntensity.HIGH, EyeTreatment.automatic());
        for (int y = 8; y < 16; y++) for (int x = 40; x < 48; x++) {
            assertEquals(source.getRGB(x,y), result.getRGB(x,y), "Contrasting hair overlay must be preserved");
        }
    }

    @Test void customEyesDarkenProgressivelyAndCanBeDisabled() {
        var source = fixture(false);
        var eyes = new EyeTreatment(EyeTreatment.Mode.CUSTOM, List.of(new EyeTreatment.Pixel(3,2)));
        int previous = 1000;
        for (InfectionIntensity intensity : InfectionIntensity.values()) {
            var result = generator.generate(source,SkinModel.WIDE,id,intensity,eyes);
            int color = result.getRGB(11,10);
            int brightness = ((color>>16)&255)+((color>>8)&255)+(color&255);
            assertTrue(brightness < previous);
            previous=brightness;
            var disabled = generator.generate(source,SkinModel.WIDE,id,intensity,EyeTreatment.off());
            assertEquals(source.getRGB(11,10), disabled.getRGB(11,10));
        }
    }

    @Test void rejectsLegacyAndHdInsteadOfSilentlyChangingLayout() {
        for (int[] size : new int[][]{{64,32},{128,128},{32,32}}) {
            assertThrows(IllegalArgumentException.class, () -> generator.generate(
                    new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB), SkinModel.WIDE, id, InfectionIntensity.MEDIUM, EyeTreatment.off()));
        }
    }

    static int[] pixels(BufferedImage image) { return image.getRGB(0,0,64,64,null,0,64); }
    private int changed(BufferedImage a, BufferedImage b) {
        int count=0; int[] left=pixels(a),right=pixels(b);
        for(int i=0;i<left.length;i++) if(left[i]!=right[i])count++;
        return count;
    }
    private long distance(BufferedImage a, BufferedImage b) {
        long sum=0;int[] left=pixels(a),right=pixels(b);
        for(int i=0;i<left.length;i++) for(int shift:new int[]{0,8,16})sum+=Math.abs(((left[i]>>shift)&255)-((right[i]>>shift)&255));
        return sum;
    }
}
