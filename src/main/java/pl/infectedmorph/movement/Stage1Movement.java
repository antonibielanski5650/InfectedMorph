package pl.infectedmorph.movement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Main-thread lifecycle with retained cleanup entries if a server API operation fails. */
public final class Stage1Movement {
    private static final class Entry {
        final MovementPort port;
        boolean active;
        Entry(MovementPort port) { this.port = port; }
    }
    public record Failure(UUID player, RuntimeException error) {}
    private final Map<UUID, Entry> entries = new HashMap<>();

    public void activate(UUID id, MovementPort port) {
        if (isActive(id)) throw new IllegalStateException("Stage 1 movement is already active.");
        deactivate(id);
        WalkingSpeed.modifierAmount(port.unrestrictedGroundSpeed()); // Validate even if the player is currently airborne.
        Entry entry = new Entry(port);
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
    public boolean handleSprintAttempt(UUID id, boolean starting) {
        if (!starting || !isActive(id)) return false;
        entries.get(id).port.stopSprinting();
        return true;
    }

    private void enforce(Entry entry) {
        entry.port.stopSprinting();
        if (entry.port.ordinaryGroundWalking()) entry.port.putOwnedModifier(WalkingSpeed.modifierAmount(entry.port.unrestrictedGroundSpeed()));
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
