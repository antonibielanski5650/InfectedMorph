package pl.infectedmorph.appearance;

import com.google.gson.JsonParser;
import pl.infectedmorph.net.BoundedHttp;
import pl.infectedmorph.skin.SkinDownloader;
import pl.infectedmorph.skin.SkinModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

/** Accessed by ONE worker only. HTTPS response is trusted; signatures are not forged or modified. */
public final class MineSkinPublisher implements SkinPublisher {
    private final BoundedHttp http;
    private final String key;
    private final Duration timeout;
    private final int intervalSeconds;
    private long nextPublishNanos;
    private final Map<String, SignedTexture> cache = new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, SignedTexture> eldest) { return size() > 32; }
    };

    public MineSkinPublisher(BoundedHttp http, String key, int timeoutSeconds, int intervalSeconds) {
        this.http = http; this.key = key; this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.intervalSeconds = intervalSeconds;
        nextPublishNanos = System.nanoTime();
    }

    @Override public SignedTexture publish(byte[] png, SkinModel model) throws IOException, InterruptedException {
        String digest = digest(png) + model.variant();
        if (cache.containsKey(digest)) return cache.get(digest);
        long remaining = nextPublishNanos - System.nanoTime();
        if (remaining > 0) throw new IOException("Skin publication is on cooldown. Please try again in " + (remaining / 1_000_000_000L + 1) + " s.");
        String boundary = "InfectedMorph" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "variant", model.variant());
        field(body, boundary, "visibility", "unlisted");
        field(body, boundary, "name", "InfectedMorph stage1");
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"stage1.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(png);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        nextPublishNanos = System.nanoTime() + Duration.ofSeconds(intervalSeconds).toNanos();
        var response = http.send(HttpRequest.newBuilder(URI.create("https://api.mineskin.org/v2/generate"))
                .timeout(timeout).header("User-Agent", "InfectedMorph/0.4.0")
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), 131072);
        if (response.statusCode() == 429) {
            int seconds = 60;
            try { seconds = Integer.parseInt(response.headers().firstValue("Retry-After").orElse("60")); }
            catch (NumberFormatException ignored) { /* HTTP-date: conservative fallback */ }
            nextPublishNanos = System.nanoTime() + Duration.ofSeconds(Math.max(intervalSeconds, Math.min(3600, Math.max(60, seconds)))).toNanos();
            throw new IOException("MineSkin: HTTP 429. Please wait for the cooldown. No automatic retries will be attempted.");
        }
        if (response.statusCode() != 200) throw new IOException("MineSkin: HTTP " + response.statusCode() + ". Check your API key, network access and service status.");
        SignedTexture texture;
        URI publishedUri;
        try {
            var root = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("success") || !root.get("success").getAsBoolean()) throw new IllegalArgumentException();
            var data = root.getAsJsonObject("skin").getAsJsonObject("texture").getAsJsonObject("data");
            texture = new SignedTexture(data.get("value").getAsString(), data.get("signature").getAsString());
            if (texture.value().isBlank() || texture.value().length() > 16384 || texture.signature().isBlank()
                    || texture.signature().length() > 4096 || Base64.getDecoder().decode(texture.signature()).length == 0) throw new IllegalArgumentException();
            var payload = JsonParser.parseString(new String(Base64.getDecoder().decode(texture.value()), StandardCharsets.UTF_8)).getAsJsonObject();
            var skin = payload.getAsJsonObject("textures").getAsJsonObject("SKIN");
            publishedUri = SkinDownloader.trustedTexture(URI.create(skin.get("url").getAsString()));
            String variant = skin.has("metadata") ? skin.getAsJsonObject("metadata").get("model").getAsString() : "classic";
            if (!model.variant().equals(variant)) throw new IllegalArgumentException();
        } catch (RuntimeException error) {
            throw new IOException("MineSkin returned an incomplete or incompatible texture, signature or model.");
        }
        // Do not apply a cached/signing-service response that contains another player's image.
        var expected = ImageIO.read(new ByteArrayInputStream(png));
        var published = new SkinDownloader(http).download(publishedUri);
        if (expected == null || expected.getWidth() != 64 || expected.getHeight() != 64) throw new IOException("The generated PNG is invalid.");
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int a = expected.getRGB(x, y), b = published.getRGB(x, y);
            if ((a >>> 24) != (b >>> 24) || ((a >>> 24) != 0 && a != b)) {
                throw new IOException("The MineSkin texture does not match the generated PNG. Transformation was blocked.");
            }
        }
        cache.put(digest, texture);
        return texture;
    }

    private static void field(ByteArrayOutputStream body, String boundary, String name, String value) {
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(byte[] png) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
