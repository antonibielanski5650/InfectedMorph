package pl.infectedmorph.skin;

import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class SkinDownloaderTest {
    private final String path = "/texture/" + "a".repeat(64);
    @Test void upgradesOfficialTextureToHttps() {
        assertEquals("https://textures.minecraft.net" + path,
                SkinDownloader.trustedTexture(URI.create("http://textures.minecraft.net" + path)).toString());
    }
    @Test void rejectsArbitraryHostsCredentialsPortsAndRedirectInputs() {
        for (String url : new String[]{"http://127.0.0.1" + path, "file:///etc/passwd",
                "https://textures.minecraft.net.evil.example" + path, "https://evil.example@textures.minecraft.net" + path,
                "https://textures.minecraft.net:443" + path, "https://textures.minecraft.net" + path + "?url=evil",
                "https://textures.minecraft.net" + path + "#fragment", "https://textures.minecraft.net/texture/not-a-hash"}) {
            assertThrows(IllegalArgumentException.class, () -> SkinDownloader.trustedTexture(URI.create(url)), url);
        }
    }
}
