package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Keep owned pets waiting physically while their owner's real entity is a camera. */
final class PetController {
    record Pet(Entity entity, Supplier<UUID> owner, BooleanSupplier sitting, Consumer<Boolean> setSitting) {}
    interface MyPetAccess {
        Pet activePet(Player player);
        Pet fromEntity(Entity entity);
    }
    private record Paused(Pet pet, UUID owner, boolean wasSitting) {}

    private final AnchoredFreecamPlugin plugin;
    private final Predicate<Player> active;
    private final MyPetAccess myPet;
    private final Map<UUID, Paused> paused = new HashMap<>();
    private boolean warned;

    PetController(AnchoredFreecamPlugin plugin, Predicate<Player> active, MyPetAccess myPet) {
        this.plugin = plugin;
        this.active = active;
        this.myPet = myPet;
    }

    void pauseOwner(Player owner) {
        if (!active.test(owner)) return;
        for (Tameable tameable : owner.getWorld().getEntitiesByClass(Tameable.class)) {
            if (owner.getUniqueId().equals(tameable.getOwnerUniqueId())) pauseEntity(tameable);
        }
        if (myPet != null) {
            try {
                Pet pet = myPet.activePet(owner);
                if (pet != null && owner.getUniqueId().equals(pet.owner().get())) pause(pet, owner.getUniqueId());
            } catch (RuntimeException ex) { warn(ex); }
        }
    }

    boolean pauseEntity(Entity entity) {
        try {
            Pet pet = myPet == null ? null : myPet.fromEntity(entity);
            if (pet == null && entity instanceof Tameable tameable && entity instanceof Sittable sitting) {
                pet = new Pet(entity, tameable::getOwnerUniqueId, sitting::isSitting, sitting::setSitting);
            }
            if (pet == null) return false;
            UUID ownerId = pet.owner().get();
            Player owner = ownerId == null ? null : plugin.getServer().getPlayer(ownerId);
            if (owner == null || !active.test(owner)) return false;
            pause(pet, ownerId);
            return true;
        } catch (RuntimeException ex) {
            warn(ex);
            return false;
        }
    }

    private void pause(Pet pet, UUID owner) {
        if (!pet.entity().isValid() || pet.entity().isDead()) return;
        UUID id = pet.entity().getUniqueId();
        Paused previous = paused.get(id);
        paused.put(id, new Paused(pet, owner, previous == null ? pet.sitting().getAsBoolean() : previous.wasSitting()));
        if (!pet.sitting().getAsBoolean()) pet.setSitting().accept(true);
        if (pet.entity() instanceof Mob mob && mob.getPathfinder() != null) mob.getPathfinder().stopPathfinding();
    }

    void restoreOwner(UUID owner) {
        for (Map.Entry<UUID, Paused> entry : Map.copyOf(paused).entrySet()) {
            if (owner.equals(entry.getValue().owner())) release(entry.getKey(), false);
        }
    }

    void beforeUnload(Collection<Entity> entities) {
        // Release temporary sitting state when the owning chunk unloads.
        for (Entity entity : entities) release(entity.getUniqueId(), true);
    }

    void restoreAll() {
        for (UUID id : java.util.Set.copyOf(paused.keySet())) release(id, false);
    }

    private void release(UUID id, boolean unloading) {
        Paused saved = paused.remove(id);
        if (saved == null) return;
        Pet pet = saved.pet();
        if (!unloading && (!pet.entity().isValid() || pet.entity().isDead())) return;
        try {
            if (saved.owner().equals(pet.owner().get()) && pet.sitting().getAsBoolean()) {
                pet.setSitting().accept(saved.wasSitting());
            }
        } catch (RuntimeException ex) { warn(ex); }
    }

    private void warn(RuntimeException ex) {
        if (!warned) {
            plugin.getLogger().warning("Could not pause/restore a freecam owner's pet: " + ex);
            warned = true;
        }
    }
}
