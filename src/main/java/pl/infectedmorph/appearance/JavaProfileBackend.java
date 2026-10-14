package pl.infectedmorph.appearance;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.entity.Player;

public final class JavaProfileBackend implements AppearanceBackend {
    @Override public void apply(Player player, SignedTexture texture) {
        PlayerProfile profile = player.getPlayerProfile();
        profile.setProperty(new ProfileProperty("textures", texture.value(), texture.signature()));
        player.setPlayerProfile(profile);
    }

    @Override public void restore(Player player, PlayerProfile original) {
        // Preserve the current name/UUID and unrelated properties; restore exact original signed textures.
        PlayerProfile current = player.getPlayerProfile();
        current.removeProperty("textures");
        for (ProfileProperty property : original.getProperties()) {
            if (property.getName().equals("textures")) current.setProperty(property);
        }
        player.setPlayerProfile(current);
    }
}
