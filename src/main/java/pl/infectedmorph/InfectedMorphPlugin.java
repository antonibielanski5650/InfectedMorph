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
    private InfectionIntensity intensity;
    private EyeTreatment eyes;
    private String publicationStatus;
    private volatile boolean stopping;

    private static final class Session {
        InfectionStage stage = InfectionStage.NORMAL;
        String state = "NORMAL";
        String detail = "Normal appearance.";
        PlayerProfile original;
        String appliedValue;
        Future<?> job;
    }

    private record WorkResult(SignedTexture texture, String preview) {}

    @Override public void onEnable() {
        saveDefaultConfig();
        stage1Enabled = getConfig().getBoolean("stage1.enabled", true);
        try { intensity = InfectionIntensity.parse(getConfig().getString("stage1.infection-intensity", "medium")); }
        catch (IllegalArgumentException invalid) {
            intensity = InfectionIntensity.MEDIUM;
            getLogger().warning("Invalid stage1.infection-intensity; expected low, medium or high. Using medium.");
        }
        eyes = readEyeSettings();
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
            publicationStatus = "JAVA_EXPERIMENTAL: MineSkin configured; visibility requires real client testing";
        } else {
            publicationStatus = display ? "PREVIEW_ONLY: no valid MineSkin key in the configured environment variable"
                    : "PREVIEW_ONLY: java-display.enabled=false";
        }
        var command = java.util.Objects.requireNonNull(getCommand("infect"), "Missing infect command in plugin.yml");
        command.setExecutor(this);
        command.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Server: " + Bukkit.getVersion() + "; Java: " + Runtime.version());
        getLogger().info("Stage 1 enabled=" + stage1Enabled + "; intensity=" + intensity.configValue() + "; eyes=" + eyes.mode() + "; " + publicationStatus);
        getLogger().info("Geyser-Spigot=" + version("Geyser-Spigot") + "; floodgate=" + version("floodgate"));
        getLogger().warning("Bedrock skin transformation is not implemented yet. Java-to-Bedrock visibility is unverified. Stages 2-5 and 3D models are not implemented.");
    }

    private int bounded(String path, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, getConfig().getInt(path, fallback)));
    }

    private EyeTreatment readEyeSettings() {
        // Retain the old opt-out for existing configurations without assuming fixed eye positions.
        if (!getConfig().getBoolean("stage1.darken-standard-eyes", true)) return EyeTreatment.off();
        try {
            var mode = EyeTreatment.Mode.valueOf(getConfig().getString("stage1.eyes.mode", "auto").trim().toUpperCase(Locale.ROOT));
            List<EyeTreatment.Pixel> pixels = new java.util.ArrayList<>();
            if (mode == EyeTreatment.Mode.CUSTOM) {
                for (String entry : getConfig().getStringList("stage1.eyes.pixels")) {
                    String[] coordinates = entry.split(",", -1);
                    if (coordinates.length != 2) throw new IllegalArgumentException();
                    pixels.add(new EyeTreatment.Pixel(Integer.parseInt(coordinates[0].trim()), Integer.parseInt(coordinates[1].trim())));
                }
                if (pixels.isEmpty()) throw new IllegalArgumentException();
            }
            return new EyeTreatment(mode, pixels);
        } catch (IllegalArgumentException invalid) {
            getLogger().warning("Invalid stage1.eyes settings. Use auto, off, or custom with x,y pixels in 0..7. Eye darkening is disabled.");
            return EyeTreatment.off();
        }
    }

    private String version(String name) {
        var plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin == null ? "not installed" : plugin.getPluginMeta().getVersion() + (plugin.isEnabled() ? " (enabled)" : " (disabled)");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) { say(sender, "Usage: /infect <stage1|normal|status>"); return true; }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("stage1", "normal", "status").contains(subcommand)) { say(sender, "Unknown option. Usage: /infect <stage1|normal|status>"); return true; }
        if (!sender.hasPermission("infectedmorph." + subcommand)) { say(sender, "You do not have permission to use this command. Required: infectedmorph." + subcommand); return true; }
        if (subcommand.equals("status")) { status(sender); return true; }
        if (!(sender instanceof Player player)) { say(sender, "Only players can use this command."); return true; }
        if (subcommand.equals("normal")) normal(player); else stage1(player);
        return true;
    }

    private void stage1(Player player) {
        Session previous = sessions.get(player.getUniqueId());
        reconcile(player, previous);
        if (previous != null && previous.stage == InfectionStage.STAGE1) { say(player, "Stage 1 is already set in your server profile. Use /infect normal to restore your skin."); return; }
        if (previous != null && previous.job != null && !previous.job.isDone()) { say(player, "A skin task is already running. Use /infect normal to cancel it."); return; }
        Session session = new Session();
        sessions.put(player.getUniqueId(), session);
        if (!stage1Enabled) { blocked(player, session, "Stage 1 is disabled in config.yml."); return; }
        var detection = detector.detect(player.getUniqueId());
        if (detection.kind() != ClientDetector.Kind.JAVA) {
            blocked(player, session, detection.kind() == ClientDetector.Kind.BEDROCK
                    ? "Bedrock skin transformation is not implemented yet. Your appearance and stage remain normal."
                    : detection.detail());
            return;
        }
        PlayerProfile original = player.getPlayerProfile();
        if (original.getTextures().getSkin() == null) {
            blocked(player, session, "Your profile has no skin texture. No default skin will be substituted. Check online-mode or your skin provider.");
            return;
        }
        URI source;
        try { source = SkinDownloader.trustedTexture(original.getTextures().getSkin().toURI()); }
        catch (Exception e) { blocked(player, session, "Unsupported skin source; textures.minecraft.net is required."); return; }
        SkinModel model = original.getTextures().getSkinModel().name().equals("SLIM") ? SkinModel.SLIM : SkinModel.WIDE;
        session.original = original.clone();
        session.state = "GENERATING";
        session.detail = "Downloading the original skin and generating a PNG on the skin worker.";
        UUID id = player.getUniqueId();
        Path previewRoot = getDataFolder().toPath().resolve("previews");
        try {
            session.job = worker.submit(() -> {
                try {
                    var image = downloader.download(source);
                    var infected = generator.generate(image, model, id, intensity, eyes);
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
                            ? error.getMessage() : "Unexpected error: " + error.getClass().getSimpleName();
                    schedule(() -> {
                        if (!current(player, session)) return;
                        session.state = "ERROR";
                        session.detail = detail;
                        say(player, "Your stage remains NORMAL. " + detail);
                        getLogger().warning("Stage1 " + id + ": " + detail);
                    });
                }
            });
            say(player, publisher == null ? "Generating a PNG preview. Your skin will not be applied: " + publicationStatus
                    : "Generating and publishing a Java skin texture. Your stage remains NORMAL until the operation completes.");
        } catch (RejectedExecutionException busy) { blocked(player, session, "The skin queue is full. Please try again later."); }
    }

    private void finish(Player player, Session session, WorkResult result) {
        if (!current(player, session)) return;
        if (result.texture() == null) {
            session.state = "PREVIEW_READY";
            session.detail = "PNG: " + result.preview() + "; intensity=" + intensity.configValue() + "; " + publicationStatus;
            say(player, "Stage 1 infection preview generated successfully! PNG: " + result.preview() + ". Your skin has not changed; your stage is NORMAL.");
            return;
        }
        if (!textures(player.getPlayerProfile()).equals(textures(session.original))) {
            blocked(player, session, "Your skin changed during generation. The result was discarded; please run the command again.");
            return;
        }
        if (!player.hasPermission("infectedmorph.stage1") || detector.detect(player.getUniqueId()).kind() != ClientDetector.Kind.JAVA) {
            blocked(player, session, "Your permissions or client type changed. The result was discarded.");
            return;
        }
        // Set ownership before mutation so a partially failing API call can still be restored.
        session.appliedValue = result.texture().value();
        try {
            backend.apply(player, result.texture());
            session.stage = InfectionStage.STAGE1;
            session.state = "JAVA_PROFILE_APPLIED";
            session.detail = "Java profile applied; verify the appearance on real clients. Bedrock visibility is unverified.";
            say(player, "Stage 1 has been set in your Java profile. Check F5 and another Java observer; Bedrock visibility is unverified.");
        } catch (RuntimeException error) {
            try { backend.restore(player, session.original); session.appliedValue = null; }
            catch (RuntimeException restoreError) {
                session.stage = InfectionStage.STAGE1;
                session.state = "RESTORE_REQUIRED";
                session.detail = "Profile update and rollback failed; your profile may have changed. Use /infect normal or reconnect.";
                say(player, session.detail);
                return;
            }
            blocked(player, session, "Profile update failed; your original texture has been restored.");
        }
    }

    private void normal(Player player) {
        Session session = sessions.get(player.getUniqueId());
        boolean restored = false;
        if (session != null && session.job != null) session.job.cancel(true);
        if (session != null && session.appliedValue != null && ownsTexture(player, session)) {
            try { backend.restore(player, session.original); restored = true; }
            catch (RuntimeException error) {
                session.state = "RESTORE_REQUIRED";
                session.detail = "Restoration failed. Please try again or reconnect.";
                say(player, session.detail);
                getLogger().warning("Restoration for " + player.getUniqueId() + ": " + error.getClass().getSimpleName());
                return;
            }
        } else if (session != null && session.appliedValue != null) {
            sessions.remove(player.getUniqueId());
            say(player, "Another plugin changed your texture. Infection state was cleared without overwriting your current skin.");
            return;
        }
        sessions.remove(player.getUniqueId());
        say(player, restored ? "Your original appearance has been restored! Your stage is NORMAL."
                : "Your stage is NORMAL. Any pending skin task has been cancelled; no applied skin needed restoration.");
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
            session.detail = "Another plugin changed your texture. InfectedMorph will not overwrite that change.";
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
        say(player, "Your stage is NORMAL. " + detail);
    }

    private void status(CommandSender sender) {
        say(sender, "Server: " + Bukkit.getVersion() + "; Java " + Runtime.version());
        say(sender, "Stage 1 enabled=" + stage1Enabled + "; intensity=" + intensity.configValue() + "; eyes=" + eyes.mode() + "; " + publicationStatus);
        say(sender, "Geyser-Spigot=" + version("Geyser-Spigot") + "; floodgate=" + version("floodgate"));
        say(sender, "Bedrock skin transformation is not implemented yet; Java skin visibility on Bedrock is unverified.");
        if (sender instanceof Player player) {
            var detection = detector.detect(player.getUniqueId());
            Session session = sessions.get(player.getUniqueId());
            reconcile(player, session);
            say(sender, "Client: " + detection.kind() + " (" + detection.detail() + ")");
            say(sender, "Stage: " + (session == null ? InfectionStage.NORMAL : session.stage)
                    + "; state: " + (session == null ? "NORMAL" : session.state));
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
                catch (RuntimeException e) { getLogger().warning("Could not restore " + player.getUniqueId() + "; the player must reconnect."); }
            }
        }
        sessions.clear();
        if (http != null) http.shutdown();
    }

    private static byte[] png(java.awt.image.BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("No PNG encoder is available.");
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
