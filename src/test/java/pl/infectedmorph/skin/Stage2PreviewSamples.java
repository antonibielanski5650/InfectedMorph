package pl.infectedmorph.skin;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Original / approved Stage 1 / Stage 2 front, back and UV samples; not Minecraft screenshots. */
public final class Stage2PreviewSamples {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? "docs/stage2-previews" : args[0]);
        Files.createDirectories(directory);
        UUID id = UUID.fromString("e1e0fa60-0e40-4ec3-874b-123456789abc");
        for (SkinModel model : SkinModel.values()) for (InfectionIntensity intensity : InfectionIntensity.values()) {
            String name = (model == SkinModel.WIDE ? "classic" : "slim") + "-" + intensity.configValue();
            var original = Stage1PreviewSamples.sample(model);
            var first = new Stage1SkinGenerator().generate(original, model, id, intensity, EyeTreatment.automatic());
            var second = new Stage2SkinGenerator().generate(original, model, id, intensity, EyeTreatment.automatic());
            ImageIO.write(original, "png", directory.resolve(name + "-original.png").toFile());
            ImageIO.write(first, "png", directory.resolve(name + "-stage1.png").toFile());
            ImageIO.write(second, "png", directory.resolve(name + "-stage2.png").toFile());
            ImageIO.write(sheet(model, intensity, new BufferedImage[]{original, first, second}), "png", directory.resolve(name + "-comparison.png").toFile());
            System.out.println(name + ": Stage 1 scratch pixels=" + scratchPixels(first)
                    + "; Stage 2 scratch pixels=" + scratchPixels(second));
        }
    }

    private static int scratchPixels(BufferedImage image) {
        int count = 0;
        for (int pixel : image.getRGB(0,0,64,64,null,0,64)) {
            int r = (pixel >>> 16) & 255, g = (pixel >>> 8) & 255, b = pixel & 255;
            if ((pixel >>> 24) != 0 && r > 90 && r > g * 1.4 && r > b * 1.3) count++;
        }
        return count;
    }

    private static BufferedImage sheet(SkinModel model, InfectionIntensity intensity, BufferedImage[] skins) {
        BufferedImage sheet = new BufferedImage(960, 510, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(27,32,42)); g.fillRect(0,0,960,510);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        String[] labels = {"Original", "Stage 1 (unchanged)", "Stage 2"};
        for (int i = 0; i < skins.length; i++) {
            g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            g.drawString(labels[i], i * 320 + 24, 26);
            for (boolean back : new boolean[]{false,true}) {
                BufferedImage figure = new BufferedImage(16,32,BufferedImage.TYPE_INT_ARGB);
                Graphics2D f = figure.createGraphics();
                if (back) {
                    part(f,skins[i],4,0,8,8,24,8); part(f,skins[i],4,0,8,8,56,8);
                    part(f,skins[i],4,8,8,12,32,20); part(f,skins[i],4,8,8,12,32,36);
                } else {
                    part(f,skins[i],4,0,8,8,8,8); part(f,skins[i],4,0,8,8,40,8);
                    part(f,skins[i],4,8,8,12,20,20); part(f,skins[i],4,8,8,12,20,36);
                }
                int w = model.armWidth(), armOffset = back ? w + 4 : 0;
                part(f,skins[i],4-w,8,w,12,44+armOffset,20); part(f,skins[i],4-w,8,w,12,44+armOffset,36);
                part(f,skins[i],12,8,w,12,36+armOffset,52); part(f,skins[i],12,8,w,12,52+armOffset,52);
                int legOffset = back ? 8 : 0;
                part(f,skins[i],4,20,4,12,4+legOffset,20); part(f,skins[i],4,20,4,12,4+legOffset,36);
                part(f,skins[i],8,20,4,12,20+legOffset,52); part(f,skins[i],8,20,4,12,4+legOffset,52);
                f.dispose();
                int x = i * 320 + (back ? 170 : 20);
                g.drawImage(figure,x,50,128,256,null);
                g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));g.drawString(back ? "Back" : "Front",x+45,44);
            }
            g.drawImage(skins[i],i*320+96,326,128,128,null);
        }
        g.setColor(Color.LIGHT_GRAY);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
        g.drawString("Synthetic " + model.variant() + " / " + intensity.configValue() + ": flat views and UV maps. Not a Minecraft screenshot.",20,490);
        g.dispose();return sheet;
    }

    private static void part(Graphics2D g, BufferedImage skin, int x, int y, int w, int h, int u, int v) {
        g.drawImage(skin,x,y,x+w,y+h,u,v,u+w,v+h,null);
    }
}
