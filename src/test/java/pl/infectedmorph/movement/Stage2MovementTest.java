package pl.infectedmorph.movement;

import org.junit.jupiter.api.Test;
import pl.infectedmorph.domain.ActivationTransaction;
import pl.infectedmorph.domain.InfectionStage;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Stage2MovementTest {
    private final UUID id = new UUID(8, 2);
    private final Stage1Movement movement = new Stage1Movement();

    static final class Subject implements MovementPort {
        boolean sprinting, ground = true, alive = true, fail;
        double base = .1;
        double scaleBase = 1, foreignScale = 1, ownedScale = 1;
        boolean failScale, rejectScaleRemoval, rejectSpeedRemoval;
        final Map<String, Double> modifiers = new HashMap<>(Map.of("foreign", .25));
        public boolean aliveAndOnline() { return alive; }
        public boolean ordinaryGroundWalking() { return ground; }
        public boolean isSprinting() { return sprinting; }
        public double unrestrictedGroundSpeed() { return base * (1 + modifiers.get("foreign")) * (sprinting ? 1.3 : 1); }
        public void stopSprinting() { sprinting = false; }
        public void putOwnedModifier(double amount) {
            modifiers.put("owned", amount);
            if (fail) throw new IllegalStateException("Simulated movement API failure");
        }
        public void removeOwnedModifier() {
            if (rejectSpeedRemoval) throw new IllegalStateException("Simulated movement cleanup failure");
            modifiers.remove("owned");
        }
        public void applyStage2Scale() {
            ownedScale = 5.0 / 3;
            if (failScale) throw new IllegalStateException("Simulated partial scale failure");
        }
        public void removeOwnedScale() {
            if (rejectScaleRemoval) throw new IllegalStateException("Simulated scale cleanup failure");
            ownedScale = 1;
        }
        double scale() { return scaleBase * foreignScale * ownedScale; }
        double speed() { return WalkingSpeed.estimateBlocksPerSecond(unrestrictedGroundSpeed() * (1 + modifiers.getOrDefault("owned", 0.0))); }
    }

    @Test void calibratesSixWalkingAndNineSprintingIncludingVanillaSprintBonus() {
        Subject p = new Subject(); movement.activate(id, p, InfectionStage.STAGE2);
        assertEquals(6, p.speed(), 1e-9);
        assertFalse(movement.blocksSprinting(id));
        for (int i = 0; i < 10; i++) {
            assertFalse(movement.handleSprintAttempt(id, true));
            p.sprinting = true; assertTrue(movement.tick().isEmpty());
            assertTrue(p.sprinting); assertEquals(9, p.speed(), 1e-9);
            p.sprinting = false; movement.tick(); assertEquals(6, p.speed(), 1e-9);
        }
        p.modifiers.put("foreign", .75); p.base = .15; movement.tick();
        assertEquals(6, p.speed(), 1e-9);
        movement.deactivate(id);
        assertEquals(Map.of("foreign", .75), p.modifiers); assertEquals(.15, p.base);
    }

    @Test void transitionsStage1Stage2Stage1NormalReleaseSprintAndRestoreForeignState() {
        Subject p = new Subject(); p.sprinting = true;
        movement.activate(id, p); assertEquals(4, p.speed(), 1e-9); assertFalse(p.sprinting);
        assertTrue(movement.handleSprintAttempt(id, true));
        movement.deactivate(id); movement.activate(id, p, InfectionStage.STAGE2);
        assertEquals(6, p.speed(), 1e-9); assertFalse(movement.handleSprintAttempt(id, true));
        p.sprinting = true; movement.tick(); assertEquals(9, p.speed(), 1e-9);
        movement.deactivate(id); movement.activate(id, p, InfectionStage.STAGE1);
        assertFalse(p.sprinting); assertEquals(4, p.speed(), 1e-9);
        movement.deactivate(id); assertFalse(movement.handleSprintAttempt(id, true));
        assertEquals(Map.of("foreign", .25), p.modifiers);
    }

    @Test void removesGroundModifierForSpecialMovementAndCleansOnDeathLogoutAndDisable() {
        Subject p = new Subject(); movement.activate(id, p, InfectionStage.STAGE2);
        p.sprinting = true; p.ground = false; movement.tick();
        assertEquals(Map.of("foreign", .25), p.modifiers); assertTrue(p.sprinting);
        p.ground = true; movement.tick(); assertEquals(9, p.speed(), 1e-9);
        p.alive = false; movement.tick(); assertFalse(movement.isActive(id));
        assertEquals(Map.of("foreign", .25), p.modifiers);
        p.alive = true; movement.activate(id, p, InfectionStage.STAGE2);
        assertTrue(movement.deactivateAll().isEmpty()); assertEquals(Map.of("foreign", .25), p.modifiers);
    }

    @Test void failedStage2ActivationRollsBackTextureAndOwnedMovement() {
        Subject p = new Subject(); p.fail = true;
        String[] texture = {"original"};
        var result = ActivationTransaction.activate(() -> texture[0] = "stage2", () -> {},
                () -> movement.activate(id, p, InfectionStage.STAGE2), () -> movement.deactivate(id), () -> texture[0] = "original");
        assertEquals(ActivationTransaction.Outcome.ROLLED_BACK, result.outcome());
        assertEquals("original", texture[0]); assertFalse(movement.isActive(id));
        assertFalse(movement.blocksSprinting(id)); assertEquals(Map.of("foreign", .25), p.modifiers);
    }

    @Test void normalIsNotAnActivatableMovementStage() {
        assertThrows(IllegalArgumentException.class, () -> movement.activate(id, new Subject(), InfectionStage.NORMAL));
        assertFalse(movement.isActive(id));
    }
}
