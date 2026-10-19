package us.to.midensthings.serverLevels.data;

import org.bukkit.entity.Player;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerCache {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    private String systemName;
    private ConcurrentHashMap<UUID, LeveledPlayer> cacheMap = new ConcurrentHashMap<>();

    public PlayerCache (String systemName) {
        this.systemName = systemName;
    }

    /***
     * Caches the player, unless they are already cached.
     * @return true if the player was cached, false if they were already cached
     */
    public boolean putIfAbsent(UUID uuid, LeveledPlayer lp) {
        if (cacheMap.putIfAbsent(uuid, lp) != null) {
            plugin.getLogger().warning("Attempted to cache player with uuid "+ uuid.toString()+" but they were already cached!");
            return false;
        }
        return true;
    }
    /***
     * Saves player to the database then removes them from the cache.
     * @param p
     */
    public void unloadPlayer(Player p) {
        unloadPlayer(p.getUniqueId());

    }
    /***
     * Saves player to the database then removes them from the cache.
     * @param uuid
     */
    public void unloadPlayer(UUID uuid) {
        LeveledPlayer lp = cacheMap.remove(uuid);
        if (lp != null) {
            lp.saveIfDirty();
        }
    }

    /***
     * Removes the player from the cache WITHOUT saving them to the database first.
     * @param uuid
     */
    public void deletePlayer(UUID uuid) {
        cacheMap.remove(uuid);
    }

    /***
     * Gets the LeveledPlayer from the player cache
     * @param uuid
     * @return the cached LeveledPlayer, or null if the player isn't cached
     */
    public LeveledPlayer getLeveledPlayer(UUID uuid) {
        return cacheMap.get(uuid);
    }

    /***
     * Saves every player in the cache whose data changed since their last save. Players stay cached.
     * @return the number of players saved
     */
    public int saveAllPlayers() {
        int saved = 0;
        for (LeveledPlayer lp : cacheMap.values()) {
            if (lp.saveIfDirty()) {
                saved++;
            }
        }
        return saved;
    }

    /***
     * Saves every player in the cache to the database, then empties the cache.
     */
    public void unloadAllPlayers() {
        cacheMap.keySet().forEach(this::unloadPlayer);
    }
}
