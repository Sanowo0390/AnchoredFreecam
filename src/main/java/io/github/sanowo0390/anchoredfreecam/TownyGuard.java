package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

final class TownyGuard {
    static final String BYPASS_PERMISSION = "anchoredfreecam.towny.bypass";

    record TownRef(UUID id, String name) {
    }

    private final Logger logger;
    private final TownProtectionStore store;
    private final Object api;
    private final Method townAtLocation;
    private final Method townOfPlayer;
    private final Method residentOfPlayer;
    private final Method isMayor;
    private final Method hasTownRank;
    private final Method townId;
    private final Method townName;
    private boolean failureLogged;

    private TownyGuard(Logger logger, TownProtectionStore store, Object api, Method townAtLocation,
                       Method townOfPlayer, Method residentOfPlayer, Method isMayor, Method hasTownRank,
                       Method townId, Method townName) {
        this.logger = logger;
        this.store = store;
        this.api = api;
        this.townAtLocation = townAtLocation;
        this.townOfPlayer = townOfPlayer;
        this.residentOfPlayer = residentOfPlayer;
        this.isMayor = isMayor;
        this.hasTownRank = hasTownRank;
        this.townId = townId;
        this.townName = townName;
    }

    static TownyGuard create(AnchoredFreecamPlugin plugin) {
        Server server = plugin.getServer();
        PluginManager pluginManager = server == null ? null : server.getPluginManager();
        Plugin towny = pluginManager == null ? null : pluginManager.getPlugin("Towny");
        if (towny == null || !towny.isEnabled()) {
            return null;
        }
        try {
            ClassLoader loader = towny.getClass().getClassLoader();
            TownProtectionStore store = new TownProtectionStore(
                    new File(plugin.getDataFolder(), "towns.yml"),
                    () -> plugin.getConfig().getBoolean("towny-protection.default-protected", true),
                    plugin.getLogger());
            TownyGuard guard = forClasses(plugin.getLogger(), store,
                    Class.forName("com.palmergames.bukkit.towny.TownyAPI", true, loader),
                    Class.forName("com.palmergames.bukkit.towny.object.Town", true, loader),
                    Class.forName("com.palmergames.bukkit.towny.object.Resident", true, loader));
            plugin.getLogger().info("Towny protection is active.");
            return guard;
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("Could not hook into the Towny API (" + ex + "). "
                    + "Towny protection is disabled.");
            return null;
        }
    }

    static TownyGuard forClasses(Logger logger, TownProtectionStore store, Class<?> apiClass,
                                 Class<?> townClass, Class<?> residentClass) throws ReflectiveOperationException {
        return new TownyGuard(logger, store,
                apiClass.getMethod("getInstance").invoke(null),
                apiClass.getMethod("getTown", Location.class),
                apiClass.getMethod("getTown", Player.class),
                apiClass.getMethod("getResident", Player.class),
                residentClass.getMethod("isMayor"),
                residentClass.getMethod("hasTownRank", String.class),
                townClass.getMethod("getUUID"),
                townClass.getMethod("getName"));
    }

    boolean isBlocked(Player player, Location target) {
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return false;
        }
        try {
            Object town = townAtLocation.invoke(api, target);
            if (town == null || town.equals(townOfPlayer.invoke(api, player))) {
                return false;
            }
            return store.isProtected((UUID) townId.invoke(town));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            failed(ex);
            return false;
        }
    }

    TownRef townOf(Player player) {
        try {
            Object town = townOfPlayer.invoke(api, player);
            if (town == null) {
                return null;
            }
            return new TownRef((UUID) townId.invoke(town), (String) townName.invoke(town));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            failed(ex);
            return null;
        }
    }

    boolean canManage(Player player) {
        try {
            Object resident = residentOfPlayer.invoke(api, player);
            if (resident == null) {
                return false;
            }
            return (Boolean) isMayor.invoke(resident) || (Boolean) hasTownRank.invoke(resident, "assistant");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            failed(ex);
            return false;
        }
    }

    boolean isTownProtected(UUID town) {
        return store.isProtected(town);
    }

    void setTownProtected(UUID town, boolean value) {
        store.setProtected(town, value);
    }

    private void failed(Exception ex) {
        if (!failureLogged) {
            failureLogged = true;
            logger.warning("Towny lookup failed; the camera is not restricted for this check: " + ex);
        }
    }
}
