package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/** Cosmetic inventory packets; never changes server inventory contents. */
final class CameraInventoryMask {
    interface EmptySlotSender {
        void send(Player player, int inventorySlot) throws ReflectiveOperationException;
    }

    private EmptySlotSender sender;
    private final Logger logger;

    CameraInventoryMask(EmptySlotSender sender, Logger logger) {
        this.sender = sender;
        this.logger = logger;
    }

    static CameraInventoryMask forPaper(Logger logger) {
        try {
            Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Method getHandle = craftPlayer.getMethod("getHandle");
            Field connection = getHandle.getReturnType().getField("connection");
            Class<?> itemStack = Class.forName("net.minecraft.world.item.ItemStack");
            Object empty = itemStack.getField("EMPTY").get(null);
            Constructor<?> packet = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket")
                    .getConstructor(int.class, itemStack);
            Method send = connection.getType().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"));
            return new CameraInventoryMask((player, slot) -> {
                Object listener = connection.get(getHandle.invoke(player));
                if (listener != null) send.invoke(listener, packet.newInstance(slot, empty));
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
            sender.send(player, selected);
            // Paper 26.2 player-inventory indices: feet, legs, chest, head, offhand.
            for (int slot = 36; slot <= 40; slot++) sender.send(player, slot);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            sender = null;
            logger.warning("Client inventory masking disabled after a packet failure: " + ex);
            player.updateInventory();
        }
    }
}
