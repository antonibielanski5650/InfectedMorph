package pl.infectedmorph.movement;

import org.junit.jupiter.api.Test;
import pl.infectedmorph.domain.ActivationTransaction;
import pl.infectedmorph.domain.InfectionStage;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Stage2ScaleLifecycleTest {
    private final Stage1Movement controller = new Stage1Movement();
    private final UUID id = new UUID(5, 3);

    @Test void normalStage1Stage2Stage1NormalRestoresScaleWithoutStacking() {
        var p = new Stage2MovementTest.Subject(); p.scaleBase = .9; p.foreignScale = 1.2;
        double original = p.scale();
        for (int cycle = 0; cycle < 3; cycle++) {
            controller.activate(id, p, InfectionStage.STAGE1); assertEquals(original, p.scale(), 1e-12);
            controller.deactivate(id); controller.activate(id, p, InfectionStage.STAGE2);
            assertEquals(original * 5 / 3, p.scale(), 1e-12);
            for (int tick = 0; tick < 20; tick++) assertTrue(controller.tick().isEmpty());
            assertEquals(original * 5 / 3, p.scale(), 1e-12);
            controller.deactivate(id); controller.activate(id, p, InfectionStage.STAGE1);
            assertEquals(original, p.scale(), 1e-12);
            controller.deactivate(id); assertEquals(original, p.scale(), 1e-12);
        }
    }

    @Test void sizePersistsInAirAndWaterWhileGroundSpeedIsSuspendedAndForeignChangesSurviveExit() {
        var p = new Stage2MovementTest.Subject(); controller.activate(id, p, InfectionStage.STAGE2);
        p.ground = false; controller.tick();
        assertFalse(p.modifiers.containsKey("owned")); assertEquals(5.0 / 3, p.scale(), 1e-12);
        p.scaleBase = 1.1; p.foreignScale = .8; controller.tick();
        assertEquals(1.1 * .8 * 5 / 3, p.scale(), 1e-12);
        controller.deactivate(id); assertEquals(1.1 * .8, p.scale(), 1e-12);
    }

    @Test void deathLogoutDisableAndRespawnOrJoinStaleCleanupRemoveScale() {
        var p = new Stage2MovementTest.Subject();
        controller.activate(id, p, InfectionStage.STAGE2); p.alive = false;
        assertTrue(controller.tick().isEmpty()); assertEquals(1, p.scale()); assertFalse(controller.isActive(id));
        p.alive = true; controller.activate(id, p, InfectionStage.STAGE2);
        controller.clear(id, p); assertEquals(1, p.scale()); // Respawn/quit use the same cleanup path.
        controller.activate(id, p, InfectionStage.STAGE2);
        assertTrue(controller.deactivateAll().isEmpty()); assertEquals(1, p.scale());
        p.ownedScale = 5.0 / 3; controller.clear(id, p); // Join/startup without an active session.
        assertEquals(1, p.scale()); assertFalse(controller.isActive(id));
    }

    @Test void partialScaleFailureRollsBackSkinMovementAndScaleWithoutActivating() {
        var p = new Stage2MovementTest.Subject(); p.failScale = true;
        String[] texture = {"original"};
        var result = ActivationTransaction.activate(() -> texture[0] = "stage2", () -> {},
                () -> controller.activate(id, p, InfectionStage.STAGE2), () -> controller.clear(id, p), () -> texture[0] = "original");
        assertEquals(ActivationTransaction.Outcome.ROLLED_BACK, result.outcome());
        assertEquals("original", texture[0]); assertEquals(1, p.scale());
        assertFalse(p.modifiers.containsKey("owned")); assertFalse(controller.isActive(id));
    }

    @Test void movementFailureAfterScaleApplicationStillRestoresScale() {
        var p = new Stage2MovementTest.Subject(); p.fail = true;
        assertThrows(IllegalStateException.class, () -> controller.activate(id, p, InfectionStage.STAGE2));
        assertEquals(1, p.scale()); assertFalse(controller.isActive(id)); assertFalse(p.modifiers.containsKey("owned"));
    }

    @Test void cleanupAttemptsBothAttributesIndependentlyAndRetainsRetries() {
        var p = new Stage2MovementTest.Subject(); controller.activate(id, p, InfectionStage.STAGE2);
        p.rejectSpeedRemoval = true;
        assertThrows(IllegalStateException.class, () -> controller.deactivate(id));
        assertEquals(1, p.scale(), "Scale must be removed even when movement removal fails");
        assertTrue(controller.cleanupPending(id)); assertFalse(controller.blocksSprinting(id));
        p.rejectSpeedRemoval = false; controller.tick(); assertFalse(controller.cleanupPending(id));
        controller.activate(id, p, InfectionStage.STAGE2); p.rejectScaleRemoval = true;
        assertThrows(IllegalStateException.class, () -> controller.deactivate(id));
        assertFalse(p.modifiers.containsKey("owned"), "Movement must be removed even when scale removal fails");
        assertTrue(controller.cleanupPending(id)); assertFalse(controller.isActive(id));
        p.rejectScaleRemoval = false; assertTrue(controller.tick().isEmpty());
        assertEquals(1, p.scale()); assertFalse(controller.cleanupPending(id));
    }
}
