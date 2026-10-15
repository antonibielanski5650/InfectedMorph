package pl.infectedmorph.movement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pl.infectedmorph.domain.InfectionStage;

/** Shared main-thread Stage 1/2 lifecycle; retained class name keeps the Stage 1 integration compatible. */
public final class Stage1Movement {
    private static final class Entry {
        final MovementPort port;
        final InfectionStage stage;
        boolean active;
        Entry(MovementPort port) { this(port, InfectionStage.STAGE1); }
        Entry(MovementPort port, InfectionStage stage) { this.port = port; this.stage = stage; }
    }
    public record Failure(UUID player, RuntimeException error) {}
    private final Map<UUID, Entry> entries = new HashMap<>();

    public void activate(UUID id, MovementPort port) {
        activate(id, port, InfectionStage.STAGE1);
    }

    public void activate(UUID id, MovementPort port, InfectionStage stage) {
        if (stage == null || stage == InfectionStage.NORMAL) throw new IllegalArgumentException("An infected movement stage is required.");
        if (isActive(id)) throw new IllegalStateException("Infection movement is already active.");
        deactivate(id);
        WalkingSpeed.modifierAmount(port.unrestrictedGroundSpeed()); // Validate even if the player is currently airborne.
        Entry entry = new Entry(port, stage);
        entries.put(id, entry);
        try {
            if (!port.aliveAndOnline()) throw new IllegalStateException("The player is no longer alive and online.");
            enforce(entry);
            entry.active = true;
        } catch (RuntimeException failure) {
            try { deactivate(id); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public boolean isActive(UUID id) {
        Entry entry = entries.get(id);
        return entry != null && entry.active;
    }

    /** True means the event adapter must cancel this sprint start, regardless of input device. */
    public boolean blocksSprinting(UUID id) {
        return isActive(id) && entries.get(id).stage == InfectionStage.STAGE1;
    }

    public boolean handleSprintAttempt(UUID id, boolean starting) {
        if (!starting || !blocksSprinting(id)) return false;
        Entry entry = entries.get(id);
        entry.port.stopSprinting();
        // Reject client prediction too: re-add our ground modifier to dirty the attribute and resend it.
        // Without this, a cancelled sprint start can leave a vanilla client's locally predicted sprint bonus.
        if (entry.port.ordinaryGroundWalking()) {
            entry.port.removeOwnedModifier();
            entry.port.putOwnedModifier(WalkingSpeed.modifierAmount(entry.port.unrestrictedGroundSpeed()));
        }
        return true;
    }

    private void enforce(Entry entry) {
        if (entry.stage == InfectionStage.STAGE1) entry.port.stopSprinting();
        double target = entry.stage == InfectionStage.STAGE1 ? 4 : entry.port.isSprinting() ? 9 : 6;
        if (entry.port.ordinaryGroundWalking()) entry.port.putOwnedModifier(
                WalkingSpeed.modifierAmount(entry.port.unrestrictedGroundSpeed(), target), WalkingSpeed.attributeFor(target));
        else entry.port.removeOwnedModifier();
    }

    public void deactivate(UUID id) {
        Entry entry = entries.get(id);
        if (entry == null) return;
        entry.active = false; // Release sprint policy even if modifier cleanup throws.
        entry.port.removeOwnedModifier();
        entries.remove(id);
    }

    /** Also removes a stale modifier after a reload/join, retaining retries if removal fails. */
    public void clear(UUID id, MovementPort fallback) {
        entries.computeIfAbsent(id, ignored -> new Entry(fallback));
        deactivate(id);
    }

    public List<Failure> tick() {
        List<Failure> failures = new ArrayList<>();
        for (var pair : List.copyOf(entries.entrySet())) {
            UUID id = pair.getKey();
            Entry entry = pair.getValue();
            try {
                if (!entry.active || !entry.port.aliveAndOnline()) deactivate(id);
                else enforce(entry);
            } catch (RuntimeException error) { failures.add(new Failure(id, error)); }
        }
        return failures;
    }

    public boolean cleanupPending(UUID id) {
        Entry entry = entries.get(id);
        return entry != null && !entry.active;
    }

    public List<Failure> deactivateAll() {
        List<Failure> failures = new ArrayList<>();
        for (UUID id : List.copyOf(entries.keySet())) {
            try { deactivate(id); } catch (RuntimeException error) { failures.add(new Failure(id, error)); }
        }
        return failures;
    }
}
