package us.to.midensthings.serverLevels.compat;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import org.bukkit.block.Block;

public class CraftEngineCompat {

    /***
     * @return the block's CraftEngine id like "craftengine:namespace:id", or null if it isn't a CraftEngine block
     */
    public static String getCustomBlockId(Block block) {
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        if (state == null || state.isEmpty()) {
            return null;
        }
        return "craftengine:" + state.owner().value().id().asString();
    }
}
