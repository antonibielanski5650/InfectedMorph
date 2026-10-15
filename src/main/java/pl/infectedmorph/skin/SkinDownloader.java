package pl.infectedmorph.skin;

import pl.infectedmorph.net.BoundedHttp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import javax.imageio.ImageIO;

public final class SkinDownloader {
    private final BoundedHttp http;
    public SkinDownloader(BoundedHttp http) { this.http = http; }

    public static URI trustedTexture(URI uri) {
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || !"textures.minecraft.net".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || !uri.getPath().matches("/texture/[a-fA-F0-9]{32,64}")) {
            throw new IllegalArgumentException("The skin must originate from textures.minecraft.net/texture/<hash>.");
        }
        return URI.create("https://textures.minecraft.net" + uri.getPath());
    }

    public BufferedImage download(URI uri) throws IOException, InterruptedException {
        var response = http.send(HttpRequest.newBuilder(trustedTexture(uri)).timeout(Duration.ofSeconds(15)).GET().build(), 262144);
        if (response.statusCode() != 200) throw new IOException("Skin download: HTTP " + response.statusCode());
        byte[] bytes = response.body();
        // Validate PNG IHDR dimensions BEFORE decoding (prevent oversized decompression).
        byte[] signature = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        if (bytes.length < 33) throw new IOException("The PNG texture is too short.");
        for (int i = 0; i < 8; i++) if (bytes[i] != signature[i]) throw new IOException("The texture is not a PNG image.");
        var header = java.nio.ByteBuffer.wrap(bytes);
        if (header.getInt(8) != 13 || header.getInt(12) != 0x49484452
                || header.getInt(16) != 64 || header.getInt(20) != 64) {
            throw new IOException("Only standard 64x64 PNG skins are supported.");
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IOException("The PNG image could not be decoded.");
        return image;
    }
}
