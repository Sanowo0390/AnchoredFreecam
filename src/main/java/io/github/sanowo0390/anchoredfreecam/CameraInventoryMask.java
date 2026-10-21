package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cosmetic inventory packets; never changes server inventory contents. */
final class CameraInventoryMask {
    interface SlotSender {
        void send(Player player, int containerSlot, ItemStack contents) throws ReflectiveOperationException;
    }

    private SlotSender sender;
    private final Map<UUID, Integer> selections = new HashMap<>();
    private final Logger logger;

    CameraInventoryMask(SlotSender sender, Logger logger) {
        this.sender = sender;
        this.logger = logger;
    }

    static CameraInventoryMask forPaper(Logger logger) {
        try {
            Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Method getHandle = craftPlayer.getMethod("getHandle");
            Field connection = getHandle.getReturnType().getField("connection");
            Field inventoryMenu = getHandle.getReturnType().getField("inventoryMenu");
            Method stateId = inventoryMenu.getType().getMethod("getStateId");
            Class<?> itemStack = Class.forName("net.minecraft.world.item.ItemStack");
            Method asNative = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
                    .getMethod("asNMSCopy", ItemStack.class);
            Constructor<?> packet = Class.forName("net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket")
                    .getConstructor(int.class, int.class, int.class, itemStack);
            Method send = connection.getType().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"));
            return new CameraInventoryMask((player, slot, contents) -> {
                Object handle = getHandle.invoke(player);
                Object listener = connection.get(handle);
                if (listener != null) send.invoke(listener, packet.newInstance(0,
                        stateId.invoke(inventoryMenu.get(handle)), slot, asNative.invoke(null, contents)));
            }, logger);
        } catch (ReflectiveOperationException | LinkageError ex) {
            logger.warning("Client inventory masking unavailable; equipment masking remains active: " + ex);
            return new CameraInventoryMask(null, logger);
        }
    }

    void mask(Player player) {
        if (sender == null) return;
        int selected = player.getInventory().getHeldItemSlot();
        if (selected < 0 || selected > 8) return;
        try {
            Integer previous = selections.put(player.getUniqueId(), selected);
            if (previous != null && previous != selected) {
                sender.send(player, 36 + previous, player.getInventory().getItem(previous));
            }
            // Window 0 uses container indices, understood consistently by Java and Geyser.
            // SetPlayerInventory's equipment indices 36..40 become hotbar 0..4 in Geyser.
            sender.send(player, 36 + selected, null);
            for (int slot = 5; slot <= 8; slot++) sender.send(player, slot, null);
            sender.send(player, 45, null);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            sender = null;
            selections.clear();
            logger.warning("Client inventory masking disabled after a packet failure: " + ex);
            player.updateInventory();
        }
    }

    void release(Player player) {
        selections.remove(player.getUniqueId());
    }
}
