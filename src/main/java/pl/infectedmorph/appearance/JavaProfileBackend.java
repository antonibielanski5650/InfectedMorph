package pl.infectedmorph.appearance;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.entity.Player;

public final class JavaProfileBackend implements AppearanceBackend {
    @Override public void apply(Player player, SignedTexture texture) {
        PlayerProfile profile = player.getPlayerProfile().clone();
        profile.setProperty(new ProfileProperty("textures", texture.value(), texture.signature()));
        player.setPlayerProfile(profile);
        if (!hasTexture(player, texture.value(), texture.signature())) {
            throw new IllegalStateException("The server did not retain the requested signed skin texture.");
        }
    }

    @Override public void restore(Player player, PlayerProfile original) {
        // Preserve the current name/UUID and unrelated properties; restore exact original signed textures.
        PlayerProfile current = player.getPlayerProfile().clone();
        current.removeProperty("textures");
        for (ProfileProperty property : original.getProperties()) {
            if (property.getName().equals("textures")) current.setProperty(property);
        }
        player.setPlayerProfile(current);
        var expected = original.getProperties().stream().filter(p -> p.getName().equals("textures"))
                .map(p -> p.getValue() + "|" + p.getSignature()).sorted().toList();
        var actual = player.getPlayerProfile().getProperties().stream().filter(p -> p.getName().equals("textures"))
                .map(p -> p.getValue() + "|" + p.getSignature()).sorted().toList();
        if (!actual.equals(expected)) throw new IllegalStateException("The server did not retain the restored skin texture.");
    }

    public static boolean hasTexture(Player player, String value, String signature) {
        return player.getPlayerProfile().getProperties().stream().anyMatch(p -> p.getName().equals("textures")
                && p.getValue().equals(value) && java.util.Objects.equals(p.getSignature(), signature));
    }
}
