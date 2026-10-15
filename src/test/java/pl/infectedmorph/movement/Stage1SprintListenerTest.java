package pl.infectedmorph.movement;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Stage1SprintListenerTest {
    @Test void stage2AllowsActualBukkitSprintEventsAndPreservesForeignCancellations() {
        UUID id = new UUID(7, 2);
        Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(id);
        var movement = new Stage1Movement();
        movement.activate(id, new Stage2MovementTest.Subject(), pl.infectedmorph.domain.InfectionStage.STAGE2);
        var listener = new Stage1SprintListener(movement, p -> fail("No failure expected"));
        var start = new PlayerToggleSprintEvent(player, true); listener.onSprint(start);
        assertFalse(start.isCancelled());
        var blockedByAnotherPlugin = new PlayerToggleSprintEvent(player, true); blockedByAnotherPlugin.setCancelled(true);
        listener.onSprint(blockedByAnotherPlugin); assertTrue(blockedByAnotherPlugin.isCancelled());
        var stop = new PlayerToggleSprintEvent(player, false); listener.onSprint(stop); assertFalse(stop.isCancelled());
    }

    @Test void actualBukkitSprintEventsAreCancelledForActiveStageOnly() {
        UUID id=new UUID(1,2);
        Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(id);
        Stage1Movement movement=new Stage1Movement();
        var subject=new Stage1MovementTest.Subject();
        var listener=new Stage1SprintListener(movement,p->fail("No failure handler expected"));
        var preview=new PlayerToggleSprintEvent(player,true);listener.onSprint(preview);assertFalse(preview.isCancelled());
        movement.activate(id,subject);
        for(int attempt=0;attempt<5;attempt++) {
            var sprintKeyOrDoubleTap=new PlayerToggleSprintEvent(player,true);
            listener.onSprint(sprintKeyOrDoubleTap);
            assertTrue(sprintKeyOrDoubleTap.isCancelled());
            assertFalse(subject.sprinting);
        }
        var stop=new PlayerToggleSprintEvent(player,false);listener.onSprint(stop);assertFalse(stop.isCancelled());
        movement.deactivate(id);
        var restored=new PlayerToggleSprintEvent(player,true);listener.onSprint(restored);assertFalse(restored.isCancelled());
    }

    @Test void doesNotUncancelAnotherPluginsEvent() {
        Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(new UUID(3,4));
        var event=new PlayerToggleSprintEvent(player,true);event.setCancelled(true);
        new Stage1SprintListener(new Stage1Movement(),p->{}).onSprint(event);
        assertTrue(event.isCancelled());
    }
}
