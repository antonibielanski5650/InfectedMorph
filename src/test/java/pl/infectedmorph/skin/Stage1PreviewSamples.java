package pl.infectedmorph.skin;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Reproducible synthetic fixtures and a flat front-view comparison, not an in-game rendering claim. */
public final class Stage1PreviewSamples {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? "docs/previews" : args[0]);
        Files.createDirectories(directory);
        var generator = new Stage1SkinGenerator();
        for (SkinModel model : SkinModel.values()) {
            String name = model == SkinModel.WIDE ? "classic" : "slim";
            BufferedImage original = sample(model);
            ImageIO.write(original, "png", directory.resolve(name + "-original.png").toFile());
            BufferedImage[] skins = new BufferedImage[4];
            skins[0] = original;
            int index = 1;
            for (InfectionIntensity intensity : InfectionIntensity.values()) {
                BufferedImage infected = generator.generate(original, model, UUID.fromString("e1e0fa60-0e40-4ec3-874b-123456789abc"), intensity, EyeTreatment.automatic());
                skins[index++] = infected;
                ImageIO.write(infected, "png", directory.resolve(name + "-" + intensity.configValue() + ".png").toFile());
                System.out.println(name + " " + intensity.configValue() + ": " + changes(original, infected) + " changed pixels; detected eyes=" + EyeTreatment.automatic().locate(original));
            }
            ImageIO.write(comparison(model, skins), "png", directory.resolve(name + "-comparison.png").toFile());
        }
    }

    public static BufferedImage sample(SkinModel model) {
        BufferedImage skin = new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for (var r : SkinUv.surfaces(model)) {
            if(r.outer())continue;
            int color = switch(r.part()) {
                case FACE, ARM -> 0xffca9879;
                case TORSO -> 0xff3e8694;
                case LEG -> 0xff34495e;
            };
            fill(skin,r.x(),r.y(),r.width(),r.height(),color);
            if(r.part()==SkinUv.Part.ARM) fill(skin,r.x(),r.y(),r.width(),4,0xff3e8694);
            if(r.part()==SkinUv.Part.LEG) fill(skin,r.x(),r.y()+10,r.width(),2,0xff292d36);
        }
        fill(skin,0,0,32,8,0xff3b2c2b);
        fill(skin,0,8,8,8,0xff3b2c2b);
        fill(skin,16,8,16,8,0xff3b2c2b);
        fill(skin,8,8,8,2,0xff3b2c2b);
        fill(skin,20,16,16,4,0xff3e8694);
        fill(skin,4,16,8,4,0xff34495e);
        fill(skin,20,48,8,4,0xff34495e);
        fill(skin,44,16,model.armWidth()*2,4,0xff3e8694);
        fill(skin,36,48,model.armWidth()*2,4,0xff3e8694);
        int eyeY = model == SkinModel.SLIM ? 11 : 12;
        fill(skin,9,eyeY,2,1,0xffefeee2);
        fill(skin,13,eyeY,2,1,0xffefeee2);
        skin.setRGB(10,eyeY,0xff477da4);skin.setRGB(13,eyeY,0xff477da4);
        fill(skin,11,14,2,1,0xff81574d);
        fill(skin,23,20,2,2,0xffca9879);
        fill(skin,20,25,8,1,0xff65b4b7);
        fill(skin,20,36,1,10,0xff347281);
        fill(skin,27,36,1,10,0xff347281);
        fill(skin,40,8,8,1,0xff3b2c2b);
        return skin;
    }

    private static void fill(BufferedImage skin,int x,int y,int width,int height,int color) {
        for(int py=y;py<y+height;py++)for(int px=x;px<x+width;px++)skin.setRGB(px,py,color);
    }
    private static int changes(BufferedImage a,BufferedImage b) {
        int changed=0;
        for(int y=0;y<64;y++)for(int x=0;x<64;x++)if(a.getRGB(x,y)!=b.getRGB(x,y))changed++;
        return changed;
    }
    private static BufferedImage comparison(SkinModel model,BufferedImage[] skins) {
        BufferedImage sheet = new BufferedImage(960,560,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=sheet.createGraphics();
        g.setColor(new Color(27,32,42));g.fillRect(0,0,960,560);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        String[] labels={"Original","Low","Medium (default)","High"};
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));
        for(int i=0;i<skins.length;i++) {
            int cx=i*240+120;
            g.setColor(Color.WHITE);g.drawString(labels[i],i*240+24,32);
            BufferedImage front=new BufferedImage(16,32,BufferedImage.TYPE_INT_ARGB);
            Graphics2D f=front.createGraphics();
            part(f,skins[i],4,0,8,8,8,8,8,8);part(f,skins[i],4,0,8,8,40,8,8,8);
            part(f,skins[i],4,8,8,12,20,20,8,12);part(f,skins[i],4,8,8,12,20,36,8,12);
            int w=model.armWidth();
            part(f,skins[i],4-w,8,w,12,44,20,w,12);part(f,skins[i],4-w,8,w,12,44,36,w,12);
            part(f,skins[i],12,8,w,12,36,52,w,12);part(f,skins[i],12,8,w,12,52,52,w,12);
            part(f,skins[i],4,20,4,12,4,20,4,12);part(f,skins[i],4,20,4,12,4,36,4,12);
            part(f,skins[i],8,20,4,12,20,52,4,12);part(f,skins[i],8,20,4,12,4,52,4,12);
            f.dispose();
            g.drawImage(front,cx-80,50,160,320,null);
            g.drawImage(skins[i],cx-64,390,128,128,null);
        }
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));g.setColor(Color.LIGHT_GRAY);
        g.drawString("Synthetic "+model.variant()+" skin: flat front view + original-size UV map scaled 2x. Not a Minecraft screenshot.",24,546);
        g.dispose();return sheet;
    }
    private static void part(Graphics2D g,BufferedImage skin,int x,int y,int w,int h,int u,int v,int sw,int sh) {
        g.drawImage(skin,x,y,x+w,y+h,u,v,u+sw,v+sh,null);
    }
}
