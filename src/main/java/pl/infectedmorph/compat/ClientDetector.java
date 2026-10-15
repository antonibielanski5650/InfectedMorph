package pl.infectedmorph.compat;

import org.bukkit.Bukkit;
import java.lang.reflect.Method;
import java.util.UUID;

/** Optional integrations use only documented public API signatures; no server internals. */
public final class ClientDetector {
    public enum Kind { JAVA, BEDROCK, UNKNOWN }
    public record Detection(Kind kind, String detail) {}

    public Detection detect(UUID player) {
        boolean failed = false;
        boolean floodgate = Bukkit.getPluginManager().isPluginEnabled("floodgate");
        boolean geyser = Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot");
        if (floodgate) {
            try {
                Class<?> api = load("floodgate", "org.geysermc.floodgate.api.FloodgateApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                if ((boolean) api.getMethod("isFloodgatePlayer", UUID.class).invoke(instance, player)) {
                    return new Detection(Kind.BEDROCK, "floodgate API");
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { failed = true; }
        }
        if (geyser) {
            try {
                Class<?> api = load("Geyser-Spigot", "org.geysermc.geyser.api.GeyserApi");
                Method accessor = api.getMethod("api");
                Object instance = accessor.invoke(null);
                if (api.getMethod("connectionByUuid", UUID.class).invoke(instance, player) != null) {
                    return new Detection(Kind.BEDROCK, "Geyser API");
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { failed = true; }
        }
        return failed ? new Detection(Kind.UNKNOWN, "Geyser/floodgate API unavailable; transformation blocked")
                : new Detection(Kind.JAVA, "no Bedrock session reported by local APIs");
    }

    private Class<?> load(String pluginName, String className) throws ClassNotFoundException {
        var plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) throw new ClassNotFoundException(pluginName);
        return Class.forName(className, true, plugin.getClass().getClassLoader());
    }
}
