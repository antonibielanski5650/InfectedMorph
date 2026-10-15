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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import pl.infectedmorph.appearance.*;
import pl.infectedmorph.compat.ClientDetector;
import pl.infectedmorph.domain.InfectionStage;
import pl.infectedmorph.domain.ActivationTransaction;
import pl.infectedmorph.domain.StageTransition;
import pl.infectedmorph.movement.BukkitMovementPort;
import pl.infectedmorph.movement.Stage1Movement;
import pl.infectedmorph.movement.Stage1SprintListener;
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
    private final Stage2SkinGenerator stage2Generator = new Stage2SkinGenerator();
    private final Stage1Movement movement = new Stage1Movement();
    private final java.util.Set<UUID> movementWarnings = new java.util.HashSet<>();
    private ExecutorService worker;
    private BoundedHttp http;
    private SkinDownloader downloader;
    private SkinPublisher publisher;
    private boolean stage1Enabled;
    private boolean stage2Enabled;
    private InfectionIntensity intensity;
    private EyeTreatment eyes;
    private String publicationStatus;
    private volatile boolean stopping;

    private static final class Session {
        InfectionStage stage = InfectionStage.NORMAL;
        InfectionStage target = InfectionStage.STAGE1;
        String state = "NORMAL";
        String detail = "Normal appearance.";
        PlayerProfile original;
        String appliedValue;
        String appliedSignature;
        Future<?> job;
    }

    private record WorkResult(SignedTexture texture, String preview) {}

    @Override public void onEnable() {
        saveDefaultConfig();
        stage1Enabled = getConfig().getBoolean("stage1.enabled", true);
        stage2Enabled = getConfig().getBoolean("stage2.enabled", true);
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
        boolean display = getConfig().getBoolean("java-display.enabled", true);
        String env = getConfig().getString("java-display.mineskin-api-key-env", "INFECTEDMORPH_MINESKIN_API_KEY");
        String key = env != null && env.matches("[A-Za-z_][A-Za-z0-9_]*") ? System.getenv(env) : null;
        if (display && key != null && !key.isBlank() && !key.contains("\r") && !key.contains("\n")) {
            publisher = new MineSkinPublisher(http, key.trim(), bounded("java-display.request-timeout-seconds", 90, 15, 180),
                    bounded("java-display.minimum-publish-interval-seconds", 60, 10, 3600));
            publicationStatus = "JAVA_PROFILE: MineSkin configured; native self/observer refresh; rendering requires client testing";
        } else {
            publicationStatus = display ? "PREVIEW_ONLY: no valid MineSkin key in the configured environment variable"
                    : "PREVIEW_ONLY: java-display.enabled=false";
        }
        var command = java.util.Objects.requireNonNull(getCommand("infect"), "Missing infect command in plugin.yml");
        command.setExecutor(this);
        command.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new Stage1SprintListener(movement, player -> endStage(player, false)), this);
        for (Player player : Bukkit.getOnlinePlayers()) releaseMovement(player);
        Bukkit.getScheduler().runTaskTimer(this, this::maintainInfection, 1L, 1L);
        getLogger().info("Server: " + Bukkit.getVersion() + "; Java: " + Runtime.version());
        getLogger().info("Stage 1 enabled=" + stage1Enabled + "; intensity=" + intensity.configValue() + "; eyes=" + eyes.mode() + "; " + publicationStatus);
        getLogger().info("Geyser-Spigot=" + version("Geyser-Spigot") + "; floodgate=" + version("floodgate"));
        getLogger().info(detector.skinCapabilities());
        getLogger().warning("Bedrock skin transformation is not implemented yet. Java-to-Bedrock visibility is unverified. Stages 3-5 and 3D models are not implemented. Stage 2 exact 1.5 x 3 dimensions are unsupported; size is unchanged.");
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
        if (args.length != 1) { say(sender, "Usage: /infect <stage1|stage2|normal|status>"); return true; }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("stage1", "stage2", "normal", "status").contains(subcommand)) { say(sender, "Unknown option. Usage: /infect <stage1|stage2|normal|status>"); return true; }
        if (!sender.hasPermission("infectedmorph." + subcommand)) { say(sender, "You do not have permission to use this command. Required: infectedmorph." + subcommand); return true; }
        if (subcommand.equals("status")) { status(sender); return true; }
        if (!(sender instanceof Player player)) { say(sender, "Only players can use this command."); return true; }
        if (subcommand.equals("normal")) normal(player); else transform(player, subcommand.equals("stage2") ? InfectionStage.STAGE2 : InfectionStage.STAGE1);
        return true;
    }

    private void transform(Player player, InfectionStage target) {
        Session previous = sessions.get(player.getUniqueId());
        reconcile(player, previous);
        if (previous != null && previous.stage == target) { say(player, target.label() + " is already set in your server profile. Use /infect normal to restore your skin."); return; }
        if (player.isDead() || player.getHealth() <= 0) { say(player, "You must respawn before starting an infection stage."); return; }
        // A transition releases the old stage first. Failure leaves NORMAL, never a half-active target.
        // The original profile is retained before restoration; an infected live profile is never a source.
        PlayerProfile retainedOriginal = null;
        if (previous != null && previous.stage != InfectionStage.NORMAL) {
            PlayerProfile originalSnapshot = previous.original;
            try {
                retainedOriginal = StageTransition.restoreBeforeTransition(originalSnapshot::clone,
                        () -> endStage(player, false), () -> !sessions.containsKey(player.getUniqueId()));
            } catch (RuntimeException recovery) {
                say(player, "Stage transition stopped: original appearance or movement recovery is pending. Use /infect normal.");
                return;
            }
            previous = null;
        }
        if (previous != null && previous.appliedValue != null) { say(player, "Appearance recovery is pending. Use /infect normal before starting another transformation."); return; }
        if (movement.cleanupPending(player.getUniqueId())) { say(player, "Movement cleanup is pending. A transformation cannot start yet."); return; }
        if (previous != null && previous.job != null && !previous.job.isDone()) { say(player, "A skin task is already running. Use /infect normal to cancel it."); return; }
        Session session = new Session();
        session.target = target;
        sessions.put(player.getUniqueId(), session);
        if (target == InfectionStage.STAGE1 ? !stage1Enabled : !stage2Enabled) { blocked(player, session, target.label() + " is disabled in config.yml."); return; }
        var detection = detector.detect(player.getUniqueId());
        if (detection.kind() != ClientDetector.Kind.JAVA) {
            blocked(player, session, detection.kind() == ClientDetector.Kind.BEDROCK
                    ? "Bedrock skin transformation is not implemented yet. Your appearance and stage remain normal."
                    : detection.detail());
            return;
        }
        PlayerProfile original = retainedOriginal != null ? retainedOriginal : player.getPlayerProfile();
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
                    var infected = target == InfectionStage.STAGE1
                            ? generator.generate(image, model, id, intensity, eyes)
                            : stage2Generator.generate(image, model, id, intensity, eyes);
                    byte[] originalPng = png(image), infectedPng = png(infected);
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    Path directory = previewRoot.resolve(id.toString());
                    Files.createDirectories(directory);
                    writeAtomic(directory.resolve("original.png"), originalPng);
                    writeAtomic(directory.resolve(target.command() + ".png"), infectedPng);
                    SignedTexture texture = publisher == null ? null : publisher.publish(infectedPng, model);
                    WorkResult result = new WorkResult(texture, "previews/" + id + "/" + target.command() + ".png");
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
                        getLogger().warning(target.label() + " " + id + ": " + detail);
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
            say(player, session.target.label() + " infection preview generated successfully! PNG: " + result.preview() + ". Your skin has not changed; your stage is NORMAL.");
            return;
        }
        if (!textures(player.getPlayerProfile()).equals(textures(session.original))) {
            blocked(player, session, "Your skin changed during generation. The result was discarded; please run the command again.");
            return;
        }
        if (!player.hasPermission("infectedmorph." + session.target.command()) || detector.detect(player.getUniqueId()).kind() != ClientDetector.Kind.JAVA) {
            blocked(player, session, "Your permissions or client type changed. The result was discarded.");
            return;
        }
        // Retain recovery data before mutation. No active stage is reported until BOTH steps succeed.
        session.appliedValue = result.texture().value();
        session.appliedSignature = result.texture().signature();
        var activation = ActivationTransaction.activate(
                () -> backend.apply(player, result.texture()),
                () -> {
                    if (!JavaProfileBackend.hasTexture(player, session.appliedValue, session.appliedSignature)) {
                        throw new IllegalStateException("Signed texture verification failed.");
                    }
                },
                () -> movement.activate(player.getUniqueId(), new BukkitMovementPort(player), session.target),
                () -> movement.clear(player.getUniqueId(), new BukkitMovementPort(player)),
                () -> backend.restore(player, session.original));
        if (activation.outcome() == ActivationTransaction.Outcome.ACTIVE) {
            session.stage = session.target;
            session.state = "JAVA_PROFILE_APPLIED";
            session.detail = "Signed Java texture verified; native self/observer refresh requested. Rendering UNVERIFIED. " + movementDescription(session.stage);
            say(player, session.stage.label() + " is active: Java profile applied. " + movementDescription(session.stage) + " Verify F5 and another Java observer; client rendering is not confirmed.");
            if (session.stage == InfectionStage.STAGE2) say(player, "Size unchanged: exact 1.5-block width and 3-block height are unsupported by the public scale API.");
        } else if (activation.outcome() == ActivationTransaction.Outcome.ROLLED_BACK) {
            session.appliedValue = null;
            session.appliedSignature = null;
            blocked(player, session, "Transformation failed. The original skin is restored and infection movement is disabled.");
        } else {
            session.stage = InfectionStage.NORMAL;
            session.state = "RECOVERY_REQUIRED";
            session.detail = "Transformation failed and recovery is incomplete. No infection stage is active. Sprint policy is released; modifier cleanup is retried. Use /infect normal or reconnect.";
            say(player, session.detail);
        }
    }

    private void normal(Player player) { endStage(player, true); }

    private void endStage(Player player, boolean notify) { endStage(player, notify, false); }

    private void endStage(Player player, boolean notify, boolean deferAppearance) {
        Session session = sessions.get(player.getUniqueId());
        boolean movementReleased = releaseMovement(player);
        boolean restored = false;
        boolean external = false;
        if (session != null && session.job != null) session.job.cancel(true);
        if (session != null) session.stage = InfectionStage.NORMAL;
        if (session != null && session.appliedValue != null && ownsTexture(player, session)) {
            if (deferAppearance || player.isDead()) {
                session.state = "RESTORE_PENDING_RESPAWN";
                session.detail = "Infection ended on death. Movement restrictions released; original skin restoration is deferred until respawn.";
                if (notify) say(player, session.detail);
                return;
            }
            try {
                backend.restore(player, session.original);
                restored = true;
                session.appliedValue = null;
                session.appliedSignature = null;
            }
            catch (RuntimeException error) {
                session.state = "RECOVERY_REQUIRED";
                session.detail = "No infection stage is active. Appearance restoration failed; movement restrictions have been released or queued for cleanup. Use /infect normal or reconnect.";
                if (notify) say(player, session.detail);
                getLogger().warning("Restoration for " + player.getUniqueId() + ": " + error.getClass().getSimpleName());
                return;
            }
        } else if (session != null && session.appliedValue != null) {
            external = true;
            session.appliedValue = null;
            session.appliedSignature = null;
        }
        if (!movementReleased) {
            if (session == null) { session = new Session(); sessions.put(player.getUniqueId(), session); }
            session.state = "RECOVERY_REQUIRED";
            session.detail = "No infection stage is active. Sprint policy released; walking-modifier cleanup is pending and will be retried.";
            if (notify) say(player, session.detail);
            return;
        }
        sessions.remove(player.getUniqueId());
        if (notify) say(player, external ? "Another plugin changed your skin. Infection movement was removed without overwriting that texture."
                : restored ? "Your original appearance and movement settings have been restored! Your stage is NORMAL."
                : "Your stage is NORMAL. Pending work is cancelled and infection movement modifiers are removed.");
    }

    private boolean ownsTexture(Player player, Session session) {
        return JavaProfileBackend.hasTexture(player, session.appliedValue, session.appliedSignature);
    }

    private void reconcile(Player player, Session session) {
        if (session != null && session.stage != InfectionStage.NORMAL && !ownsTexture(player, session)) {
            session.stage = InfectionStage.NORMAL;
            releaseMovement(player);
            session.appliedValue = null;
            session.appliedSignature = null;
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
                && !player.isDead() && player.getHealth() > 0 && session.state.equals("GENERATING") && sessions.get(player.getUniqueId()) == session;
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
        say(sender, "Stage 1 enabled=" + stage1Enabled + "; Stage 2 enabled=" + stage2Enabled + "; shared intensity=" + intensity.configValue() + "; eyes=" + eyes.mode() + "; " + publicationStatus);
        say(sender, "Geyser-Spigot=" + version("Geyser-Spigot") + "; floodgate=" + version("floodgate"));
        say(sender, detector.skinCapabilities());
        say(sender, "Bedrock skin transformation is not implemented yet; Java skin visibility on Bedrock is unverified.");
        if (sender instanceof Player player) {
            var detection = detector.detect(player.getUniqueId());
            Session session = sessions.get(player.getUniqueId());
            reconcile(player, session);
            say(sender, "Client: " + detection.kind() + " (" + detection.detail() + ")");
            say(sender, "Stage: " + (session == null ? InfectionStage.NORMAL : session.stage)
                    + "; state: " + (session == null ? "NORMAL" : session.state));
            say(sender, "Appearance: " + (session != null && session.stage != InfectionStage.NORMAL
                    ? "SIGNED_JAVA_PROFILE_CONFIRMED; client rendering UNVERIFIED" : "not active (preview, normal, or recovery)"));
            say(sender, "Infection movement: " + (movement.isActive(player.getUniqueId()) ? "ACTIVE; " + movementDescription(session.stage)
                    : movement.cleanupPending(player.getUniqueId()) ? "CLEANUP_PENDING; sprint policy released" : "INACTIVE"));
            say(sender, "Stage 2 size: UNIMPLEMENTED; exact 1.5 x 3 dimensions unsupported. Scale is unchanged.");
            var box = player.getBoundingBox();
            say(sender, String.format(Locale.ROOT, "Current server bounding box: %.3f x %.3f x %.3f blocks (width x height x depth); pose=%s.", box.getWidthX(), box.getHeight(), box.getWidthZ(), player.getPose()));
            if (session != null) say(sender, session.detail);
        }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("stage1", "stage2", "normal", "status").stream()
                .filter(s -> s.startsWith(prefix) && sender.hasPermission("infectedmorph." + s)).toList();
    }

    private boolean releaseMovement(Player player) {
        try {
            movement.clear(player.getUniqueId(), new BukkitMovementPort(player));
            movementWarnings.remove(player.getUniqueId());
            return true;
        } catch (RuntimeException error) {
            if (movementWarnings.add(player.getUniqueId())) {
                getLogger().warning("Walking-modifier cleanup is pending for " + player.getUniqueId() + ": " + error.getClass().getSimpleName());
            }
            return false;
        }
    }

    private void maintainInfection() {
        for (var pair : List.copyOf(sessions.entrySet())) {
            Session session = pair.getValue();
            if (session.stage == InfectionStage.NORMAL) continue;
            Player player = Bukkit.getPlayer(pair.getKey());
            if (player == null) {
                try { movement.deactivate(pair.getKey()); } catch (RuntimeException ignored) { /* tick retries retained cleanup */ }
                sessions.remove(pair.getKey());
            } else if (player.isDead()) endStage(player, false, true);
            else reconcile(player, session);
        }
        for (var failure : movement.tick()) {
            Session session = sessions.get(failure.player());
            Player player = Bukkit.getPlayer(failure.player());
            if (session != null && session.stage != InfectionStage.NORMAL && player != null) {
                endStage(player, false);
                say(player, "Infection ended because movement enforcement failed. Check /infect status.");
            }
            if (movementWarnings.add(failure.player())) {
                getLogger().warning("Infection movement error for " + failure.player() + ": " + failure.error().getClass().getSimpleName());
            }
        }
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) { endStage(event.getEntity(), false, true); }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        releaseMovement(player);
        if (session == null) return;
        session.stage = InfectionStage.NORMAL;
        session.state = "RESTORE_PENDING_RESPAWN";
        if (session.job != null) session.job.cancel(true);
        Bukkit.getScheduler().runTask(this, () -> {
            if (!stopping && player.isOnline() && sessions.get(player.getUniqueId()) == session) endStage(player, true);
        });
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { releaseMovement(event.getPlayer()); }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        endStage(event.getPlayer(), false);
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @Override public void onDisable() {
        stopping = true;
        if (worker != null) worker.shutdownNow();
        for (var failure : movement.deactivateAll()) {
            getLogger().warning("Movement cleanup on disable failed for " + failure.player() + "; the transient modifier is not saved. Reconnect if needed.");
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            releaseMovement(player);
            Session session = sessions.get(player.getUniqueId());
            if (session != null && session.appliedValue != null && !player.isDead() && ownsTexture(player, session)) {
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

    private static String movementDescription(InfectionStage stage) {
        return stage == InfectionStage.STAGE2 ? "Walking target: 6 blocks/s; sprint target: 9 blocks/s; sprint permitted (normal hunger rules apply)."
                : "Walking target: 4 blocks/s; sprinting blocked.";
    }

    private static void say(CommandSender sender, String message) { sender.sendMessage("[InfectedMorph] " + message); }
}
