package pl.infectedmorph.skin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.awt.image.BufferedImage;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EyeTreatmentTest {
    private BufferedImage face() {
        var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int y=8;y<16;y++)for(int x=8;x<16;x++)image.setRGB(x,y,0xffcf9976);
        return image;
    }
    private void eyes(BufferedImage image,int row) {
        for(int x:new int[]{1,2,5,6})image.setRGB(8+x,8+row,0xff356ea4);
    }
    @ParameterizedTest @ValueSource(ints={2,3,4,5})
    void detectsContrastingEyesAtDifferentRows(int row) {
        var source=face();eyes(source,row);
        var found=EyeTreatment.automatic().locate(source);
        assertEquals(4,found.size());
        assertTrue(found.stream().allMatch(pixel->pixel.y()==row));
    }
    @Test void leavesUniformAndAmbiguousFacesAlone() {
        assertTrue(EyeTreatment.automatic().locate(face()).isEmpty());
        var ambiguous=face();eyes(ambiguous,2);eyes(ambiguous,5);
        assertTrue(EyeTreatment.automatic().locate(ambiguous).isEmpty());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(SkinModel.class)
    void detectsSampleEyesBelowHairAndDarkensThemInGeneratedOutput(SkinModel model) {
        var source=Stage1PreviewSamples.sample(model);
        int row=model==SkinModel.SLIM?3:4;
        var eyes=EyeTreatment.automatic().locate(source);
        assertEquals(4,eyes.size());
        assertTrue(eyes.stream().allMatch(pixel->pixel.y()==row));
        var generated=new Stage1SkinGenerator().generate(source,model,new java.util.UUID(4,5),InfectionIntensity.MEDIUM,EyeTreatment.automatic());
        for(var eye:eyes) {
            int before=source.getRGB(8+eye.x(),8+eye.y()),after=generated.getRGB(8+eye.x(),8+eye.y());
            assertTrue(((after>>16)&255)<((before>>16)&255));
            assertEquals(before>>>24,after>>>24);
        }
    }
    @Test void customCoordinatesAllowAsymmetricOrNonstandardEyes() {
        var pixels=List.of(new EyeTreatment.Pixel(0,1),new EyeTreatment.Pixel(4,6));
        assertEquals(pixels,new EyeTreatment(EyeTreatment.Mode.CUSTOM,pixels).locate(face()));
        assertTrue(EyeTreatment.off().locate(face()).isEmpty());
    }
    @Test void rejectsCoordinatesOutsideTheFaceAndCopiesTheSettings() {
        assertThrows(IllegalArgumentException.class,()->new EyeTreatment.Pixel(8,4));
        assertThrows(IllegalArgumentException.class,()->new EyeTreatment.Pixel(2,-1));
        var mutable=new java.util.ArrayList<>(List.of(new EyeTreatment.Pixel(1,3)));
        var settings=new EyeTreatment(EyeTreatment.Mode.CUSTOM,mutable);
        mutable.clear();assertEquals(1,settings.pixels().size());
        assertThrows(UnsupportedOperationException.class,()->settings.pixels().clear());
    }
    @Test void parsesIntensityCaseInsensitivelyAndRejectsInvalidValues() {
        assertEquals(InfectionIntensity.MEDIUM,InfectionIntensity.parse(null));
        assertEquals(InfectionIntensity.LOW,InfectionIntensity.parse(" LOW "));
        assertEquals(InfectionIntensity.HIGH,InfectionIntensity.parse("high"));
        assertThrows(IllegalArgumentException.class,()->InfectionIntensity.parse("maximum"));
    }
}
