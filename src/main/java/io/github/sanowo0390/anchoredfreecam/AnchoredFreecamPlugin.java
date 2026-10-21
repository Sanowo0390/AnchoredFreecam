package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import java.util.Collection;
import java.util.UUID;

public final class AnchoredFreecamPlugin extends JavaPlugin {
    private FreecamManager manager;
    private Messages messages;
    private CameraInventoryMask inventoryMask;
    private PetController pets;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        inventoryMask = CameraInventoryMask.forPaper(getLogger());
        manager = new FreecamManager(this, messages);
        pets = new PetController(this, manager::isActive, MyPetCompatibility.install(this, manager::isActive));

        FreecamCommand freecamCommand = new FreecamCommand(this, manager, messages);
        PluginCommand command = getCommand("freecam");
        if (command == null) {
            throw new IllegalStateException("freecam command is missing from plugin.yml");
        }
        command.setExecutor(freecamCommand);
        command.setTabCompleter(freecamCommand);

        getServer().getPluginManager().registerEvents(new FreecamListener(this, manager), this);
        getLogger().info("AnchoredFreecam enabled. Max distance: " + manager.getMaxDistance()
                + ", language: " + messages.language());
    }

    void maskCameraInventory(Player player) {
        if (inventoryMask != null) inventoryMask.mask(player);
    }

    void pauseOwnerPets(Player owner) {
        if (pets != null) pets.pauseOwner(owner);
    }

    boolean pausePetIfOwnerFreecam(Entity entity) {
        return pets != null && pets.pauseEntity(entity);
    }

    void restoreOwnerPets(UUID owner) {
        if (pets != null) pets.restoreOwner(owner);
    }

    void releasePetsBeforeUnload(Collection<Entity> entities) {
        if (pets != null) pets.beforeUnload(entities);
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
        if (pets != null) pets.restoreAll();
    }
}
