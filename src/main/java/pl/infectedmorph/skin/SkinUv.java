package pl.infectedmorph.skin;

import java.util.ArrayList;
import java.util.List;

/** Minecraft 64x64 UV side surfaces. Caps and unused slim columns are deliberately excluded. */
public final class SkinUv {
    public enum Part { FACE, TORSO, ARM, LEG }
    public record Region(Part part, int x, int y, int width, int height, boolean outer) {
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private SkinUv() {}

    public static List<Region> surfaces(SkinModel model) {
        List<Region> regions = new ArrayList<>();
        regions.add(new Region(Part.FACE, 8, 8, 8, 8, false));
        regions.add(new Region(Part.FACE, 40, 8, 8, 8, true));
        box(regions, Part.TORSO, 16, 16, 8, 4, 12, false);
        box(regions, Part.TORSO, 16, 32, 8, 4, 12, true);
        box(regions, Part.ARM, 40, 16, model.armWidth(), 4, 12, false);
        box(regions, Part.ARM, 40, 32, model.armWidth(), 4, 12, true);
        box(regions, Part.ARM, 32, 48, model.armWidth(), 4, 12, false);
        box(regions, Part.ARM, 48, 48, model.armWidth(), 4, 12, true);
        box(regions, Part.LEG, 0, 16, 4, 4, 12, false);
        box(regions, Part.LEG, 0, 32, 4, 4, 12, true);
        box(regions, Part.LEG, 16, 48, 4, 4, 12, false);
        box(regions, Part.LEG, 0, 48, 4, 4, 12, true);
        return List.copyOf(regions);
    }

    private static void box(List<Region> regions, Part part, int u, int v, int width, int depth, int height, boolean outer) {
        regions.add(new Region(part, u, v + depth, depth, height, outer));
        regions.add(new Region(part, u + depth, v + depth, width, height, outer));
        regions.add(new Region(part, u + depth + width, v + depth, depth, height, outer));
        regions.add(new Region(part, u + 2 * depth + width, v + depth, width, height, outer));
    }
}
