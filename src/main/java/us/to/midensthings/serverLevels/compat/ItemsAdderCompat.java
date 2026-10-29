package us.to.midensthings.serverLevels.compat;

import dev.lone.itemsadder.api.CustomBlock;
import org.bukkit.block.Block;

public class ItemsAdderCompat {

    /***
     * @return the block's ItemsAdder id like "itemsadder:namespace:id", or null if it isn't an ItemsAdder block
     */
    public static String getCustomBlockId(Block block) {
        CustomBlock customBlock = CustomBlock.byAlreadyPlaced(block);
        return customBlock == null ? null : "itemsadder:" + customBlock.getNamespacedID();
    }
}
