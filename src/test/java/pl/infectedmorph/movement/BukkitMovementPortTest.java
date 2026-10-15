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

class BukkitMovementPortTest {
    @Test void usesTransientOwnedModifierAndNeverRewritesBaseOrWalkFlySpeeds() {
        Player player = mock(Player.class);
        AttributeInstance attribute = mock(AttributeInstance.class);
        Map<Key,AttributeModifier> modifiers = new HashMap<>();
        var foreign = new AttributeModifier(new NamespacedKey("another_plugin","speed"),.25,AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        modifiers.put(foreign.getKey(),foreign);
        when(attribute.getBaseValue()).thenReturn(.1);
        when(attribute.getValue()).thenAnswer(call -> .1 * modifiers.values().stream()
                .mapToDouble(m -> 1 + m.getAmount()).reduce(1, (left,right)->left*right));
        when(attribute.getModifier(any(Key.class))).thenAnswer(call->modifiers.get(call.getArgument(0)));
        doAnswer(call->{modifiers.remove(call.getArgument(0));return null;}).when(attribute).removeModifier(any(Key.class));
        doAnswer(call->{AttributeModifier m=call.getArgument(0);modifiers.put(m.getKey(),m);return null;})
                .when(attribute).addTransientModifier(any(AttributeModifier.class));
        var port = new BukkitMovementPort(player,()->attribute);
        double amount=WalkingSpeed.modifierAmount(port.unrestrictedGroundSpeed());
        port.putOwnedModifier(amount);
        port.putOwnedModifier(amount);
        assertEquals(2,modifiers.size());
        assertSame(foreign,modifiers.get(foreign.getKey()));
        verify(attribute,times(1)).addTransientModifier(any(AttributeModifier.class));
        port.removeOwnedModifier();
        assertEquals(Map.of(foreign.getKey(),foreign),modifiers);
        verify(attribute,never()).setBaseValue(anyDouble());
        verify(attribute,never()).addModifier(any(AttributeModifier.class));
        verify(player,never()).setWalkSpeed(anyFloat());
        verify(player,never()).setFlySpeed(anyFloat());
        verify(player,never()).setVelocity(any());
    }

    @Test void skipsSpecialMovementContextsAndStopsOnlyExistingSprint() {
        Player player=mock(Player.class);
        var port=new BukkitMovementPort(player,()->mock(AttributeInstance.class));
        when(player.isOnGround()).thenReturn(true);
        assertTrue(port.ordinaryGroundWalking());
        when(player.isInWater()).thenReturn(true);assertFalse(port.ordinaryGroundWalking());
        when(player.isInWater()).thenReturn(false);when(player.isFlying()).thenReturn(true);assertFalse(port.ordinaryGroundWalking());
        when(player.isFlying()).thenReturn(false);when(player.isGliding()).thenReturn(true);assertFalse(port.ordinaryGroundWalking());
        when(player.isGliding()).thenReturn(false);when(player.isInsideVehicle()).thenReturn(true);assertFalse(port.ordinaryGroundWalking());
        when(player.isInsideVehicle()).thenReturn(false);when(player.isOnGround()).thenReturn(false);assertFalse(port.ordinaryGroundWalking());
        when(player.isSprinting()).thenReturn(false);port.stopSprinting();verify(player,never()).setSprinting(false);
        when(player.isSprinting()).thenReturn(true);port.stopSprinting();verify(player).setSprinting(false);
    }

    @Test void treatsMissingAttributeAsAnActivationErrorWithoutChangingAbilities() {
        Player player=mock(Player.class);
        var port=new BukkitMovementPort(player,()->null);
        assertThrows(IllegalStateException.class,port::unrestrictedGroundSpeed);
        assertDoesNotThrow(port::removeOwnedModifier);
        verifyNoInteractions(player);
    }
}
