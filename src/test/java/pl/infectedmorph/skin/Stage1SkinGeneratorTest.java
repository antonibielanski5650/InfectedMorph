package pl.infectedmorph.skin;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Stage1SkinGeneratorTest {
    private final Stage1SkinGenerator generator = new Stage1SkinGenerator();
    private final UUID id = UUID.fromString("e1e0fa60-0e40-4ec3-874b-123456789abc");

    private BufferedImage fixture() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            image.setRGB(x, y, ((x + y) % 3 == 0 ? 0 : (x + y) % 3 == 1 ? 0x80000000 : 0xff000000)
                    | ((x * 3 + 60) << 16) | ((y * 2 + 50) << 8) | 90);
        }
        return image;
    }

    @Test void preservesOriginalAlphaHairAndMostPixelsForBothModels() {
        for (SkinModel model : SkinModel.values()) {
            BufferedImage original = fixture();
            BufferedImage result = generator.generate(original, model, id, true);
            int changed = 0;
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                int before = original.getRGB(x, y), after = result.getRGB(x, y);
                assertEquals(before >>> 24, after >>> 24, "Alpha at " + x + "," + y);
                if ((before >>> 24) == 0 || y < 12) assertEquals(before, after);
                if (before != after) changed++;
                assertEquals(fixturePixel(x, y), before, "Input must be unchanged");
            }
            assertTrue(changed > 10 && changed < 70, "Conservative marks, changed=" + changed);
            assertEquals(64, result.getWidth());
            assertEquals(64, result.getHeight());
        }
    }

    private int fixturePixel(int x, int y) {
        return ((x + y) % 3 == 0 ? 0 : (x + y) % 3 == 1 ? 0x80000000 : 0xff000000)
                | ((x * 3 + 60) << 16) | ((y * 2 + 50) << 8) | 90;
    }

    @Test void deterministicAndPersonalized() {
        var source = fixture();
        var a = generator.generate(source, SkinModel.WIDE, id, true);
        var b = generator.generate(source, SkinModel.WIDE, id, true);
        var c = generator.generate(source, SkinModel.WIDE, new UUID(5, 7), true);
        boolean different = false;
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            assertEquals(a.getRGB(x, y), b.getRGB(x, y));
            different |= a.getRGB(x, y) != c.getRGB(x, y);
        }
        assertTrue(different);
    }

    @Test void eyeHeuristicCanBeDisabled() {
        var source = fixture();
        var result = generator.generate(source, SkinModel.SLIM, id, false);
        for (int x : new int[]{9, 10, 13, 14, 41, 42, 45, 46}) assertEquals(source.getRGB(x, 12), result.getRGB(x, 12));
    }

    @Test void slimNeverPaintsUnusedArmColumns() {
        var source = fixture();
        var result = generator.generate(source, SkinModel.SLIM, id, true);
        for (int[] arm : new int[][]{{44,20},{44,36},{36,52},{52,52}}) {
            for (int y = arm[1]; y < arm[1] + 12; y++) assertEquals(source.getRGB(arm[0] + 3, y), result.getRGB(arm[0] + 3, y));
        }
    }

    @Test void rejectsLegacyAndHdInsteadOfSilentlyChangingLayout() {
        for (int[] size : new int[][]{{64,32},{128,128},{32,32}}) {
            assertThrows(IllegalArgumentException.class, () -> generator.generate(
                    new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB), SkinModel.WIDE, id, true));
        }
    }
}
