package io.github.sanowo0390.anchoredfreecam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

final class BodyNameplate {
    private final AnchoredFreecamPlugin plugin;
    private boolean warned;

    BodyNameplate(AnchoredFreecamPlugin plugin) {
        this.plugin = plugin;
    }

    Component name(Player player) {
        String prefix = null;
        if (plugin.getConfig().getBoolean("show-luckperms-prefix", true)
                && plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                prefix = LuckPermsAccess.prefix(plugin.getServer(), player);
            } catch (RuntimeException | LinkageError ex) {
                if (!warned) {
                    plugin.getLogger().warning("Could not read LuckPerms prefix: " + ex);
                    warned = true;
                }
            }
        }
        return format(prefix).append(Component.text(player.getName()));
    }

    static Component format(String prefix) {
        if (prefix == null || prefix.isEmpty()) return Component.empty();
        if (prefix.matches("(?s).*<[/!?#a-zA-Z][^>]*>.*")) {
            return MiniMessage.miniMessage().deserialize(prefix);
        }
        return LegacyComponentSerializer.builder().character('&').hexColors().build()
                .deserialize(prefix.replace('§', '&'));
    }

    // Keep all LP-typed bytecode in this lazily loaded class. LP is optional.
    private static final class LuckPermsAccess {
        static String prefix(Server server, Player player) {
            RegisteredServiceProvider<LuckPerms> registration = server.getServicesManager().getRegistration(LuckPerms.class);
            return registration == null ? null : registration.getProvider().getPlayerAdapter(Player.class)
                    .getMetaData(player).getPrefix();
        }
    }
}
