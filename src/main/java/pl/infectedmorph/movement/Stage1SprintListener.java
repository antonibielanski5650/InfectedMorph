package pl.infectedmorph.movement;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import java.util.function.Consumer;

public final class Stage1SprintListener implements Listener {
    private final Stage1Movement movement;
    private final Consumer<Player> failureHandler;

    public Stage1SprintListener(Stage1Movement movement, Consumer<Player> failureHandler) {
        this.movement = movement; this.failureHandler = failureHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting() || !movement.isActive(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        try { movement.handleSprintAttempt(event.getPlayer().getUniqueId(), true); }
        catch (RuntimeException error) { failureHandler.accept(event.getPlayer()); }
    }
}
