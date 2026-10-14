package pl.infectedmorph;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import pl.infectedmorph.appearance.*;
import pl.infectedmorph.compat.ClientDetector;
import pl.infectedmorph.domain.InfectionStage;
import pl.infectedmorph.net.BoundedHttp;
import pl.infectedmorph.skin.*;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public final class InfectedMorphPlugin extends JavaPlugin implements Listener, TabExecutor {
    private final Map<UUID, Session> sessions = new HashMap<>(); // Main thread only.
    private final ClientDetector detector = new ClientDetector();
    private final AppearanceBackend backend = new JavaProfileBackend();
    private final Stage1SkinGenerator generator = new Stage1SkinGenerator();
    private ExecutorService worker;
    private BoundedHttp http;
    private SkinDownloader downloader;
    private SkinPublisher publisher;
    private boolean stage1Enabled;
    private boolean darkenEyes;
    private String publicationStatus;
    private volatile boolean stopping;

    private static final class Session {
        InfectionStage stage = InfectionStage.NORMAL;
        String state = "NORMAL";
        String detail = "Normalny wygląd.";
        PlayerProfile original;
        String appliedValue;
        Future<?> job;
    }

    private record WorkResult(SignedTexture texture, String preview) {}

    @Override public void onEnable() {
        saveDefaultConfig();
        stage1Enabled = getConfig().getBoolean("stage1.enabled", true);
        darkenEyes = getConfig().getBoolean("stage1.darken-standard-eyes", true);
        http = new BoundedHttp();
        downloader = new SkinDownloader(http);
        // Bounded queue and one serialized worker prevent upload bursts and unbounded memory growth.
        worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8), runnable -> {
            Thread thread = new Thread(runnable, "InfectedMorph-skins");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        boolean display = getConfig().getBoolean("java-display.enabled", false);
        String env = getConfig().getString("java-display.mineskin-api-key-env", "INFECTEDMORPH_MINESKIN_API_KEY");
        String key = env != null && env.matches("[A-Za-z_][A-Za-z0-9_]*") ? System.getenv(env) : null;
        if (display && key != null && !key.isBlank() && !key.contains("\r") && !key.contains("\n")) {
            publisher = new MineSkinPublisher(http, key.trim(), bounded("java-display.request-timeout-seconds", 90, 15, 180),
                    bounded("java-display.minimum-publish-interval-seconds", 60, 10, 3600));
            publicationStatus = "JAVA_EXPERIMENTAL: MineSkin skonfigurowany; widoczność wymaga testów klientów";
        } else {
            publicationStatus = display ? "PREVIEW_ONLY: brak poprawnego klucza MineSkin w zmiennej środowiskowej"
                    : "PREVIEW_ONLY: java-display.enabled=false";
        }
        var command = java.util.Objects.requireNonNull(getCommand("infect"), "Brak komendy infect w plugin.yml");
        command.setExecutor(this);
        command.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Serwer: " + Bukkit.getVersion() + "; Java: " + Runtime.version());
        getLogger().info("Stage1 enabled=" + stage1Enabled + "; " + publicationStatus);
        getLogger().info("Geyser-Spigot=" + version("Geyser-Spigot") + "; floodgate=" + version("floodgate"));
        getLogger().warning("Bedrock: adapter wyglądu niezaimplementowany. Brak gwarancji Java -> Bedrock. Fazy 2-5 i modele 3D niezaimplementowane.");
    }

    private int bounded(String path, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, getConfig().getInt(path, fallback)));
    }

    private String version(String name) {
        var plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin == null ? "brak" : plugin.getPluginMeta().getVersion() + (plugin.isEnabled() ? " (enabled)" : " (disabled)");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) { say(sender, "Użycie: /infect <stage1|normal|status>"); return true; }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("stage1", "normal", "status").contains(subcommand)) { say(sender, "Nieznana opcja. Użycie: /infect <stage1|normal|status>"); return true; }
        if (!sender.hasPermission("infectedmorph." + subcommand)) { say(sender, "Brak uprawnienia infectedmorph." + subcommand); return true; }
        if (subcommand.equals("status")) { status(sender); return true; }
        if (!(sender instanceof Player player)) { say(sender, "Ta komenda jest dostępna tylko dla gracza."); return true; }
        if (subcommand.equals("normal")) normal(player); else stage1(player);
        return true;
    }

    private void stage1(Player player) {
        Session previous = sessions.get(player.getUniqueId());
        reconcile(player, previous);
        if (previous != null && previous.stage == InfectionStage.STAGE1) { say(player, "Faza 1 jest już ustawiona w profilu serwera; /infect normal przywraca skin."); return; }
        if (previous != null && previous.job != null && !previous.job.isDone()) { say(player, "Zadanie już trwa; /infect normal je anuluje."); return; }
        Session session = new Session();
        sessions.put(player.getUniqueId(), session);
        if (!stage1Enabled) { blocked(player, session, "Faza 1 wyłączona w config.yml."); return; }
        var detection = detector.detect(player.getUniqueId());
        if (detection.kind() != ClientDetector.Kind.JAVA) {
            blocked(player, session, detection.kind() == ClientDetector.Kind.BEDROCK
                    ? "Bedrock: brak zweryfikowanego adaptera tekstury/odświeżania. Wygląd i faza pozostają normalne."
                    : detection.detail());
            return;
        }
        PlayerProfile original = player.getPlayerProfile();
        if (original.getTextures().getSkin() == null) {
            blocked(player, session, "Profil nie zawiera tekstury. Nie zastępuję go skinem Steve/Alex. Sprawdź online-mode lub dostawcę skinów.");
            return;
        }
        URI source;
        try { source = SkinDownloader.trustedTexture(original.getTextures().getSkin().toURI()); }
        catch (Exception e) { blocked(player, session, "Nieobsługiwane źródło tekstury; wymagane textures.minecraft.net."); return; }
        SkinModel model = original.getTextures().getSkinModel().name().equals("SLIM") ? SkinModel.SLIM : SkinModel.WIDE;
        session.original = original.clone();
        session.state = "GENERATING";
        session.detail = "Pobieranie oryginału i generowanie PNG poza wątkiem serwera.";
        UUID id = player.getUniqueId();
        Path previewRoot = getDataFolder().toPath().resolve("previews");
        try {
            session.job = worker.submit(() -> {
                try {
                    var image = downloader.download(source);
                    var infected = generator.generate(image, model, id, darkenEyes);
                    byte[] originalPng = png(image), infectedPng = png(infected);
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    Path directory = previewRoot.resolve(id.toString());
                    Files.createDirectories(directory);
                    writeAtomic(directory.resolve("original.png"), originalPng);
                    writeAtomic(directory.resolve("stage1.png"), infectedPng);
                    SignedTexture texture = publisher == null ? null : publisher.publish(infectedPng, model);
                    WorkResult result = new WorkResult(texture, "previews/" + id + "/stage1.png");
                    schedule(() -> finish(player, session, result));
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                catch (Exception error) {
                    String detail = error instanceof IOException || error instanceof IllegalArgumentException
                            ? error.getMessage() : "Nieoczekiwany błąd " + error.getClass().getSimpleName();
                    schedule(() -> {
                        if (!current(player, session)) return;
                        session.state = "ERROR";
                        session.detail = detail;
                        say(player, "Faza pozostaje NORMAL. " + detail);
                        getLogger().warning("Stage1 " + id + ": " + detail);
                    });
                }
            });
            say(player, publisher == null ? "Generuję podgląd PNG. Skin nie zostanie zastosowany: " + publicationStatus
                    : "Generuję i publikuję teksturę Java. Faza pozostaje NORMAL do zakończenia operacji.");
        } catch (RejectedExecutionException busy) { blocked(player, session, "Kolejka pełna. Spróbuj ponownie później."); }
    }

    private void finish(Player player, Session session, WorkResult result) {
        if (!current(player, session)) return;
        if (result.texture() == null) {
            session.state = "PREVIEW_READY";
            session.detail = "PNG: " + result.preview() + "; " + publicationStatus;
            say(player, "Podgląd gotowy: " + result.preview() + ". Skin NIE został zmieniony; faza NORMAL.");
            return;
        }
        if (!textures(player.getPlayerProfile()).equals(textures(session.original))) {
            blocked(player, session, "Skin zmienił się w trakcie generowania. Wynik odrzucony; wykonaj komendę ponownie.");
            return;
        }
        if (!player.hasPermission("infectedmorph.stage1") || detector.detect(player.getUniqueId()).kind() != ClientDetector.Kind.JAVA) {
            blocked(player, session, "Zmieniły się uprawnienia lub rodzaj klienta. Wynik odrzucony.");
            return;
        }
        // Set ownership before mutation so a partially failing API call can still be restored.
        session.appliedValue = result.texture().value();
        try {
            backend.apply(player, result.texture());
            session.stage = InfectionStage.STAGE1;
            session.state = "JAVA_PROFILE_APPLIED";
            session.detail = "Profil Java ustawiony; potwierdź wygląd na klientach. Bedrock niezweryfikowany.";
            say(player, "Faza 1 ustawiona w profilu Java. Sprawdź widoczność u drugiego gracza i w F5; brak gwarancji Bedrock.");
        } catch (RuntimeException error) {
            try { backend.restore(player, session.original); session.appliedValue = null; }
            catch (RuntimeException restoreError) {
                session.stage = InfectionStage.STAGE1;
                session.state = "RESTORE_REQUIRED";
                session.detail = "Błąd API i rollbacku; profil może być zmieniony. Wykonaj /infect normal lub połącz się ponownie.";
                say(player, session.detail);
                return;
            }
            blocked(player, session, "Błąd ustawiania profilu; oryginalna tekstura przywrócona.");
        }
    }

    private void normal(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.job != null) session.job.cancel(true);
        if (session != null && session.appliedValue != null && ownsTexture(player, session)) {
            try { backend.restore(player, session.original); }
            catch (RuntimeException error) {
                session.state = "RESTORE_REQUIRED";
                session.detail = "Przywracanie nie powiodło się. Spróbuj ponownie lub połącz się ponownie.";
                say(player, session.detail);
                getLogger().warning("Przywracanie " + player.getUniqueId() + ": " + error.getClass().getSimpleName());
                return;
            }
        } else if (session != null && session.appliedValue != null) {
            sessions.remove(player.getUniqueId());
            say(player, "Inny plugin zmienił teksturę. Usunięto stan infekcji bez nadpisywania aktualnego skina.");
            return;
        }
        sessions.remove(player.getUniqueId());
        say(player, "Faza NORMAL. Zadanie anulowane; tekstura InfectedMorph przywrócona, jeśli była aktywna.");
    }

    private boolean ownsTexture(Player player, Session session) {
        return player.getPlayerProfile().getProperties().stream()
                .anyMatch(p -> p.getName().equals("textures") && p.getValue().equals(session.appliedValue));
    }

    private void reconcile(Player player, Session session) {
        if (session != null && session.stage == InfectionStage.STAGE1 && !ownsTexture(player, session)) {
            session.stage = InfectionStage.NORMAL;
            session.appliedValue = null;
            session.state = "EXTERNAL_SKIN_CHANGE";
            session.detail = "Inny plugin zmienił teksturę. InfectedMorph nie nadpisuje tej zmiany.";
        }
    }

    private List<String> textures(PlayerProfile profile) {
        return profile.getProperties().stream().filter(p -> p.getName().equals("textures"))
                .map(p -> p.getValue() + "|" + p.getSignature()).sorted().toList();
    }

    private boolean current(Player player, Session session) {
        return !stopping && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player
                && sessions.get(player.getUniqueId()) == session;
    }

    private void schedule(Runnable callback) {
        if (stopping) return;
        try { Bukkit.getScheduler().runTask(this, callback); }
        catch (org.bukkit.plugin.IllegalPluginAccessException ignored) { /* disabled between check and scheduling */ }
    }

    private void blocked(Player player, Session session, String detail) {
        session.state = "BLOCKED";
        session.detail = detail;
        say(player, "Faza NORMAL. " + detail);
    }

    private void status(CommandSender sender) {
        say(sender, "Serwer: " + Bukkit.getVersion() + "; Java " + Runtime.version());
        say(sender, "Faza 1 enabled=" + stage1Enabled + "; " + publicationStatus);
        say(sender, "Geyser-Spigot=" + version("Geyser-Spigot") + "; floodgate=" + version("floodgate"));
        say(sender, "Bedrock: brak adaptera wyglądu; widoczność zmian Java na Bedrock niezweryfikowana.");
        if (sender instanceof Player player) {
            var detection = detector.detect(player.getUniqueId());
            Session session = sessions.get(player.getUniqueId());
            reconcile(player, session);
            say(sender, "Klient: " + detection.kind() + " (" + detection.detail() + ")");
            say(sender, "Faza: " + (session == null ? InfectionStage.NORMAL : session.stage)
                    + "; stan: " + (session == null ? "NORMAL" : session.state));
            if (session != null) say(sender, session.detail);
        }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("stage1", "normal", "status").stream()
                .filter(s -> s.startsWith(prefix) && sender.hasPermission("infectedmorph." + s)).toList();
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        normal(event.getPlayer());
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @Override public void onDisable() {
        stopping = true;
        if (worker != null) worker.shutdownNow();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Session session = sessions.get(player.getUniqueId());
            if (session != null && session.appliedValue != null && ownsTexture(player, session)) {
                try { backend.restore(player, session.original); }
                catch (RuntimeException e) { getLogger().warning("Nie udało się przywrócić " + player.getUniqueId() + "; wymagane ponowne połączenie."); }
            }
        }
        sessions.clear();
        if (http != null) http.shutdown();
    }

    private static byte[] png(java.awt.image.BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("Brak kodera PNG.");
        return output.toByteArray();
    }

    private static void writeAtomic(Path file, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), "skin-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void say(CommandSender sender, String message) { sender.sendMessage("[InfectedMorph] " + message); }
}
