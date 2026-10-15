package pl.infectedmorph.appearance;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.entity.Player;

/** Main-thread renderer boundary. Later models can implement a separate backend. */
public interface AppearanceBackend {
    void apply(Player player, SignedTexture texture);
    void restore(Player player, PlayerProfile original);
}
