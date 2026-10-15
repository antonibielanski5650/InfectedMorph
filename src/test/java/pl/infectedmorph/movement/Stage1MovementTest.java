package pl.infectedmorph.movement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Stage1MovementTest {
    private final UUID id = new UUID(1, 2);
    private final Stage1Movement movement = new Stage1Movement();

    static final class Subject implements MovementPort {
        double base = .1;
        boolean alive = true, ground = true, sprinting = true, rejectRemoval, failAfterPut;
        final Map<String, Double> modifiers = new HashMap<>(Map.of("another_plugin:bonus", .25));
        public boolean aliveAndOnline() { return alive; }
        public boolean ordinaryGroundWalking() { return ground; }
        public double unrestrictedGroundSpeed() { return base * (1 + modifiers.getOrDefault("another_plugin:bonus",0.0)); }
        public void stopSprinting() { sprinting = false; }
        public void putOwnedModifier(double amount) {
            modifiers.put("infectedmorph:stage1_walking", amount);
            if (failAfterPut) throw new IllegalStateException("Simulated partial API failure");
        }
        public void removeOwnedModifier() {
            if (rejectRemoval) throw new IllegalStateException("Simulated cleanup failure");
            modifiers.remove("infectedmorph:stage1_walking");
        }
    }

    @ParameterizedTest @ValueSource(doubles = {.05, .1, .15, .25})
    void calibratesNominalFourBlocksPerSecondWithoutChangingBase(double base) {
        Subject p = new Subject(); p.base = base;
        movement.activate(id, p);
        double amount = p.modifiers.get("infectedmorph:stage1_walking");
        assertEquals(4, WalkingSpeed.estimateBlocksPerSecond(base * 1.25 * (1 + amount)), 1e-9);
        assertEquals(base, p.base);
        assertEquals(.25, p.modifiers.get("another_plugin:bonus"));
        movement.deactivate(id);
        assertEquals(Map.of("another_plugin:bonus", .25), p.modifiers);
        assertEquals(base, p.base);
    }

    @Test void rejectsRepeatedSprintStartsAndTickCorrectsServerSideSprintState() {
        Subject p = new Subject(); movement.activate(id, p);
        assertFalse(p.sprinting);
        for (int attempt = 0; attempt < 20; attempt++) {
            p.sprinting = true;
            assertTrue(movement.handleSprintAttempt(id, true));
            assertFalse(p.sprinting);
        }
        p.sprinting = true;
        assertTrue(movement.tick().isEmpty());
        assertFalse(p.sprinting);
        assertFalse(movement.handleSprintAttempt(id, false));
        movement.deactivate(id);
        p.sprinting = true;
        assertFalse(movement.handleSprintAttempt(id, true));
        assertTrue(p.sprinting, "Ending Stage 1 releases the sprint policy");
    }

    @Test void removesOnlyOwnedSpeedModifierOutsideGroundWalkingAndReappliesOnLanding() {
        Subject p = new Subject(); movement.activate(id, p);
        p.ground = false; movement.tick();
        assertEquals(Map.of("another_plugin:bonus", .25), p.modifiers);
        assertTrue(movement.isActive(id));
        p.ground = true; movement.tick();
        assertTrue(p.modifiers.containsKey("infectedmorph:stage1_walking"));
        p.base = .15; movement.tick();
        double amount = p.modifiers.get("infectedmorph:stage1_walking");
        assertEquals(4, WalkingSpeed.estimateBlocksPerSecond(.15 * 1.25 * (1 + amount)), 1e-9);
        assertEquals(.25, p.modifiers.get("another_plugin:bonus"));
    }

    @Test void deathLogoutAndDisableReleaseAllOwnedState() {
        Subject dead = new Subject(), offline = new Subject(), enabled = new Subject();
        UUID second = new UUID(3,4), third = new UUID(5,6);
        movement.activate(id,dead); movement.activate(second,offline); movement.activate(third,enabled);
        dead.alive = false; offline.alive = false;
        assertTrue(movement.tick().isEmpty());
        assertFalse(movement.isActive(id)); assertFalse(movement.isActive(second));
        assertEquals(Map.of("another_plugin:bonus", .25),dead.modifiers);
        assertTrue(movement.deactivateAll().isEmpty());
        assertFalse(movement.isActive(third));
        assertEquals(Map.of("another_plugin:bonus", .25),enabled.modifiers);
        // A fresh respawn/rejoin has no automatic infection or sprint lock.
        assertFalse(movement.handleSprintAttempt(id,true));
    }

    @Test void failedActivationAndInterruptedCleanupCannotKeepASprintPolicy() {
        Subject p = new Subject(); p.failAfterPut = true;
        assertThrows(IllegalStateException.class,()->movement.activate(id,p));
        assertFalse(movement.isActive(id));
        assertEquals(Map.of("another_plugin:bonus", .25),p.modifiers);
        p.failAfterPut = false; movement.activate(id,p); p.rejectRemoval = true;
        assertThrows(IllegalStateException.class,()->movement.deactivate(id));
        assertFalse(movement.isActive(id)); assertTrue(movement.cleanupPending(id));
        assertFalse(movement.handleSprintAttempt(id,true));
        assertEquals(1,movement.tick().size());
        p.rejectRemoval = false;
        assertTrue(movement.tick().isEmpty()); assertFalse(movement.cleanupPending(id));
        assertEquals(Map.of("another_plugin:bonus", .25),p.modifiers);
    }

    @Test void previewOrInactivePlayersNeverAcquireRestrictionsAndStaleCleanupIsOwnedOnly() {
        Subject p = new Subject();
        assertFalse(movement.handleSprintAttempt(id,true)); assertTrue(p.sprinting);
        assertTrue(movement.tick().isEmpty());
        p.modifiers.put("infectedmorph:stage1_walking",-.5);
        movement.clear(id,p);
        assertEquals(Map.of("another_plugin:bonus", .25),p.modifiers);
        assertFalse(movement.isActive(id));
    }

    @Test void invalidOrImmobilizedBaseDoesNotActivateOrTouchForeignModifiers() {
        for (double base : new double[]{0,-.1,Double.NaN,Double.POSITIVE_INFINITY}) {
            Subject p = new Subject(); p.base = base;
            assertThrows(IllegalArgumentException.class,()->movement.activate(id,p));
            assertFalse(movement.isActive(id));
            assertEquals(Map.of("another_plugin:bonus", .25),p.modifiers);
        }
    }
}
