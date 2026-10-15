package pl.infectedmorph.net;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class BoundedHttpTest {
    @Test void capsResponsesAndDoesNotFollowRedirects() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/body", exchange -> {
            byte[] bytes = new byte[4096];
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/body");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        BoundedHttp http = new BoundedHttp();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var body = HttpRequest.newBuilder(URI.create(base + "/body")).timeout(Duration.ofSeconds(5)).GET().build();
            assertThrows(IOException.class, () -> http.send(body, 1024));
            assertEquals(4096, http.send(body, 4096).body().length);
            var redirect = HttpRequest.newBuilder(URI.create(base + "/redirect")).timeout(Duration.ofSeconds(5)).GET().build();
            assertEquals(302, http.send(redirect, 4096).statusCode());
        } finally { http.shutdown(); server.stop(0); }
    }
}
