package us.to.midensthings.serverLevels.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

public final class EntityUtil {

    private EntityUtil() {}

    /***
     * Whether killing or damaging this entity can give exp. Real mobs (anything with mob AI) and players can,
     * and so can mannequins, which admins place as training dummies.
     * Armor stands and other non-mob entities can't, so players can't farm exp from them.
     */
    public static boolean canGiveExp(Entity entity) {
        return entity instanceof Mob || entity instanceof Player || entity instanceof Mannequin;
    }
}
