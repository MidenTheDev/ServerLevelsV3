package us.to.midensthings.serverLevels.compat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;

public class LuckPermsCompat {
    private net.luckperms.api.LuckPerms luckPermsAPI;

    public LuckPermsCompat() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            this.luckPermsAPI = provider.getProvider();
        }
    }


    public void addPermissions(Player p, List<String> perms) {
        if (perms.isEmpty()) {
            return;
        }

        User user = luckPermsAPI.getPlayerAdapter(Player.class).getUser(p);
        for (String perm : perms) {
            user.data().add(Node.builder(perm).build());
        }
        luckPermsAPI.getUserManager().saveUser(user);


    }

}
