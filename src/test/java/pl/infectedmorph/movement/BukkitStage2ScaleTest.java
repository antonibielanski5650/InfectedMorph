package pl.infectedmorph.movement;

import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BukkitStage2ScaleTest {
    private static final NamespacedKey OWNED = new NamespacedKey("infectedmorph", "stage2_scale");

    private static final class AttributeFixture {
        final AttributeInstance attribute = mock(AttributeInstance.class);
        final Map<Key, AttributeModifier> modifiers = new HashMap<>();
        double base = 1, maximum = 16;
        boolean rejectAdd, rejectRemove;
        AttributeFixture() {
            when(attribute.getValue()).thenAnswer(call -> {
                double flat = 0, scalar = 0, factor = 1;
                for (var m : modifiers.values()) switch (m.getOperation()) {
                    case ADD_NUMBER -> flat += m.getAmount();
                    case ADD_SCALAR -> scalar += m.getAmount();
                    case MULTIPLY_SCALAR_1 -> factor *= 1 + m.getAmount();
                }
                return Math.min(maximum, (base + flat) * (1 + scalar) * factor);
            });
            when(attribute.getModifier(any(Key.class))).thenAnswer(call -> modifiers.get(call.getArgument(0)));
            doAnswer(call -> {
                if (!rejectRemove) modifiers.remove(call.getArgument(0));
                return null;
            }).when(attribute).removeModifier(any(Key.class));
            doAnswer(call -> {
                AttributeModifier m = call.getArgument(0);
                if (!rejectAdd) modifiers.put(m.getKey(), m);
                return null;
            }).when(attribute).addTransientModifier(any(AttributeModifier.class));
        }
        void foreign(String name, double amount, AttributeModifier.Operation operation) {
            var m = new AttributeModifier(new NamespacedKey("other_plugin", name), amount, operation);
            modifiers.put(m.getKey(), m);
        }
        BukkitStage2Scale adapter() { return new BukkitStage2Scale(() -> attribute); }
    }

    @Test void usesNativeTransientFiveThirdsMultiplierAndIsIdempotent() {
        var f = new AttributeFixture(); var scale = f.adapter();
        scale.apply(); scale.apply();
        assertEquals(5.0 / 3, f.attribute.getValue(), 1e-12);
        var owned = f.modifiers.get(OWNED);
        assertEquals(AttributeModifier.Operation.MULTIPLY_SCALAR_1, owned.getOperation());
        assertEquals(2.0 / 3, owned.getAmount(), 1e-12);
        verify(f.attribute, times(1)).addTransientModifier(any(AttributeModifier.class));
        verify(f.attribute, never()).setBaseValue(anyDouble());
        verify(f.attribute, never()).addModifier(any(AttributeModifier.class));
        scale.remove(); scale.remove();
        assertEquals(1, f.attribute.getValue()); assertTrue(f.modifiers.isEmpty());
    }

    @Test void preservesAllForeignOperationsAndMidInfectionChangesWithoutRestoringAStaleSnapshot() {
        var f = new AttributeFixture();
        f.foreign("flat", .2, AttributeModifier.Operation.ADD_NUMBER);
        f.foreign("base_multiplier", .1, AttributeModifier.Operation.ADD_SCALAR);
        f.foreign("final_multiplier", .25, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        var before = Map.copyOf(f.modifiers);
        double original = f.attribute.getValue(); var scale = f.adapter(); scale.apply();
        assertEquals(original * 5 / 3, f.attribute.getValue(), 1e-12);
        before.forEach((key,value) -> assertSame(value, f.modifiers.get(key)));
        f.base = .9; f.foreign("new_effect", .5, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        double infected = f.attribute.getValue(); scale.apply();
        assertEquals(infected, f.attribute.getValue(), 1e-12);
        scale.remove();
        assertEquals(infected / (5.0 / 3), f.attribute.getValue(), 1e-12);
        assertEquals(.9, f.base); assertEquals(4, f.modifiers.size());
        before.forEach((key,value) -> assertSame(value, f.modifiers.get(key)));
    }

    @Test void replacesOnlyStaleOwnedModifierAndReappliesAfterExternalRemoval() {
        var f = new AttributeFixture();
        f.foreign("size", .1, AttributeModifier.Operation.ADD_NUMBER);
        f.modifiers.put(OWNED, new AttributeModifier(OWNED, 5, AttributeModifier.Operation.ADD_NUMBER));
        var scale = f.adapter(); scale.apply();
        assertEquals(1.1 * 5 / 3, f.attribute.getValue(), 1e-12);
        f.modifiers.remove(OWNED); scale.apply();
        assertEquals(1.1 * 5 / 3, f.attribute.getValue(), 1e-12);
        scale.remove(); assertEquals(1.1, f.attribute.getValue(), 1e-12);
        assertEquals(1, f.modifiers.size());
    }

    @Test void reportsMissingInvalidRejectedAndClampedScaleInsteadOfSuccessfulActivation() {
        var missing = new BukkitStage2Scale(() -> null);
        assertThrows(IllegalStateException.class, missing::apply); assertDoesNotThrow(missing::remove);
        var f = new AttributeFixture(); var scale = f.adapter();
        for (double invalid : new double[]{0, -1, Double.NaN}) {
            f.base = invalid; assertThrows(IllegalStateException.class, scale::apply);
            assertFalse(f.modifiers.containsKey(OWNED));
        }
        f.base = 1; f.rejectAdd = true;
        assertThrows(IllegalStateException.class, scale::apply);
        f.rejectAdd = false; f.base = 12;
        assertThrows(IllegalStateException.class, scale::apply);
        scale.remove(); assertEquals(12, f.attribute.getValue());
    }

    @Test void failedRemovalRemainsVisibleAndCanBeRetried() {
        var f = new AttributeFixture(); var scale = f.adapter(); scale.apply(); f.rejectRemove = true;
        assertThrows(IllegalStateException.class, scale::remove);
        assertTrue(f.modifiers.containsKey(OWNED));
        f.rejectRemove = false; scale.remove(); assertEquals(1, f.attribute.getValue());
    }

    @Test void actualPlayerPortDelegatesToScaleWithoutTouchingMovementOrPlayerAbilities() {
        var f = new AttributeFixture(); var player = mock(Player.class);
        var movementAttribute = mock(AttributeInstance.class);
        var port = new BukkitMovementPort(player, () -> movementAttribute, () -> f.attribute);
        port.applyStage2Scale(); assertEquals(5.0 / 3, f.attribute.getValue(), 1e-12);
        port.removeOwnedScale(); assertEquals(1, f.attribute.getValue());
        verifyNoInteractions(movementAttribute, player);
    }
}
