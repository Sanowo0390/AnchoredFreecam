package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraInventoryMaskTest {
    @Test
    void sendsSelectedSlotAndAllEquipmentWithoutWritingInventory() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHeldItemSlot()).thenReturn(4);
        List<Integer> slots = new ArrayList<>();
        var mask = new CameraInventoryMask((recipient, slot) -> {
            assertSame(player, recipient);
            slots.add(slot);
        }, mock(Logger.class));
        mask.mask(player);
        assertEquals(List.of(4, 36, 37, 38, 39, 40), slots);
        verify(inventory).getHeldItemSlot();
        verifyNoMoreInteractions(inventory);
    }

    @Test
    void hotbarChangeMasksNewSelectionInsteadOfClearingEveryHotbarSlot() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHeldItemSlot()).thenReturn(0, 8);
        List<Integer> slots = new ArrayList<>();
        var mask = new CameraInventoryMask((recipient, slot) -> slots.add(slot), mock(Logger.class));
        mask.mask(player);
        mask.mask(player);
        assertEquals(0, slots.get(0));
        assertEquals(8, slots.get(6));
        assertEquals(12, slots.size());
        assertFalse(slots.contains(1));
    }

    @Test
    void packetFailureRestoresInventoryAndStopsRetries() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        Logger logger = mock(Logger.class);
        var mask = new CameraInventoryMask((recipient, slot) -> {
            throw new ReflectiveOperationException("unsupported runtime");
        }, logger);
        mask.mask(player);
        mask.mask(player);
        verify(player, times(1)).updateInventory();
        verify(logger, times(1)).warning(anyString());
    }

    @Test
    void unavailableAdapterDoesNotTouchInventory() {
        Player player = mock(Player.class);
        new CameraInventoryMask(null, mock(Logger.class)).mask(player);
        verifyNoInteractions(player);
    }
}
