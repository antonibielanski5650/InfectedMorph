package pl.infectedmorph.appearance;

import pl.infectedmorph.skin.SkinModel;
import java.io.IOException;

/** Signing/publication is independent of the renderer; future backends can reuse generated PNGs. */
public interface SkinPublisher {
    SignedTexture publish(byte[] png, SkinModel model) throws IOException, InterruptedException;
}
