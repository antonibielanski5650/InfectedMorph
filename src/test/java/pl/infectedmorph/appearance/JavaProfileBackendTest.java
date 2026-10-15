package pl.infectedmorph.appearance;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JavaProfileBackendTest {
    private PlayerProfile profile(Set<ProfileProperty> initial) {
        Set<ProfileProperty> properties = new HashSet<>(initial);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.getProperties()).thenReturn(properties);
        when(profile.clone()).thenAnswer(call->profile(properties));
        doAnswer(call->{
            ProfileProperty property=call.getArgument(0);
            properties.removeIf(p->p.getName().equals(property.getName()));
            properties.add(property);return null;
        }).when(profile).setProperty(any(ProfileProperty.class));
        when(profile.removeProperty(anyString())).thenAnswer(call->properties.removeIf(p->p.getName().equals(call.getArgument(0))));
        return profile;
    }

    @Test void appliesThroughNativeRefreshApiAndRestoresSignedOriginalWithoutMutatingSource() {
        ProfileProperty originalTexture = new ProfileProperty("textures","original-value","original-signature");
        PlayerProfile original = profile(Set.of(originalTexture));
        AtomicReference<PlayerProfile> live = new AtomicReference<>(original);
        Player player = mock(Player.class);
        when(player.getPlayerProfile()).thenAnswer(call->live.get());
        doAnswer(call->{live.set(call.getArgument(0));return null;}).when(player).setPlayerProfile(any(PlayerProfile.class));
        JavaProfileBackend backend = new JavaProfileBackend();
        backend.apply(player,new SignedTexture("infected-value","infected-signature"));
        assertNotSame(original,live.get());
        assertEquals(Set.of(originalTexture),original.getProperties());
        assertTrue(JavaProfileBackend.hasTexture(player,"infected-value","infected-signature"));
        var foreign = new ProfileProperty("other-plugin","keep-me");
        live.get().getProperties().add(foreign);
        backend.restore(player,original);
        assertTrue(JavaProfileBackend.hasTexture(player,"original-value","original-signature"));
        assertTrue(live.get().getProperties().contains(foreign));
        verify(player,times(2)).setPlayerProfile(any(PlayerProfile.class));
    }

    @Test void rejectsSilentProfileApplicationFailure() {
        PlayerProfile original = profile(Set.of(new ProfileProperty("textures","original","signed")));
        Player player = mock(Player.class);when(player.getPlayerProfile()).thenReturn(original);
        // Simulate a server/another plugin refusing to retain the requested replacement.
        assertThrows(IllegalStateException.class,()->new JavaProfileBackend().apply(player,new SignedTexture("new","new-signature")));
        assertTrue(JavaProfileBackend.hasTexture(player,"original","signed"));
    }

    @Test void rejectsSilentRestorationFailure() {
        PlayerProfile original = profile(Set.of(new ProfileProperty("textures","original","signed")));
        PlayerProfile infected = profile(Set.of(new ProfileProperty("textures","infected","new-signature")));
        Player player = mock(Player.class);when(player.getPlayerProfile()).thenReturn(infected);
        assertThrows(IllegalStateException.class,()->new JavaProfileBackend().restore(player,original));
        assertTrue(JavaProfileBackend.hasTexture(player,"infected","new-signature"));
    }
}
