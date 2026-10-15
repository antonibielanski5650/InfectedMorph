package pl.infectedmorph.movement;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import java.util.function.Supplier;

/** Native SCALE affects both the vanilla model and dimensions; never rewrites a foreign scale baseline. */
public final class BukkitStage2Scale {
    public static final double FACTOR = 5.0 / 3.0;
    private static final NamespacedKey KEY = new NamespacedKey("infectedmorph", "stage2_scale");
    private final Supplier<AttributeInstance> attributes;

    public BukkitStage2Scale(Supplier<AttributeInstance> attributes) { this.attributes = attributes; }

    public void apply() {
        AttributeInstance attribute = attributes.get();
        if (attribute == null) throw new IllegalStateException("The player has no scale attribute.");
        AttributeModifier current = attribute.getModifier(KEY);
        if (current != null && current.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1
                && Math.abs(current.getAmount() - (FACTOR - 1)) < 1.0e-12) return;
        remove();
        double original = attribute.getValue();
        if (!Double.isFinite(original) || original <= 0) throw new IllegalStateException("The effective player scale is invalid.");
        attribute.addTransientModifier(new AttributeModifier(KEY, FACTOR - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        AttributeModifier applied = attribute.getModifier(KEY);
        if (applied == null || applied.getOperation() != AttributeModifier.Operation.MULTIPLY_SCALAR_1
                || Math.abs(applied.getAmount() - (FACTOR - 1)) >= 1.0e-12
                || !Double.isFinite(attribute.getValue()) || Math.abs(attribute.getValue() - original * FACTOR) > 1.0e-6) {
            throw new IllegalStateException("The Stage 2 scale multiplier was rejected or clamped by the server.");
        }
    }

    public void remove() {
        AttributeInstance attribute = attributes.get();
        if (attribute == null || attribute.getModifier(KEY) == null) return;
        attribute.removeModifier(KEY);
        if (attribute.getModifier(KEY) != null) throw new IllegalStateException("The Stage 2 scale modifier could not be removed.");
    }
}
