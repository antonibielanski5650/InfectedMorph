package pl.infectedmorph.movement;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

public final class BukkitMovementPort implements MovementPort {
    private static final NamespacedKey KEY = new NamespacedKey("infectedmorph", "stage1_walking");
    private final Player player;
    private final java.util.function.Supplier<AttributeInstance> attributes;

    public BukkitMovementPort(Player player) { this(player, () -> player.getAttribute(Attribute.MOVEMENT_SPEED)); }

    BukkitMovementPort(Player player, java.util.function.Supplier<AttributeInstance> attributes) {
        this.player = player;
        this.attributes = attributes;
    }

    private AttributeInstance attribute() {
        AttributeInstance attribute = attributes.get();
        if (attribute == null) throw new IllegalStateException("The player has no movement-speed attribute.");
        return attribute;
    }

    @Override public boolean aliveAndOnline() { return player.isOnline() && !player.isDead() && player.getHealth() > 0; }

    @Override public boolean ordinaryGroundWalking() {
        return player.isOnGround() && !player.isInWater() && !player.isSwimming() && !player.isFlying()
                && !player.isGliding() && !player.isInsideVehicle();
    }

    @Override public double unrestrictedGroundSpeed() {
        AttributeInstance attribute = attribute();
        AttributeModifier owned = attribute.getModifier(KEY);
        if (owned == null) return attribute.getValue();
        if (owned.getOperation() != AttributeModifier.Operation.MULTIPLY_SCALAR_1 || owned.getAmount() <= -1) {
            attribute.removeModifier(KEY);
            return attribute.getValue();
        }
        // Factor out only our multiplier. Foreign equipment/effect/plugin modifiers remain installed.
        return attribute.getValue() / (1 + owned.getAmount());
    }

    @Override public void stopSprinting() { if (player.isSprinting()) player.setSprinting(false); }

    @Override public boolean isSprinting() { return player.isSprinting(); }

    @Override public void putOwnedModifier(double amount) {
        putOwnedModifier(amount, WalkingSpeed.TARGET_ATTRIBUTE);
    }

    @Override public void putOwnedModifier(double amount, double targetAttribute) {
        AttributeInstance attribute = attribute();
        AttributeModifier current = attribute.getModifier(KEY);
        if (current != null && current.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1
                && Math.abs(current.getAmount() - amount) < 1.0e-12) return;
        attribute.removeModifier(KEY);
        attribute.addTransientModifier(new AttributeModifier(KEY, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        AttributeModifier applied = attribute.getModifier(KEY);
        if (applied == null || Math.abs(applied.getAmount() - amount) >= 1.0e-12) {
            throw new IllegalStateException("The infection movement modifier was not accepted by the server.");
        }
        if (Math.abs(attribute.getValue() - targetAttribute) > 1.0e-6) {
            throw new IllegalStateException("The effective walking attribute could not be calibrated safely.");
        }
    }

    @Override public void removeOwnedModifier() {
        AttributeInstance attribute = attributes.get();
        if (attribute != null) {
            attribute.removeModifier(KEY);
            if (attribute.getModifier(KEY) != null) throw new IllegalStateException("The infection movement modifier could not be removed.");
        }
    }
}
