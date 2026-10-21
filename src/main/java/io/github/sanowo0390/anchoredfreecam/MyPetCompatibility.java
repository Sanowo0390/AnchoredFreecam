package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Optional MyPet 3.14.3 API bridge, verified against that distribution's API. */
final class MyPetCompatibility implements PetController.MyPetAccess {
    private final Method getManager;
    private final Method getPet;
    private final Method getEntity;
    private final Class<?> petEntity;
    private final Method getOwner;
    private final Method ownerUuid;
    private final Method sitting;
    private final Method setSitting;

    private MyPetCompatibility(ClassLoader loader) throws ReflectiveOperationException {
        getManager = loader.loadClass("de.Keyle.MyPet.MyPetApi").getMethod("getMyPetManager");
        getPet = getManager.getReturnType().getMethod("getMyPet", Player.class);
        getEntity = getPet.getReturnType().getMethod("getEntity");
        petEntity = loader.loadClass("de.Keyle.MyPet.api.entity.MyPetBukkitEntity");
        getOwner = petEntity.getMethod("getOwner");
        ownerUuid = getOwner.getReturnType().getMethod("getPlayerUUID");
        sitting = petEntity.getMethod("isSitting");
        setSitting = petEntity.getMethod("setSitting", boolean.class);
    }

    static PetController.MyPetAccess install(AnchoredFreecamPlugin plugin, Predicate<Player> active) {
        Plugin installed = plugin.getServer().getPluginManager().getPlugin("MyPet");
        if (installed == null || !installed.isEnabled()) return null;
        try {
            ClassLoader loader = installed.getClass().getClassLoader();
            MyPetCompatibility bridge = new MyPetCompatibility(loader);
            Class<? extends Event> callEvent = loader.loadClass("de.Keyle.MyPet.api.event.MyPetCallEvent").asSubclass(Event.class);
            Method playerMethod = callEvent.getMethod("getPlayer");
            plugin.getServer().getPluginManager().registerEvent(callEvent, new Listener() {}, EventPriority.HIGHEST,
                    (listener, event) -> {
                        Object owner = invoke(playerMethod, event);
                        if (owner instanceof Player player && active.test(player) && event instanceof Cancellable cancellable) {
                            cancellable.setCancelled(true);
                        }
                    }, plugin, true);
            return bridge;
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("MyPet freecam compatibility unavailable: " + ex);
            return null;
        }
    }

    @Override
    public PetController.Pet activePet(Player owner) {
        Object manager = invoke(getManager, null);
        if (manager == null) return null;
        Object pet = invoke(getPet, manager, owner);
        if (pet == null) return null;
        Object entity = ((Optional<?>) invoke(getEntity, pet)).orElse(null);
        return entity instanceof Entity bukkit ? fromEntity(bukkit) : null;
    }

    @Override
    public PetController.Pet fromEntity(Entity entity) {
        if (!petEntity.isInstance(entity)) return null;
        return new PetController.Pet(entity,
                () -> (UUID) invoke(ownerUuid, invoke(getOwner, entity)),
                () -> (boolean) invoke(sitting, entity),
                value -> invoke(setSitting, entity, value));
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try { return method.invoke(target, arguments); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException("MyPet API: " + method.getName(), ex); }
    }
}
